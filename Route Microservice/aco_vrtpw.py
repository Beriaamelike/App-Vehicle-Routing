import logging
from typing import List
import numpy as np
import pandas as pd
import requests
from fastapi import FastAPI, HTTPException, Query, UploadFile, File, Depends
from sqlalchemy.orm import Session
from create_tables import Aco, AssignDriverRequest, Customer, CustomerCreate, Route, RouteAssignment, UserDetailsResponse, UserDetailsRoles
from database import get_db
from database import engine, Base
from fastapi import FastAPI, Depends, APIRouter
from pydantic import BaseModel
from sqlalchemy.orm import Session
from database import get_db
from create_tables import Depot, UserDetails
import time  
from create_tables import DepotCreate
from sqlalchemy import func
import os
import jwt

from vrp_tests import test_capacity_constraint, test_geographic_consistency, test_total_demand_info, test_vehicle_limit


SECRET_KEY = "A0B1C2D3E4F5061728394A5B6C7D8E9F1011121314151617181920212223242526272829303132333435363738393A3B3C3D3E3F40414243444546474849" 
Base.metadata.create_all(bind=engine)

app = FastAPI()

OSRM_API_URL = "http://router.project-osrm.org/table/v1/driving"

class VehicleRoutingProblem:
    def __init__(self, nodes, depot, vehicle_capacity, num_vehicles, max_working_time):
        self.depot = depot
        self.nodes = [depot] + nodes
        self.vehicle_capacity = vehicle_capacity
        self.num_vehicles = num_vehicles
        self.distance_matrix, self.duration_matrix = self.calculate_distance_and_duration_matrices()
        self.demands = [node.get("demand", 0) for node in self.nodes]
        self.ready_times = [node.get("ready_time", 0) for node in self.nodes]
        self.due_times = [node.get("due_time", 99999) for node in self.nodes]
        self.service_times = [node.get("service_time", 0) for node in self.nodes]
        self.max_working_time = max_working_time

    def calculate_distance_and_duration_matrices(self):
        try:
           locations = ";".join([f"{float(node['yc']):.8f},{float(node['xc']):.8f}" for node in self.nodes])
           url = f"{OSRM_API_URL}/{locations}?annotations=distance,duration"
           logging.debug(f"OSRM URL: {url}")

           response = requests.get(url, timeout=10)
           response.raise_for_status()
           data = response.json()

           distance_matrix = np.array(data["distances"]) / 1000  # ❗ km
           duration_matrix = np.array(data["durations"]) / 60    # ❗ dakika

        # NaN'leri sıfırla
           distance_matrix = np.nan_to_num(distance_matrix, nan=0.0)
           duration_matrix = np.nan_to_num(duration_matrix, nan=0.0)

           return distance_matrix, duration_matrix
        except requests.exceptions.RequestException as e:
           logging.error(f"OSRM API error: {str(e)}")
           raise HTTPException(status_code=500, detail="OSRM API request failed")



MAX_TIME = 1236  # örnek değer; bunu CSV'den alıp problem objesine de taşıyabilirsin

def construct_solution(problem, pheromone_matrix, alpha, beta):
    n = len(problem.nodes)
    remaining_nodes = set(range(1, n))
    routes = []
    route_count = 0
    unassigned_info = {}  # node_index -> reason

    while remaining_nodes and route_count < problem.num_vehicles:
        route = [0]
        capacity = 0
        current_node = 0
        current_time = 0

        while True:
            candidates = []
            for next_node in remaining_nodes:
                demand = problem.demands[next_node]

                if demand + capacity > problem.vehicle_capacity:
                    unassigned_info[next_node] = "kapasite aşıldı"
                    continue

                distance = problem.distance_matrix[current_node][next_node]
                duration = problem.duration_matrix[current_node][next_node]
                arrival = current_time + duration
                start_service = max(arrival, problem.ready_times[next_node])
                finish_service = start_service + problem.service_times[next_node]

                if finish_service > problem.due_times[next_node]:
                    unassigned_info[next_node] = "zaman penceresi aşıldı"
                    continue
                if finish_service > problem.max_working_time:
                    unassigned_info[next_node] = "günlük çalışma süresi aşıldı"
                    continue
                if distance > 20.0:
                    unassigned_info[next_node] = "bu rota için çok uzak"
                    continue

                tau = pheromone_matrix[current_node][next_node] ** alpha
                eta = (1 / max(distance, 1e-6)) ** beta

