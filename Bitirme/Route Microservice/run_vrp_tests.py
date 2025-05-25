from database import SessionLocal
from create_tables import Customer, Depot
from aco_vrtpw import haversine_distance, optimize_routes_from_db
from vrp_tests import (
    test_capacity_constraint,
    test_time_windows,
    test_total_demand_info,
    test_vehicle_limit,
    test_geographic_consistency,
)

import asyncio

db = SessionLocal()

# Müşteri verilerini al
customers = db.query(Customer).all()
customer_nodes = [{
    "customer": f"Customer {c.id}",
    "xc": c.xc,
    "yc": c.yc,
    "demand": c.demand,
    "ready_time": c.ready_time,
    "due_time": c.due_time,
    "service_time": c.service_time
} for c in customers]
my_customer_list = [{"demand": c["demand"]} for c in customer_nodes]

# Asenkron optimize çağrısı
async def run_tests():
    print("🚀 Optimizasyon başlatılıyor...")
    result = await optimize_routes_from_db(db=db)
    print("✅ Optimizasyon tamamlandı.")

    
  
    test_total_demand_info(result, customers_data=my_customer_list)
    test_geographic_consistency(result)
    depot = db.query(Depot).first()
    test_capacity_constraint(result, vehicle_capacity=depot.capacity)

    depot = db.query(Depot).first()
    test_vehicle_limit(result, num_vehicles=depot.fleet_size)

    test_time_windows(result, osrm_distance_func=haversine_distance)


asyncio.run(run_tests())
