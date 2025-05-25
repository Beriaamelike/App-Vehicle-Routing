import logging
from typing import List
import numpy as np
import pandas as pd
import requests
from fastapi import FastAPI, HTTPException, UploadFile, File, Depends
from sqlalchemy.orm import Session
from create_tables import Aco, AssignDriverRequest, Customer, CustomerCreate, Route, RouteAssignment, RouteInfoResponse
from database import get_db, engine, Base
from fastapi import APIRouter
from create_tables import Depot, UserDetails
import time
from create_tables import DepotCreate
from sqlalchemy import func
import os
import jwt
from datetime import datetime, timezone
import time
import logging
from sqlalchemy.orm import Session
from fastapi import HTTPException


SECRET_KEY = "A0B1C2D3E4F5061728394A5B6C7D8E9F1011121314151617181920212223242526272829303132333435363738393A3B3C3D3E3F40414243444546474849"
Base.metadata.create_all(bind=engine)

app = FastAPI()

OSRM_API_URL = "http://router.project-osrm.org/table/v1/driving"

# Bu sınıf Araç Rotalama Probleminin verilerini ve çözüm için gerekli hazırlıkları içerir
class VehicleRoutingProblem:
    def __init__(self, nodes, depot, vehicle_capacity, num_vehicles, max_working_time):
        self.depot = depot   # Depo bilgisi
        self.nodes = [depot] + nodes  # Tüm noktalar
        self.vehicle_capacity = vehicle_capacity  # Araç başına maksimum taşıma kapasitesi
        self.num_vehicles = num_vehicles  # Toplam araç sayısı

        # OSRM üzerinden hesaplanan mesafe ve süre matrisleri
        self.distance_matrix, self.duration_matrix = self.calculate_distance_and_duration_matrices()


        self.demands = [node.get("demand", 0) for node in self.nodes] # Her noktanın talep miktarı
        self.ready_times = [node.get("ready_time", 0) for node in self.nodes] # Servis başlangıç zamanı
        self.due_times = [node.get("due_time", 99999) for node in self.nodes] # Servis bitiş zamanı
        self.service_times = [node.get("service_time", 0) for node in self.nodes] # Servis süresi 
        self.max_working_time = max_working_time # Araç başına günlük maksimum çalışma süresi(dk)

    # OSRM API üzerinden mesafe ve süre matrislerini hesaplar
    def calculate_distance_and_duration_matrices(self):
        try:
            locations = ";".join([f"{float(node['yc']):.8f},{float(node['xc']):.8f}" for node in self.nodes])
            url = f"{OSRM_API_URL}/{locations}?annotations=distance,duration"
            response = requests.get(url, timeout=10)
            response.raise_for_status()
            data = response.json()

            distance_matrix = np.array(data["distances"]) / 1000  # kilometre cinsinden hesaplar
            duration_matrix = np.array(data["durations"]) / 60    # dakika cinsinden hesaplar

            return distance_matrix, duration_matrix
        except requests.exceptions.RequestException as e:
            logging.error(f"OSRM API error: {str(e)}")
            raise HTTPException(status_code=500, detail="OSRM API request failed")
        

# Verilen feromon matrisine göre bir çözüm rotası oluşturur
def construct_solution(problem, pheromone_matrix, alpha, beta):
    n = len(problem.nodes)
    remaining_nodes = set(range(1, n))
    routes = []
    route_count = 0
    unassigned_info = {}

    # Her araç için rota oluştur
    while remaining_nodes and route_count < problem.num_vehicles:
        route = [0]  # Başlangıç noktası depo
        capacity = 0
        current_node = 0
        current_time = 0

        # Uygun müşteri adaylarını değerlendirir
        while True:
            candidates = []
            for next_node in remaining_nodes:
                demand = problem.demands[next_node]

                # Kapasite kontrolü
                if demand + capacity > problem.vehicle_capacity:
                    unassigned_info[next_node] = "kapasite aşıldı"
                    continue

                distance = problem.distance_matrix[current_node][next_node]
                duration = problem.duration_matrix[current_node][next_node]
                arrival = current_time + duration
                start_service = max(arrival, problem.ready_times[next_node])
                finish_service = start_service + problem.service_times[next_node]

                # Zaman kısıtlarının kontrolü
                if finish_service > problem.due_times[next_node]:
                    unassigned_info[next_node] = "zaman penceresi aşıldı"
                    continue
                if finish_service > problem.max_working_time:
                    unassigned_info[next_node] = "günlük çalışma süresi aşıldı"
                    continue

                tau = pheromone_matrix[current_node][next_node] ** alpha
                eta = (1 / max(distance, 1e-6)) ** beta
                prob = tau * eta
                candidates.append((next_node, prob, finish_service, demand))

            if not candidates:
                break

            # Olasılığa göre müşteri seçimi
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
                del unassigned_info[selected_node]

        route.append(0)  # Depoya dönüş
        routes.append(route)
        route_count += 1

    return routes, unassigned_info

