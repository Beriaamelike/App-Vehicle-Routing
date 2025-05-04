import numpy as np
import pandas as pd
import requests
from fastapi import FastAPI, UploadFile, File, Depends
from sqlalchemy.orm import Session
from create_tables import Aco, Customer, CustomerCreate, Route
from database import get_db
from database import engine, Base
from fastapi import FastAPI, Depends
from pydantic import BaseModel
from sqlalchemy.orm import Session
from database import get_db
from create_tables import Depot
import time  

Base.metadata.create_all(bind=engine)

app = FastAPI()

OSRM_API_URL = "http://router.project-osrm.org/table/v1/driving"

class VehicleRoutingProblem:
    def __init__(self, nodes, depot, vehicle_capacity, num_vehicles):
        self.depot = depot
        self.nodes = [depot] + nodes
        self.vehicle_capacity = vehicle_capacity
        self.num_vehicles = num_vehicles
        self.distance_matrix = self.calculate_distance_matrix()
        self.demands = [node.get("demand", 0) for node in self.nodes]
        self.ready_times = [node.get("ready_time", 0) for node in self.nodes]
        self.due_times = [node.get("due_time", 99999) for node in self.nodes]
        self.service_times = [node.get("service_time", 0) for node in self.nodes]


    def calculate_distance_matrix(self):
        locations = ";".join([f"{node['yc']},{node['xc']}" for node in self.nodes])
        response = requests.get(f"{OSRM_API_URL}/{locations}?annotations=distance")

        if response.status_code != 200:
            raise Exception("OSRM API request failed!")

        data = response.json()
        matrix = np.array(data["distances"]) / 1000
        matrix = np.nan_to_num(matrix, nan=0.0)
        return matrix

MAX_TIME = 1236  # örnek değer; bunu CSV'den alıp problem objesine de taşıyabilirsin

def construct_solution(problem, pheromone_matrix, alpha, beta):
    n = len(problem.nodes)
    remaining_nodes = set(range(1, n))
    routes = []

    while remaining_nodes:
        route = [0]
        capacity = 0
        current_node = 0
        current_time = 0

        while remaining_nodes:
            probabilities = []
            possible_nodes = []

            for next_node in remaining_nodes:
                distance = problem.distance_matrix[current_node][next_node]
                travel_time = distance
                arrival_time = current_time + travel_time
                start_service = max(arrival_time, problem.ready_times[next_node])
                finish_service = start_service + problem.service_times[next_node]

                if (
                    capacity + problem.demands[next_node] <= problem.vehicle_capacity and
                    finish_service <= problem.due_times[next_node] and
                    finish_service <= MAX_TIME

                ):
                    
                    if distance > 15.0:  # 3 km’den uzaksa alma
                        continue

                    possible_nodes.append((next_node, finish_service))
                    tau = pheromone_matrix[current_node][next_node] ** alpha
                    eta = (1 / max(distance, 1e-6)) ** beta

# Eklenen kısım: uzak olanlara baskı uygulayan yumuşak kesme
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


