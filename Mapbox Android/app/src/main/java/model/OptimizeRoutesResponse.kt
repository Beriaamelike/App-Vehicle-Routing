package model
import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize


@Parcelize
data class UniqueRoutesResponse(
    val route_customers: List<List<Route>>,
    val route_durations: List<Double>,
    val route_fuel_costs: List<Double>
) : Parcelable


data class OptimizeRoutesResponse(
    val duration_seconds: Double,
    val total_distance_km: Double,
    val total_duration_minutes: Double,
    val route_durations: List<Double>,
    val route_distances_km: List<Double>,
    val route_fuel_costs: List<Double>,
    val total_fuel_cost: Double,
    val route_demands: List<Int>,
    val route_customers: List<List<Customer>>,
    val unassigned_customers: List<UnassignedCustomer>?
)