# Atanamayan müşterilerin detaylarını listeler
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
            "excluded_reason": reason
        })
    return customers


# Her rotanın toplam mesafesini hesaplar
def calculate_route_distances(problem, routes):
    return [sum(problem.distance_matrix[route[i]][route[i+1]] for i in range(len(route)-1)) for route in routes]


# Her rota için müşteri bilgilerini getirir
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



# Rotadaki seyahat, bekleme ve hizmet sürelerinin toplamını verir
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


# ACO algoritmasını çalıştırarak en iyi rotaları bulur
def solve_aco(problem, alpha, beta, rho, iterations):
    np.random.seed(42)
    pheromone_matrix = 1 / (problem.distance_matrix + np.finfo(float).eps)
    best_routes = []
    best_distance = float("inf")
    best_unassigned_info = {}
    best_total_times = []

    for _ in range(iterations):
        routes, unassigned_info = construct_solution(problem, pheromone_matrix, alpha, beta)
        route_distances = calculate_route_distances(problem, routes)
        route_times = calculate_total_time_with_service_and_wait(problem, routes)
        total_distance = sum(route_distances)

        if total_distance < best_distance:
            best_distance = total_distance
            best_routes = routes
            best_unassigned_info = unassigned_info
            best_total_times = route_times

        # Feromon güncelleme
        pheromone_matrix *= (1 - rho)
        for route, route_distance in zip(routes, route_distances):
            pheromone_amount = (len(route) - 2) / (route_distance + 1e-6)
            for i in range(len(route)-1):
                pheromone_matrix[route[i]][route[i+1]] += pheromone_amount

    return best_routes, best_unassigned_info, best_total_times

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
    max_working_time = int(vehicle_info_df['fleet_max_working_time'][0]) 


    depot = {
        "customer": "Depo",
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

    end_time = time.time() 
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
            "customer": "Depo",
            "coordinates": {"lat": problem.depot["yc"], "lon": problem.depot["xc"]},
            "demand": problem.depot["demand"]
        }
        customer_info.append(depot_info)

        # Şimdi rotadaki tüm müşterileri ekle
        for node_idx in route[1:]: 
            customer_info.append({
                "customer": problem.nodes[node_idx].get("customer", f"Node {node_idx}"),
                "coordinates": {"lat": problem.nodes[node_idx]["xc"], "lon": problem.nodes[node_idx]["yc"]},
                "demand": problem.nodes[node_idx].get("demand", 0)
            })
        
        route_customers.append(customer_info)

    return route_customers


@app.get("/get_routes")
async def get_routes(db: Session = Depends(get_db)):
    routes = db.query(Aco).all()
    
    route_data = {}
    route_stats = {}

    for route in routes:
        rn = route.route_number
        if rn not in route_data:
            route_data[rn] = []
            route_stats[rn] = {
                "route_duration": route.route_duration,
                "route_fuel_cost": route.route_fuel_cost
            }

        route_data[rn].append({
            "route_number": rn,
            "route_order": route.route_order,
            "customer_id": route.customer_id,
            "customer_name": route.customer_name,
            "product_id": route.product_id,
            "coordinates": {"lat": route.customer_lat, "lon": route.customer_lon},
            "demand": route.demand,
            "created_at": route.created_at
        })

    return {
        "route_customers": list(route_data.values()),
        "route_durations": [round(route_stats[r]["route_duration"], 2) for r in sorted(route_stats)],
        "route_fuel_costs": [round(route_stats[r]["route_fuel_cost"], 2) for r in sorted(route_stats)],
    }


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
        "customer": "Depo",
        "xc": vehicle_info_df['fleet_start_x_coord'][0],
        "yc": vehicle_info_df['fleet_start_y_coord'][0],
        "demand": 0
    }

    # Müşteri düğümleri verisini CSV'den al
    customer_nodes = nodes_df.to_dict(orient="records")

    # Depoyu veritabanına kaydet
    route_entry = Route(
        customer_id=0,  # Depot'un müşteri ID'si yok
        customer_name="Depo",
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
            "product_id": route.product_id,
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
        max_working_time=depot.max_working_time,
        fuel_consumption=depot.fuel_consumption  
    )
    db.add(db_depot)
    db.commit()
    db.refresh(db_depot)
    return {"message": "Depo başarıyla eklendi.", "depot_id": db_depot.id}