# Ortalama konumu hesapla
                mean_x = np.mean([float(problem.nodes[i]["xc"]) for i in route])
                mean_y = np.mean([float(problem.nodes[i]["yc"]) for i in route])

                dx = float(problem.nodes[next_node]["xc"]) - mean_x
                dy = float(problem.nodes[next_node]["yc"]) - mean_y
                cluster_distance = np.sqrt(dx**2 + dy**2)

                cluster_penalty = 1 / (1 + cluster_distance ** 2)


# Tümünü çarp
                prob = tau * eta * cluster_penalty

                candidates.append((next_node, prob, finish_service, demand))

            if not candidates:
                break

            probs = np.array([c[1] for c in candidates])
            probs /= probs.sum()
            selected_index = np.random.choice(len(candidates), p=probs)

            selected_node, _, new_time, demand = candidates[selected_index]

            route.append(selected_node)
            current_node = selected_node
            current_time = new_time
            capacity += demand
            remaining_nodes.remove(selected_node)
            if selected_node in unassigned_info:
                del unassigned_info[selected_node]  # artık eklendi, nedeni sil

        route.append(0)
        routes.append(route)
        route_count += 1

    # Araç yetmediği için taşınamayanlar
    for node in remaining_nodes:
        if node not in unassigned_info:
            unassigned_info[node] = "araç sayısı yetersiz"

    return routes, unassigned_info


def get_unassigned_customers(problem, unassigned_info):
    customers = []
    for node_idx, reason in unassigned_info.items():
        node = problem.nodes[node_idx]
        customers.append({
            "customer": node.get("customer", f"Node {node_idx}"),
            "coordinates": {"lat": node["xc"], "lon": node["yc"]},
            "demand": node.get("demand", 0),
            "ready_time": node.get("ready_time", 0),
            "due_time": node.get("due_time", 99999),
            "excluded_reason": reason  # ❗ neden dahil edilmedi
        })
    return customers


from math import radians, sin, cos, sqrt, atan2

def haversine_distance(coord1, coord2):
    R = 6371  # km cinsinden Dünya yarıçapı
    lat1, lon1 = radians(coord1["lat"]), radians(coord1["lon"])
    lat2, lon2 = radians(coord2["lat"]), radians(coord2["lon"])
    dlat = lat2 - lat1
    dlon = lon2 - lon1

    a = sin(dlat/2)**2 + cos(lat1) * cos(lat2) * sin(dlon/2)**2
    c = 2 * atan2(sqrt(a), sqrt(1-a))
    return R * c

def calculate_route_distances(problem, routes):
    return [sum(problem.distance_matrix[route[i]][route[i+1]] for i in range(len(route)-1)) for route in routes]

def get_route_customers(problem, routes):
    route_customers = []
    for route in routes:
        customer_info = []
        for node_idx in route:
            customer_info.append({
                "customer": problem.nodes[node_idx].get("customer", f"Node {node_idx}"),
                "coordinates": {"lat": problem.nodes[node_idx]["xc"], "lon": problem.nodes[node_idx]["yc"]},
                "demand": problem.nodes[node_idx].get("demand", 0)
            })
        route_customers.append(customer_info)
    return route_customers

def cluster_spread_penalty(distance_matrix, route, penalty_weight=1.0):
    if len(route) <= 2:
        return 0
    customer_nodes = route[1:-1]  # depoyu çıkar
    max_distance = 0
    for i in range(len(customer_nodes)):
        for j in range(i + 1, len(customer_nodes)):
            dist = distance_matrix[customer_nodes[i]][customer_nodes[j]]
            max_distance = max(max_distance, dist)
    return penalty_weight * max_distance


