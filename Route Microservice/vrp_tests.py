#Kapasite Kısıtı Testi

from create_tables import Customer


def test_capacity_constraint(result, vehicle_capacity):
    for i, route in enumerate(result["route_customers"]):
        total_demand = sum(c["demand"] for c in route if c["customer"] != "Depot")
        print(f"🛻 Route {i+1} toplam talep: {total_demand}")
        assert total_demand <= vehicle_capacity, f"❌ Kapasite aşıldı: {total_demand} > {vehicle_capacity}"
    print("✅ Kapasite kısıtı testi başarılı.")




#Zaman Penceresi Testi

def test_time_windows(result, osrm_distance_func):
    for route in result["route_customers"]:
        current_time = 0
        for i in range(1, len(route) - 1):  # Depot'u atla, son depo hariç
            prev = route[i - 1]
            curr = route[i]

            travel = osrm_distance_func(prev["coordinates"], curr["coordinates"])
            arrival = current_time + travel
            ready = curr.get("ready_time", 0)
            due = curr.get("due_time", 99999)
            start_service = max(arrival, ready)

            if start_service > due:
                raise AssertionError(
                    f"❌ Zaman penceresi ihlali: {curr['customer']} için {start_service:.2f} > {due}"
                )
            current_time = start_service + curr.get("service_time", 0)

    print("✅ Zaman penceresi testi başarılı.")



# Araç Sayısı ve Yetersizlik Testi

def test_vehicle_limit(result, num_vehicles):
    actual_routes = len(result["route_customers"])
    assert actual_routes <= num_vehicles, f"❌ Araç sınırı aşıldı: {actual_routes} > {num_vehicles}"
    print("✅ Araç sayısı kısıtı testi başarılı.")



# Toplam Talep Karşılandı mı?

def test_total_demand_info(result, customers_data):
    expected = len(customers_data)
    actual = sum(1 for route in result["route_customers"] for c in route if c["customer"] != "Depot")
    print(f"ℹ️ Servis edilen müşteri sayısı: {actual} / {expected}")



#Cluster (Coğrafi Tutarlılık) Testi

def test_geographic_consistency(result):
    for route in result["route_customers"]:
        lats = [c["coordinates"]["lat"] for c in route]
        lons = [c["coordinates"]["lon"] for c in route]
        lat_spread = max(lats) - min(lats)
        lon_spread = max(lons) - min(lons)
        assert lat_spread < 0.1 and lon_spread < 0.1, f"❌ Rota çok dağınık: lat {lat_spread}, lon {lon_spread}"
    print("✅ Rotalar coğrafi olarak tutarlı.")


  
