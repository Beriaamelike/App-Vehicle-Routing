# Araç Rotalama Problemi çözümü için gerekli kütüphaneler
import logging
import numpy as np
import requests
from fastapi import FastAPI, HTTPException, Depends
from sqlalchemy.orm import Session
from create_tables import  Customer
from database import get_db
from create_tables import Depot
import time

app = FastAPI()

# OSRM (Open Source Routing Machine) API URL'si: 
# Bu URL koordinatlar arasında mesafe ve süre matrislerini almak için kullanılır.
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

# API endpoint: Veritabanından müşteri ve depo verilerini çekip rotaları optimize eder
@app.post("/optimize_routes_from_db")
async def optimize_routes_from_db(
    alpha = 1.0,
    beta = 3.5,
    rho = 0.3,
    iterations: int = 200,
    db: Session = Depends(get_db)
):
    start_time = time.time()

    #Veritabanından depo ve müşteri bilgileri alınır. 
    depot = db.query(Depot.id, Depot.x, Depot.y, Depot.capacity, Depot.fleet_size, Depot.max_working_time).first()
    customers = db.query(Customer).all()

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


    #Algoritma çalıştırılır
    problem = VehicleRoutingProblem(
        nodes=customer_nodes,
        depot=depot_data,
        vehicle_capacity=depot.capacity,
        num_vehicles=depot.fleet_size,
        max_working_time=depot.max_working_time
    )

    best_routes, unassigned_info, route_durations = solve_aco(problem, alpha, beta, rho, iterations)
    unassigned_customers = get_unassigned_customers(problem, unassigned_info)

    total_distance = sum(calculate_route_distances(problem, best_routes))
    total_duration_minutes = sum(route_durations)

    end_time = time.time()
    total_duration = end_time - start_time
    route_demands = [sum(problem.demands[n] for n in r if n != 0) for r in best_routes]

    #Rota bilgileri
    return {
        "duration_seconds": round(total_duration, 2),
        "total_distance_km": round(total_distance, 2),
        "total_duration_minutes": round(total_duration_minutes, 2),
        "route_durations": [round(t, 2) for t in route_durations],
        "route_demands": route_demands,
        "route_customers": get_route_customers(problem, best_routes),
        "unassigned_customers": unassigned_customers
    }