import logging
import time

def solve_aco(problem, alpha, beta, rho, iterations):
    np.random.seed(42)
    pheromone_matrix = 1 / (problem.distance_matrix + np.finfo(float).eps)
    best_routes = []
    best_distance = float("inf")
    best_unassigned_info = {}

    for _ in range(iterations):
        routes, unassigned_info = construct_solution(problem, pheromone_matrix, alpha, beta)
        route_distances = calculate_route_distances(problem, routes)
        spread_penalties = [cluster_spread_penalty(problem.distance_matrix, r) for r in routes]
        distance = np.std(route_distances) + 0.3 * np.mean(route_distances) + np.mean(spread_penalties)

        if distance < best_distance:
            best_distance = distance
            best_routes = routes
            best_unassigned_info = unassigned_info

        pheromone_matrix *= (1 - rho)
        for route, route_distance in zip(routes, route_distances):
            pheromone_amount = (len(route) - 2) / (route_distance + 1e-6)
            for i in range(len(route)-1):
                pheromone_matrix[route[i]][route[i+1]] += pheromone_amount

    # 🟢 Konsola rotaya dahil edilen müşteriler
    print("\n✅ Rotaya dahil edilen müşteriler:")
    for i, route in enumerate(best_routes, start=1):
        customers = [problem.nodes[n]["customer"] for n in route if n != 0]
        total_demand = sum(problem.demands[n] for n in route if n != 0)
        print(f"🛻 Route {i} - Toplam Talep: {total_demand} - Müşteriler: {customers}")

    # 🔴 Rotaya dahil edilemeyenler
    if best_unassigned_info:
        print("\n❌ Rotaya dahil edilemeyen müşteriler:")
        for idx, reason in best_unassigned_info.items():
            node = problem.nodes[idx]
            print(f"🚫 {node['customer']} - Talep: {node.get('demand', 0)} - Nedeni: {reason}")
    else:
        print("\n✅ Tüm müşteriler başarıyla rotalara eklendi.")

    return best_routes, best_unassigned_info



@app.post("/optimize_routes")
async def optimize_routes(
    nodes_csv: UploadFile = File(...),
    vehicle_info_csv: UploadFile = File(...),
    alpha: float = 2.0,
    beta: float = 10.0,
    rho: float = 0.2,
    iterations: int = 10
):
    start_time = time.time()  # ⏱ Optimizasyon başlangıç zamanı

    nodes_df = pd.read_csv(nodes_csv.file)
    nodes_df.columns = nodes_df.columns.str.lower()

    vehicle_info_df = pd.read_csv(vehicle_info_csv.file)
    vehicle_capacity = int(vehicle_info_df['fleet_capacity'][0])
    num_vehicles = int(vehicle_info_df['fleet_size'][0])
    max_working_time = int(vehicle_info_df['fleet_max_working_time'][0])  # ➕ Bunu ekle


    depot = {
        "customer": "Depot",
        "xc": vehicle_info_df['fleet_start_x_coord'][0],
        "yc": vehicle_info_df['fleet_start_y_coord'][0],
        "demand": 0
    }

    customer_nodes = nodes_df.to_dict(orient="records")

    problem = VehicleRoutingProblem(
        nodes=customer_nodes,
        depot=depot,
        vehicle_capacity=vehicle_capacity,
        num_vehicles=num_vehicles,
        max_working_time=max_working_time
    )

    best_routes, unassigned_nodes = solve_aco(problem, alpha, beta, rho, iterations)
    unassigned_customers = get_unassigned_customers(problem, unassigned_nodes)

    # Toplam mesafeyi hesapla
    total_distance = sum(calculate_route_distances(problem, best_routes))

    end_time = time.time()  # ⏱ Optimizasyon bitiş zamanı
    duration = round(end_time - start_time, 2)

    return {
        "duration_seconds": duration,
        "total_distance_km": round(total_distance, 2),
        "route_customers": get_route_customers(problem, best_routes),
        "unassigned_customers": unassigned_customers 
    }