@app.post("/add_customer")
async def add_customer(customer: CustomerCreate, db: Session = Depends(get_db)):
    new_customer = Customer(
        customer_id=customer.customer_id,
        product_id=customer.product_id,
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
    return {"message": "Müşteri başarıyla eklendi", "id": new_customer.customer_id}


@app.post("/add_customers")
async def add_customers(customers: List[CustomerCreate], db: Session = Depends(get_db)):
    added_customers = []

    for customer in customers:
        new_customer = Customer(
            customer_id=customer.customer_id,
            product_id=customer.product_id,
            xc=customer.xc,
            yc=customer.yc,
            demand=customer.demand,
            ready_time=customer.ready_time,
            due_time=customer.due_time,
            service_time=customer.service_time
        )
        db.add(new_customer)
        added_customers.append(customer.customer_id)

    db.commit()
    return {"message": f"{len(added_customers)} müşteri başarıyla eklendi", "added_customer_ids": added_customers}



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
            "fleet_size": d.fleet_size,
            "max_working_time": d.max_working_time,
            "fuel_consumption": d.fuel_consumption  
        } for d in depots
    ]}


router = APIRouter()



logging.basicConfig(level=logging.DEBUG)

# API endpoint: Veritabanından rota optimizasyonu yapan ve sonucu kaydeden uç nokta
@router.post("/optimize_routes_from_db")
async def optimize_routes_from_db(
    alpha: float = 1.0,
    beta: float = 3.5,
    rho: float = 0.3,
    iterations: int = 200,
    db: Session = Depends(get_db)
):
    start_time = time.time()

    try:
        # Depo bilgisi alınır
        depot = db.query(Depot).first()
        if not depot:
            raise HTTPException(status_code=404, detail="Depo verisi bulunamadı")

        # Müşteri verileri alınır
        customers = db.query(Customer).all()

        # Depo düğümü tanımlanır
        depot_data = {
            "customer": "Depo",
            "xc": depot.x,
            "yc": depot.y,
            "demand": 0
        }

        # Müşteri düğümleri oluşturulur
        customer_nodes = [
            {
                "customer": f"Müşteri ID: {c.customer_id}",
                "xc": c.xc,
                "yc": c.yc,
                "product_id": c.product_id,
                "demand": c.demand,
                "ready_time": c.ready_time,
                "due_time": c.due_time,
                "service_time": c.service_time
            } for c in customers
        ]

        # Problem nesnesi oluşturulur
        problem = VehicleRoutingProblem(
            nodes=customer_nodes,
            depot=depot_data,
            vehicle_capacity=depot.capacity,
            num_vehicles=depot.fleet_size,
            max_working_time=depot.max_working_time
        )

        # ACO algoritması çalıştırılır
        best_routes, unassigned_info, route_durations = solve_aco(problem, alpha, beta, rho, iterations)

        # Atanmamış müşteriler bulunur
        unassigned_customers = get_unassigned_customers(problem, unassigned_info)

        # Her rotanın mesafesi hesaplanır
        route_distances = calculate_route_distances(problem, best_routes)
        total_distance = sum(route_distances)

        FUEL_PRICE_PER_LITER = 47.0  # Sabit benzin fiyatı (TL)

        # Yakıt tüketimi ve maliyet hesaplamaları yapılır
        route_fuel_liters = [distance * float(depot.fuel_consumption) for distance in route_distances]
        route_fuel_costs = [liters * FUEL_PRICE_PER_LITER for liters in route_fuel_liters]
        total_fuel_cost = sum(route_fuel_costs)
        total_duration_minutes = sum(route_durations)

        # Veri tabanına kayıt için zaman damgası
        current_time = datetime.now(timezone.utc)

        # Daha önceki maksimum rota numarası belirlenir
        max_route_number = db.query(func.max(Aco.route_number)).scalar() or 0

        # Her bir rota için ACO tablosuna kayıt yapılır
        for route_number, route in enumerate(best_routes, start=max_route_number + 1):
            duration = route_durations[route_number - max_route_number - 1]
            fuel_cost = route_fuel_costs[route_number - max_route_number - 1]

            # Depo başlangıç noktası eklenir
            db.add(Aco(
                route_number=route_number,
                route_order=0,
                customer_id=0,
                customer_name="Depo",
                customer_lat=depot.x,
                customer_lon=depot.y,
                demand=0,
                route_duration=duration,
                route_fuel_cost=fuel_cost,
                created_at=current_time
            ))

            # Müşteri düğümleri eklenir
            for order, node_index in enumerate(route[1:-1], start=1):
                customer_node = problem.nodes[node_index]
                customer_id_str = customer_node["customer"].split()[-1]
                customer_id = int(customer_id_str) if customer_id_str.isdigit() else None

                if customer_id:
                    db.add(Aco(
                        route_number=route_number,
                        route_order=order,
                        customer_id=customer_id,
                        customer_name=f"{customer_id}",
                        product_id=customer_node["product_id"],
                        customer_lat=customer_node["xc"],
                        customer_lon=customer_node["yc"],
                        demand=customer_node["demand"],
                        route_duration=duration,
                        route_fuel_cost=fuel_cost,
                        created_at=current_time
                    ))

            # Depoya dönüş noktası eklenir
            db.add(Aco(
                route_number=route_number,
                route_order=len(route) + 1,
                customer_id=depot.id,
                customer_name="Depo",
                customer_lat=depot.x,
                customer_lon=depot.y,
                demand=0,
                route_duration=duration,
                route_fuel_cost=fuel_cost,
                created_at=current_time
            ))

        db.commit()

        # Atanan müşterilerin customer tablosundan silinmesi
        assigned_customer_ids = set()
        for route in best_routes:
            for node_index in route:
                if node_index == 0:
                    continue
                customer_node = problem.nodes[node_index]
                customer_id_str = customer_node["customer"].split()[-1]
                if customer_id_str.isdigit():
                    assigned_customer_ids.add(int(customer_id_str))

        if assigned_customer_ids:
            db.query(Customer).filter(Customer.customer_id.in_(assigned_customer_ids)).delete(synchronize_session=False)
            db.commit()

        end_time = time.time()

        # Rota başına talepler hesaplanır
        route_demands = [sum(problem.demands[n] for n in r if n != 0) for r in best_routes]

        # Sonuçlar kullanıcıya döndürülür
        return {
            "duration_seconds": round(end_time - start_time, 2),
            "total_distance_km": round(total_distance, 2),
            "total_duration_minutes": round(total_duration_minutes, 2),
            "route_durations": [round(t, 2) for t in route_durations],
            "route_distances_km": [round(d, 2) for d in route_distances],
            "route_fuel_liters": [round(l, 2) for l in route_fuel_liters],
            "route_fuel_costs": [round(c, 2) for c in route_fuel_costs],
            "total_fuel_cost": round(total_fuel_cost, 2),
            "route_demands": route_demands,
            "route_customers": get_route_customers(problem, best_routes),
            "unassigned_customers": unassigned_customers
        }

    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Hata oluştu: {str(e)}")