def solve_aco(problem, alpha, beta, rho, iterations):
    np.random.seed(42)
    pheromone_matrix = 1 / (problem.distance_matrix + np.finfo(float).eps)
    best_distance = float('inf')
    best_routes = []

    for _ in range(iterations):
        routes = construct_solution(problem, pheromone_matrix, alpha, beta)
        route_distances = calculate_route_distances(problem, routes)
        # Rota yayılım cezası ekle (aynı rotadaki noktalar birbirine yakın olsun)
        spread_penalties = [cluster_spread_penalty(problem.distance_matrix, r, penalty_weight=0.5) for r in routes]
        distance = np.std(route_distances) + 0.3 * np.mean(route_distances) + np.mean(spread_penalties)




        if distance < best_distance:
            best_distance = distance
            best_routes = routes

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
    iterations: int = 200,
    db: Session = Depends(get_db)
):
    start_time = time.time()  # ⏱ Optimizasyon başlangıç zamanı

    nodes_df = pd.read_csv(nodes_csv.file)
    nodes_df.columns = nodes_df.columns.str.lower()

    vehicle_info_df = pd.read_csv(vehicle_info_csv.file)
    vehicle_capacity = int(vehicle_info_df['fleet_capacity'][0])
    num_vehicles = int(vehicle_info_df['fleet_size'][0])

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
        num_vehicles=num_vehicles
    )

    best_routes, best_distance = solve_aco(problem, alpha, beta, rho, iterations)

    # Toplam mesafeyi hesapla
    total_distance = sum(calculate_route_distances(problem, best_routes))

    # Rotaları veritabanına kaydet
    for route_number, route in enumerate(best_routes, start=1):
        db.add(Aco(
            route_number=route_number,
            route_order=0,
            customer_id=0,
            customer_name="Depot",
            customer_lat=depot["xc"],
            customer_lon=depot["yc"],
            demand=depot["demand"]
        ))

        for order, node_idx in enumerate(route[1:], start=1):
            customer_info = problem.nodes[node_idx]
            db.add(Aco(
                route_number=route_number,
                route_order=order,
                customer_id=node_idx,
                customer_name=customer_info.get("customer", f"Node {node_idx}"),
                customer_lat=customer_info["xc"],
                customer_lon=customer_info["yc"],
                demand=customer_info.get("demand", 0)
            ))
        db.commit()

    end_time = time.time()  # ⏱ Optimizasyon bitiş zamanı
    duration = round(end_time - start_time, 2)

    return {
        "duration_seconds": duration,
        "total_distance_km": round(total_distance, 2),
        "route_customers": get_route_customers_with_depot(problem, best_routes)
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
            "demand": route.demand
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

class DepotCreate(BaseModel):
    x: float
    y: float
    capacity: int
    fleet_size: int

@app.post("/add_depot")
async def add_depot(depot: DepotCreate, db: Session = Depends(get_db)):
    db_depot = Depot(
        x=depot.x,
        y=depot.y,
        capacity=depot.capacity,
        fleet_size=depot.fleet_size
    )
    db.add(db_depot)
    db.commit()
    db.refresh(db_depot)
    return {"message": "Depo başarıyla eklendi.", "depot_id": db_depot.id}


@app.post("/add_customer")
async def add_customer(customer: CustomerCreate, db: Session = Depends(get_db)):
    new_customer = Customer(
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


@app.post("/optimize_routes_from_db")
async def optimize_routes_from_db(
    alpha: float = 1.0,
    beta: float = 8.0,
    rho: float = 0.2,
    iterations: int = 200,
    db: Session = Depends(get_db)
):
    # Veritabanından depot ve müşteri bilgilerini al
    depot_record = db.query(Depot).order_by(Depot.id.desc()).first()
    customer_records = db.query(Customer).all()

    if depot_record is None or not customer_records:
        return {"error": "Depo veya müşteri verisi eksik."}

    # Depot verisini hazırla
    depot = {
        "customer": "Depot",
        "xc": depot_record.x,
        "yc": depot_record.y,
        "demand": 0
    }

    # Müşteri verilerini hazırla
    customer_nodes = [
        {
            "customer": f"Müşteri {i+1}",
            "xc": c.xc,
            "yc": c.yc,
            "demand": c.demand,
            "ready_time": c.ready_time,
            "due_time": c.due_time,
            "service_time": c.service_time
        }
        for i, c in enumerate(customer_records)
    ]

    problem = VehicleRoutingProblem(
        nodes=customer_nodes,
        depot=depot,
        vehicle_capacity=depot_record.capacity,
        num_vehicles=depot_record.fleet_size
    )

    best_routes, best_distance = solve_aco(problem, alpha, beta, rho, iterations)

    for route_number, route in enumerate(best_routes, start=1):
        route_entry = Aco(
            route_number=route_number,
            route_order=0,
            customer_id=0,
            customer_name="Depot",
            customer_lat=depot["xc"],
            customer_lon=depot["yc"],
            demand=depot["demand"]
        )
        db.add(route_entry)

        for order, node_idx in enumerate(route[1:], start=1):
            customer_info = problem.nodes[node_idx]
            route_entry = Aco(
                route_number=route_number,
                route_order=order,
                customer_id=node_idx,
                customer_name=customer_info.get("customer", f"Node {node_idx}"),
                customer_lat=customer_info["xc"],
                customer_lon=customer_info["yc"],
                demand=customer_info.get("demand", 0)
            )
            db.add(route_entry)

        db.commit()

    return {
        "route_customers": get_route_customers_with_depot(problem, best_routes)
    }