def get_route_customers_with_depot(problem, routes):
    route_customers = []
    for route in routes:
        customer_info = []
        
        # İlk olarak depoyu ekle
        depot_info = {
            "customer": "Depot",
            "coordinates": {"lat": problem.depot["xc"], "lon": problem.depot["yc"]},
            "demand": problem.depot["demand"]
        }
        customer_info.append(depot_info)

        # Şimdi rotadaki tüm müşterileri ekle
        for node_idx in route[1:]:  # Depot'u atla, index 0
            customer_info.append({
                "customer": problem.nodes[node_idx].get("customer", f"Node {node_idx}"),
                "coordinates": {"lat": problem.nodes[node_idx]["xc"], "lon": problem.nodes[node_idx]["yc"]},
                "demand": problem.nodes[node_idx].get("demand", 0)
            })
        
        route_customers.append(customer_info)

    return route_customers


@app.get("/get_routes")
async def get_routes(db: Session = Depends(get_db)):
    # Veritabanından rotaları çek
    routes = db.query(Aco).all()

    # Rotaları kullanıcı dostu formatta hazırlama
    route_data = {}
    for route in routes:
        route_number = route.route_number
        if route_number not in route_data:
            route_data[route_number] = []

        route_data[route_number].append({
            "route_number": route.route_number,
            "route_order": route.route_order,
            "customer_id": route.customer_id,
            "customer_name": route.customer_name,
            "coordinates": {"lat": route.customer_lat, "lon": route.customer_lon},
            "demand": route.demand,
            "created_at": route.created_at
        })

    # Harita üzerinde kullanılabilir formata dönüştürme
    route_customers = []
    for route_number, customers in route_data.items():
        route_customers.append(customers)

    return {"route_customers": route_customers}


@app.post("/upload_csv_without_route")
async def upload_csv_without_route(
    nodes_csv: UploadFile = File(...),
    vehicle_info_csv: UploadFile = File(...),
    db: Session = Depends(get_db)
):
    # CSV dosyasını oku ve pandas DataFrame'e çevir
    nodes_df = pd.read_csv(nodes_csv.file)
    nodes_df.columns = nodes_df.columns.str.lower()  # Kolon isimlerini küçük harfe çevir

    vehicle_info_df = pd.read_csv(vehicle_info_csv.file)

    # Depot bilgilerini al
    depot = {
        "customer": "Depot",
        "xc": vehicle_info_df['fleet_start_x_coord'][0],
        "yc": vehicle_info_df['fleet_start_y_coord'][0],
        "demand": 0
    }

    # Müşteri düğümleri verisini CSV'den al
    customer_nodes = nodes_df.to_dict(orient="records")

    # Depoyu veritabanına kaydet
    route_entry = Route(
        customer_id=0,  # Depot'un müşteri ID'si yok
        customer_name="Depot",
        customer_lat=depot["xc"],
        customer_lon=depot["yc"],
        demand=depot["demand"]
    )
    db.add(route_entry)

    # Müşteri bilgilerini veritabanına kaydet
    for node_idx, customer_info in enumerate(customer_nodes, start=1):  # Başlangıçta depo ekledik
        route_entry = Route(
            customer_id=node_idx,
            customer_name=customer_info.get("customer", f"Node {node_idx}"),
            customer_lat=customer_info["xc"],
            customer_lon=customer_info["yc"],
            demand=customer_info.get("demand", 0)
        )
        db.add(route_entry)

    db.commit()  # Değişiklikleri veritabanına kaydet

    return {"message": "CSV verileri başarıyla veritabanına kaydedildi"}