# Rotaların servis süreleri ve bekleme süreleri dahil toplam süresini hesaplar
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


# Son kaydedilen rotaları getirir
@app.get("/get_recent_routes")
async def get_recent_routes(db: Session = Depends(get_db)):
    last_route = db.query(Aco).order_by(Aco.created_at.desc()).first()
    if not last_route:
        raise HTTPException(status_code=404, detail="No routes found")

    routes = db.query(Aco).filter(Aco.created_at == last_route.created_at).all()

    route_data = {}
    route_stats = {}

    # Her rotayı sırayla gruplar ve bilgileri toplar
    for route in routes:
        rn = route.route_number
        if rn not in route_data:
            route_data[rn] = []
            route_stats[rn] = {
                "route_duration": route.route_duration,
                "route_fuel_cost": route.route_fuel_cost
            }

        route_data[rn].append({
            "route_number": rn,
            "route_order": route.route_order,
            "customer_id": route.customer_id,
            "customer_name": route.customer_name,
            "product_id": route.product_id,
            "coordinates": {"lat": route.customer_lat, "lon": route.customer_lon},
            "demand": route.demand,
            "created_at": route.created_at
        })

    return {
        "route_customers": list(route_data.values()),
        "route_durations": [round(route_stats[r]["route_duration"], 2) for r in sorted(route_stats)],
        "route_fuel_costs": [round(route_stats[r]["route_fuel_cost"], 2) for r in sorted(route_stats)],
    }



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


# Sürücünün atandığı rotaları getiren endpoint
@app.get("/driver-routes/{driver_id}", response_model=List[int])
def get_driver_routes(driver_id: int, db: Session = Depends(get_db)):
    # route_number'ları getir
    route_numbers = db.query(RouteAssignment.route_number).filter(
        RouteAssignment.driver_user_id == driver_id
    ).all()
    return [r[0] for r in route_numbers] 



#rota bilgilerini getiren endpoint
@router.get("/get_route_info/{route_number}", response_model=RouteInfoResponse)
def get_route_info(route_number: int, db: Session = Depends(get_db)):
    route = db.query(Aco).filter(Aco.route_number == route_number).first()
    if not route:
        raise HTTPException(status_code=404, detail="Rota bulunamadı")

    return {
        "route_number": route.route_number,
        "route_duration": route.route_duration,
        "route_fuel_cost": route.route_fuel_cost
    }

app.include_router(router)