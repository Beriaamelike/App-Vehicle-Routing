import logging
import numpy as np
import pandas as pd
import requests
from fastapi import FastAPI, HTTPException, Query, UploadFile, File, Depends
from sqlalchemy.orm import Session
from create_tables import Aco, Customer, CustomerCreate, Route
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


import user_details

SECRET_KEY = "" 
Base.metadata.create_all(bind=engine)

app = FastAPI()

OSRM_API_URL = "http://router.project-osrm.org/table/v1/driving"

class VehicleRoutingProblem:
    def __init__(self, nodes, depot, vehicle_capacity, num_vehicles, max_working_time):
        self.depot = depot
        self.nodes = [depot] + nodes
        self.vehicle_capacity = vehicle_capacity
        self.num_vehicles = num_vehicles
        self.distance_matrix = self.calculate_distance_matrix()
        self.demands = [node.get("demand", 0) for node in self.nodes]
        self.ready_times = [node.get("readime", 0) for node in self.nodes]
        self.due_times = [node.get("due_time", 99999) for node in self.nodes]
        self.service_times = [node.get("service_time", 0) for node in self.nodes]
        self.max_working_time = max_working_time

    def calculate_distance_matrix(self):
        try:
            locations = ";".join([f"{float(node['yc']):.8f},{float(node['xc']):.8f}" for node in self.nodes])
            logging.debug(f"OSRM locations: {locations}")
            response = requests.get(f"{OSRM_API_URL}/{locations}?annotations=distance", timeout=10)
            response.raise_for_status()
            data = response.json()
            matrix = np.array(data["distances"]) / 1000
            matrix = np.nan_to_num(matrix, nan=0.0)
            return matrix
        except requests.exceptions.RequestException as e:
            logging.error(f"OSRM API error: {str(e)}")
            raise HTTPException(status_code=500, detail="OSRM API request failed")


MAX_TIME = 1236  # örnek değer; bunu CSV'den alıp problem objesine de taşıyabilirsin

def construct_solution(problem, pheromone_matrix, alpha, beta):
    n = len(problem.nodes)
    remaining_nodes = set(range(1, n))
    routes = []
    
    logging.debug(f"Başlangıçta kalan düğümler: {remaining_nodes}")

    while remaining_nodes:
        route = [0]
        capacity = 0
        current_node = 0
        current_time = 0

        while remaining_nodes:
            probabilities = []
            possible_nodes = []
            logging.debug(f"Mevcut düğüm: {current_node}, Kalan düğümler: {remaining_nodes}")

            for next_node in remaining_nodes:
                distance = problem.distance_matrix[current_node][next_node]
                travel_time = distance
                arrival_time = current_time + travel_time
                start_service = max(arrival_time, problem.ready_times[next_node])
                finish_service = start_service + problem.service_times[next_node]

                if (
                    capacity + problem.demands[next_node] <= problem.vehicle_capacity and
                    finish_service <= problem.due_times[next_node] and
                    finish_service <= problem.max_working_time
                ):
                    if distance > 15.0:  # 3 km’den uzaksa alma
                        continue

                    possible_nodes.append((next_node, finish_service))
                    tau = pheromone_matrix[current_node][next_node] ** alpha
                    eta = (1 / max(distance, 1e-6)) ** beta
                    cutoff_factor = 1 / (1 + distance ** 2)

                    probabilities.append(tau * eta * cutoff_factor)

            if not possible_nodes:
                break

            probabilities = np.array(probabilities)
            probabilities /= np.sum(probabilities)
            selected_idx = np.random.choice(len(possible_nodes), p=probabilities)
            next_node, next_finish_time = possible_nodes[selected_idx]

            route.append(next_node)
            current_node = next_node
            capacity += problem.demands[next_node]
            current_time = next_finish_time
            remaining_nodes.remove(next_node)

        route.append(0)
        routes.append(route)
        logging.debug(f"Rota oluşturuldu: {route}")

    logging.debug(f"Tüm rotalar oluşturuldu: {routes}")
    return routes



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
    best_distance = float('inf')
    best_routes = []

    for iteration in range(iterations):
        iteration_start_time = time.time()
        logging.debug(f"Iteration {iteration + 1} başlatılıyor...")
        
        # Çözüm oluşturuluyor
        routes = construct_solution(problem, pheromone_matrix, alpha, beta)
        
        # Rotaların mesafelerini hesapla
        route_distances = calculate_route_distances(problem, routes)
        
        # Yayılma cezalarını ekle
        spread_penalties = [cluster_spread_penalty(problem.distance_matrix, r, penalty_weight=0.5) for r in routes]
        
        distance = np.std(route_distances) + 0.3 * np.mean(route_distances) + np.mean(spread_penalties)
        
        iteration_end_time = time.time()
        iteration_duration = iteration_end_time - iteration_start_time
        logging.debug(f"Iteration {iteration + 1} tamamlandı. Süre: {iteration_duration:.4f} saniye")

        # En iyi çözümün güncellenmesi
        if distance < best_distance:
            best_distance = distance
            best_routes = routes
            logging.debug(f"Yeni en iyi rota bulundu! Toplam mesafe: {best_distance:.4f} km")
        
        # Feromon matrisini güncelle
        pheromone_matrix *= (1 - rho)
        for route, route_distance in zip(routes, route_distances):
            pheromone_amount = (len(route) - 2) / (route_distance + 1e-6)
            for i in range(len(route)-1):
                pheromone_matrix[route[i]][route[i+1]] += pheromone_amount
        
    return best_routes, best_distance


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

    best_routes, best_distance = solve_aco(problem, alpha, beta, rho, iterations)

    # Toplam mesafeyi hesapla
    total_distance = sum(calculate_route_distances(problem, best_routes))

    end_time = time.time()  # ⏱ Optimizasyon bitiş zamanı
    duration = round(end_time - start_time, 2)

    return {
        "duration_seconds": duration,
        "total_distance_km": round(total_distance, 2),
        "route_customers": get_route_customers(problem, best_routes)
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
        best_routes, best_distance = solve_aco(problem, alpha, beta, rho, iterations)
        aco_duration = time.time() - aco_start_time
        logging.debug(f"ACO optimization completed. Duration: {aco_duration:.4f} seconds")

        total_distance = sum(calculate_route_distances(problem, best_routes))

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
                for order, customer_id in enumerate(route, start=1):
                    customer = next((cust for cust in customers if cust.id == customer_id), None)
                    if customer:
                        aco_entry = Aco(
                            route_number=route_number,
                            route_order=order,
                            customer_id=customer.id,
                            customer_name=f"Customer {customer.id}",
                            customer_lat=customer.xc,
                            customer_lon=customer.yc,
                            demand=customer.demand,
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

        return {
            "duration_seconds": round(total_duration, 2),
            "total_distance_km": round(total_distance, 2),
            "route_customers": get_route_customers(problem, best_routes)
        }

    except Exception as e:
        logging.error(f"Error occurred during optimization: {str(e)}")
        raise HTTPException(status_code=500, detail=f"Error occurred: {str(e)}")


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