@app.get("/get_all_customers")
async def get_all_routes(db: Session = Depends(get_db)):
    # Veritabanındaki tüm rotaları çek
    routes = db.query(Route).all()

    # Rotaları kullanıcı dostu formatta hazırlama
    route_data = []
    for route in routes:
        route_data.append({
            "id": route.id,
            "customer_id": route.customer_id,
            "customer_name": route.customer_name,
            "customer_lat": route.customer_lat,
            "customer_lon": route.customer_lon,
            "demand": route.demand
        })

    # Tüm rotaları döndür
    return {"routes": route_data} 


@app.post("/add_depot")
async def add_depot(depot: DepotCreate, db: Session = Depends(get_db)):
    db_depot = Depot(
       x=depot.x,
       y=depot.y,
       capacity=depot.capacity,
       fleet_size=depot.fleet_size,
       max_working_time=depot.max_working_time
   )

    db.add(db_depot)
    db.commit()
    db.refresh(db_depot)
    return {"message": "Depo başarıyla eklendi.", "depot_id": db_depot.id}


@app.post("/add_customer")
async def add_customer(customer: CustomerCreate, db: Session = Depends(get_db)):
    new_customer = Customer(
        id=customer.id,
        xc=customer.xc,
        yc=customer.yc,
        demand=customer.demand,
        ready_time=customer.ready_time,
        due_time=customer.due_time,
        service_time=customer.service_time
    )
    db.add(new_customer)
    db.commit()
    db.refresh(new_customer)
    return {"message": "Müşteri başarıyla eklendi", "id": new_customer.id}


@app.get("/get_customers")
async def get_customers(db: Session = Depends(get_db)):
    customers = db.query(Customer).all()
    return {"customers": [c.__dict__ for c in customers]}


@app.get("/get_depot")
async def get_depot(db: Session = Depends(get_db)):
    depots = db.query(Depot).all()
    return {"depots": [
        {
            "id": d.id,
            "x": d.x,
            "y": d.y,
            "capacity": d.capacity,
            "fleet_size": d.fleet_size
        } for d in depots
    ]}


router = APIRouter()


import time
import logging
from sqlalchemy.orm import Session
from fastapi import HTTPException

# Loglama ayarları
logging.basicConfig(level=logging.DEBUG)

