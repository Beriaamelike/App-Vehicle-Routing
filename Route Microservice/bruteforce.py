from fastapi import FastAPI
import requests
from itertools import permutations

app = FastAPI()

# Depo ve müşteri koordinatları (lat, lon formatında)
depot_coords = (39.9213722, 32.853286)
customer_coords = [
    (39.94398521, 32.889785),
    (39.93158266, 32.889785),
    (39.93399941, 32.82216911),
    (39.92458861, 32.82927702),
]

# OSRM mesafe hesaplama
def get_osrm_distance_km(coords):
    coord_str = ";".join([f"{lon},{lat}" for lat, lon in coords])
    url = f"http://router.project-osrm.org/route/v1/driving/{coord_str}?overview=false"
    response = requests.get(url)
    if response.status_code == 200:
        return response.json()['routes'][0]['distance'] / 1000
    return float("inf")

# Tüm permütasyonları deneyip yazdır
results = []
print("Tüm permütasyonlar için OSRM mesafeleri:\n")
for i, perm in enumerate(permutations(customer_coords), 1):
    full_route = [depot_coords] + list(perm) + [depot_coords]
    dist = get_osrm_distance_km(full_route)
    print(f"{i}. Rota:")
    for lat, lon in perm:
        print(f"   - ({lat}, {lon})")
    print(f"   → Toplam mesafe: {round(dist, 2)} km\n")
    results.append((perm, dist))

# En kısa rotayı bul ve yazdır
optimal_route, optimal_distance = min(results, key=lambda x: x[1])
print("\n✅ En kısa OSRM mesafe (km):", round(optimal_distance, 2))
print("✅ Optimal müşteri sırası:")
for i, (lat, lon) in enumerate(optimal_route, 1):
    print(f"   {i}. ({lat}, {lon})")
