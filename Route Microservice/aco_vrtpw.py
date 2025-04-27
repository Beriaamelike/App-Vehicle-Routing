import numpy as np
import pandas as pd
import requests
from fastapi import FastAPI, UploadFile, File, Depends
from sqlalchemy.orm import Session
from create_tables import Aco, Route
from database import get_db
from database import engine, Base

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

    def calculate_distance_matrix(self):
        locations = ";".join([f"{node['yc']},{node['xc']}" for node in self.nodes])
        response = requests.get(f"{OSRM_API_URL}/{locations}?annotations=distance")

        if response.status_code != 200:
            raise Exception("OSRM API request failed!")

        data = response.json()
        matrix = np.array(data["distances"]) / 1000
        matrix = np.nan_to_num(matrix, nan=0.0)
        return matrix

def construct_solution(problem, pheromone_matrix, alpha, beta):
    n = len(problem.nodes)
    remaining_nodes = set(range(1, n))
    routes = []

    while remaining_nodes:
        route = [0]  # Her rotanın başlangıç noktası depo (index 0)
        capacity = 0
        current_node = 0

        while remaining_nodes:
            probabilities = []
            possible_nodes = []

            for next_node in remaining_nodes:
                distance = problem.distance_matrix[current_node][next_node]

                if capacity + problem.demands[next_node] <= problem.vehicle_capacity:
                    possible_nodes.append(next_node)
                    tau = pheromone_matrix[current_node][next_node] ** alpha
                    eta = (1 / max(distance, 1e-6)) ** beta
                    probabilities.append(tau * eta)

            if not possible_nodes:
                break

            probabilities = np.array(probabilities)
            probabilities /= np.sum(probabilities)
            next_node = np.random.choice(possible_nodes, p=probabilities)

            route.append(next_node)
            current_node = next_node
            capacity += problem.demands[next_node]
            remaining_nodes.remove(next_node)

        route.append(0)  # Her rotanın bitiş noktası depo (index 0)
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

def solve_aco(problem, alpha, beta, rho, iterations):
    np.random.seed(42)
    pheromone_matrix = np.ones(problem.distance_matrix.shape)
    best_distance = float('inf')
    best_routes = []

    for _ in range(iterations):
        routes = construct_solution(problem, pheromone_matrix, alpha, beta)
        route_distances = calculate_route_distances(problem, routes)
        distance = sum(route_distances)

        if distance < best_distance:
            best_distance = distance
            best_routes = routes

        pheromone_matrix *= (1 - rho)
        for route, route_distance in zip(routes, route_distances):
            pheromone_amount = 1 / (route_distance + 1e-6)
            for i in range(len(route)-1):
                pheromone_matrix[route[i]][route[i+1]] += pheromone_amount

    return best_routes, best_distance


@app.post("/optimize_routes")
async def optimize_routes(
    nodes_csv: UploadFile = File(...),
    vehicle_info_csv: UploadFile = File(...),
    alpha: float = 1.0,
    beta: float = 2.0,
    rho: float = 0.5,
    iterations: int = 100,
    db: Session = Depends(get_db)
):
    # Veriyi yükle ve işle
    nodes_df = pd.read_csv(nodes_csv.file)
    nodes_df.columns = nodes_df.columns.str.lower()

    vehicle_info_df = pd.read_csv(vehicle_info_csv.file)
    vehicle_capacity = int(vehicle_info_df['fleet_capacity'][0])
    num_vehicles = int(vehicle_info_df['fleet_size'][0])

    # Depot bilgisi (bu da rotalara dahil edilecek)
    depot = {
        "customer": "Depot",
        "xc": vehicle_info_df['fleet_start_x_coord'][0],
        "yc": vehicle_info_df['fleet_start_y_coord'][0],
        "demand": 0
    }

    # Müşteri düğümleri verisi
    customer_nodes = nodes_df.to_dict(orient="records")

    # VehicleRoutingProblem nesnesi oluştur
    problem = VehicleRoutingProblem(
        nodes=customer_nodes,
        depot=depot,
        vehicle_capacity=vehicle_capacity,
        num_vehicles=num_vehicles
    )

    # Ant Colony Optimization (ACO) ile çözümle
    best_routes, best_distance = solve_aco(problem, alpha, beta, rho, iterations)

    # En iyi rotaları veritabanına kaydet, depoyu da dahil et
    route_id = 1  # İlk rotayı başlat, ya da mevcut en yüksek ID ile
    for route_number, route in enumerate(best_routes, start=1):
        # İlk olarak depo bilgilerini ekle
        route_entry = Aco(
            route_number=route_number,
            route_order=0,  # Depot her zaman ilk sırada
            customer_id=0,  # Depot’un müşteri ID'si yok
            customer_name="Depot",
            customer_lat=depot["xc"],
            customer_lon=depot["yc"],
            demand=depot["demand"]
        )
        db.add(route_entry)

        # Şimdi rotadaki tüm müşterileri ekle (depo zaten eklendi)
        for order, node_idx in enumerate(route[1:], start=1):  # Depot'u atla, index 0
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

        db.commit()  # Her rota için değişiklikleri kaydet

    # Optimizasyon sonuçlarını döndür
    return {
    
        "route_customers": get_route_customers_with_depot(problem, best_routes)  # Depoyu da dahil et
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