@app.post("/optimize_routes_from_db")
async def optimize_routes_from_db(
    alpha: float = 2.0,
    beta: float = 10.0,
    rho: float = 0.2,
    iterations: int = 10,
    db: Session = Depends(get_db)
):
    start_time = time.time()
    try:
        # Fetch depot and customer data from the database
        logging.debug("Fetching depot information from the database...")
        depot_start_time = time.time()
        depot = db.query(Depot.id, Depot.x, Depot.y, Depot.capacity, Depot.fleet_size, Depot.max_working_time).first()
        depot_duration = time.time() - depot_start_time
        logging.debug(f"Depot data fetched. Duration: {depot_duration:.4f} seconds")

        if not depot:
            logging.debug("Depot not found in the database. Adding depot to the database...")
            depot = Depot(
                id=1,
                x=39.9213722,
                y=32.853286,
                capacity=100,
                fleet_size=5,
                max_working_time=8
            )
            db.add(depot)
            db.commit()
            logging.debug(f"Depot added with ID {depot.id}.")

        logging.debug("Fetching customer information from the database...")
        customers_start_time = time.time()
        customers = db.query(Customer).all()
        customers_duration = time.time() - customers_start_time
        logging.debug(f"Customer data fetched. Duration: {customers_duration:.4f} seconds")

        # Preparing the problem data
        depot_data = {
            "customer": "Depot",
            "xc": depot.x,
            "yc": depot.y,
            "demand": 0
        }

        customer_nodes = [{
            "customer": f"Customer {customer.id}",
            "xc": customer.xc,
            "yc": customer.yc,
            "demand": customer.demand,
            "ready_time": customer.ready_time,
            "due_time": customer.due_time,
            "service_time": customer.service_time
        } for customer in customers]

        logging.debug(f"Depot and {len(customers)} customer data loaded.")

        # ACO optimization process
        problem = VehicleRoutingProblem(
            nodes=customer_nodes,
            depot=depot_data,
            vehicle_capacity=depot.capacity,
            num_vehicles=depot.fleet_size,
            max_working_time=depot.max_working_time
        )

        aco_start_time = time.time()
        best_routes, unassigned_info = solve_aco(problem, alpha, beta, rho, iterations)
        unassigned_customers = get_unassigned_customers(problem, unassigned_info)
        aco_duration = time.time() - aco_start_time
        logging.debug(f"ACO optimization completed. Duration: {aco_duration:.4f} seconds")

        total_distance = sum(calculate_route_distances(problem, best_routes))
        total_duration_minutes = sum(calculate_total_time_with_service_and_wait(problem, best_routes))


        # Getting the current timestamp for the created_at value
        current_time = time.strftime("%Y-%m-%d %H:%M:%S", time.gmtime())  # Current timestamp

        # Use SQLAlchemy func to get the maximum route_number
        try:
            max_route_number = db.query(func.max(Aco.route_number)).scalar() or 0  # If no rows, set to 0
            logging.debug(f"Max route_number found: {max_route_number}")
        except Exception as e:
            logging.error(f"Error fetching max route_number: {str(e)}")
            max_route_number = 0  # Fallback to 0 if query fails

        logging.debug(f"Saving ACO routes to the database...")
        for route_number, route in enumerate(best_routes, start=max_route_number + 1):  # Start from the next available number
            try:
                # Add depot at the start of the route
                aco_entry_depot_start = Aco(
                    route_number=route_number,
                    route_order=0,  # Depot is the first stop in the route
                    customer_id=depot.id,
                    customer_name="Depot",
                    customer_lat=depot.x,
                    customer_lon=depot.y,
                    demand=0,
                    created_at=current_time
                )
                db.add(aco_entry_depot_start)

                # Add customers in the route
                for order, node_index in enumerate(route[1:-1], start=1):  # [1:-1] çünkü baş ve son depo
                   customer_node = problem.nodes[node_index]
                   customer_id_str = customer_node["customer"].split()[-1]
                   customer_id = int(customer_id_str) if customer_id_str.isdigit() else None

                   if customer_id:
                     aco_entry = Aco(
                        route_number=route_number,
                        route_order=order,
                        customer_id=customer_id,
                        customer_name=f"Customer {customer_id}",
                        customer_lat=customer_node["xc"],
                        customer_lon=customer_node["yc"],
                        demand=customer_node["demand"],
                        created_at=current_time
                     )
                     db.add(aco_entry)

                # Add depot at the end of the route
                aco_entry_depot_end = Aco(
                    route_number=route_number,
                    route_order=len(route) + 1,
                    customer_id=depot.id,
                    customer_name="Depot",
                    customer_lat=depot.x,
                    customer_lon=depot.y,
                    demand=0,
                    created_at=current_time
                )
                db.add(aco_entry_depot_end)
            except Exception as e:
                logging.error(f"Error saving ACO route: {str(e)}")

        db.commit()
        logging.debug("ACO routes successfully saved to the database.")

        end_time = time.time()
        total_duration = end_time - start_time
        logging.debug(f"Optimization completed. Total duration: {total_duration:.4f} seconds")
        # her rotanın toplam talebini göster
        route_demands = [sum(problem.demands[n] for n in r if n != 0) for r in best_routes]
    

        return {
            "duration_seconds": round(total_duration, 2),
            "total_distance_km": round(total_distance, 2),
            "total_duration_minutes": round(total_duration_minutes, 2),
            "route_demands": route_demands,
            "route_customers": get_route_customers(problem, best_routes),
            "unassigned_customers": unassigned_customers
        }

    except Exception as e:
        logging.error(f"Error occurred during optimization: {str(e)}")
        raise HTTPException(status_code=500, detail=f"Error occurred: {str(e)}")

def calculate_total_time_with_service_and_wait(problem, routes):
    total_times = []

    for route in routes:
        time = 0
        for i in range(len(route) - 1):
            current = route[i]
            next_node = route[i + 1]
            travel_time = problem.duration_matrix[current][next_node]
            arrival_time = time + travel_time

            ready_time = problem.ready_times[next_node]
            wait_time = max(0, ready_time - arrival_time)
            service_time = problem.service_times[next_node]

            time = arrival_time + wait_time + service_time

        total_times.append(time)

    return total_times


@app.get("/get_recent_routes")
async def get_recent_routes(db: Session = Depends(get_db)):
    # Veritabanındaki son eklenen rotayı al
    last_route = db.query(Aco).order_by(Aco.created_at.desc()).first()
    
    if not last_route:
        raise HTTPException(status_code=404, detail="No routes found")
    
    # O rota zamanına sahip tüm rotaları çek
    routes = db.query(Aco).filter(Aco.created_at == last_route.created_at).all()

    # Rotaları kullanıcı dostu formatta hazırlama
    route_data = {}
    for route in routes:
        route_number = route.route_number
        if route_number not in route_data:
            route_data[route_number] = []

        route_data[route_number].append({
            "route_number": route.route_number,
            "route_order": route.route_order,
            "customer_id": route.customer_id,
            "customer_name": route.customer_name,
            "coordinates": {"lat": route.customer_lat, "lon": route.customer_lon},
            "demand": route.demand,
            "created_at": route.created_at  # created_at alanı eklendi
        })

    # Harita üzerinde kullanılabilir formata dönüştürme
    route_customers = []
    for route_number, customers in route_data.items():
        route_customers.append(customers)

    return {"route_customers": route_customers}


# Token doğrulama için fonksiyon
def verify_jwt(token: str):
    try:
        payload = jwt.decode(token, SECRET_KEY, algorithms=["HS256"])
        return payload
    except jwt.PyJWTError:
        raise HTTPException(status_code=403, detail="Invalid token")
    
    
@app.get("/getUserDetails")
async def get_user_details(email: str, db: Session = Depends(get_db)):
    # Veritabanından user_details tablosunda email ile kullanıcıyı sorguluyoruz
    user = db.query(UserDetails).filter(UserDetails.username == email).first()

    if user is None:
        raise HTTPException(status_code=404, detail="User not found")
    
    return {"user_id": user.user_id, "name": user.name, "username": user.username}


@app.post("/assign-driver")
def assign_driver_to_route(request: AssignDriverRequest, db: Session = Depends(get_db)):
    # Daha önce bu rota atanmış mı?
    existing = db.query(RouteAssignment).filter(RouteAssignment.route_number == request.route_number).first()
    if existing:
        raise HTTPException(status_code=400, detail="Bu rota zaten bir sürücüye atanmış.")

    # Yeni atama
    assignment = RouteAssignment(
        route_number=request.route_number,
        driver_user_id=request.driver_user_id
    )
    db.add(assignment)
    db.commit()
    db.refresh(assignment)

    return {"message": "Sürücü başarıyla atandı", "assignment_id": assignment.id}


@app.get("/drivers", response_model=List[UserDetailsResponse])
def get_available_drivers(db: Session = Depends(get_db)):
    # ROLE_DRIVER id'si 1 varsayıldı
    driver_ids = db.query(UserDetailsRoles.user_user_id).filter(UserDetailsRoles.roles_role_id == 1).all()
    user_ids = [id_tuple[0] for id_tuple in driver_ids]
    return db.query(UserDetails).filter(UserDetails.user_id.in_(user_ids)).all()



@app.get("/driver-routes/{driver_id}", response_model=List[int])
def get_driver_routes(driver_id: int, db: Session = Depends(get_db)):
    # route_number'ları getir
    route_numbers = db.query(RouteAssignment.route_number).filter(
        RouteAssignment.driver_user_id == driver_id
    ).all()
    return [r[0] for r in route_numbers] 