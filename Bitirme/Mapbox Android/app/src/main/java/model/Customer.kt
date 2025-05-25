package model

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize



@Parcelize
data class Route(
    val route_number: Int,
    val route_order: Int,
    val product_id: Int,
    val customer_id: Int,
    val customer_name: String,
    val coordinates: Coordinate,
    val demand: Double
) : Parcelable

data class RouteInfoResponse(
    val route_number: Int,
    val route_duration: Double,
    val route_fuel_cost: Double
)

@Parcelize
data class Coordinate(
    val lat: Double,
    val lon: Double
) : Parcelable


@Parcelize
data class Customer(
    val customer: String,
    val product_id: Int,
    val coordinates: Coordinate,
    val demand: Int
) : Parcelable

@Parcelize
data class UnassignedCustomer(
    val customer: String,
    val coordinates: Coordinate,
    val demand: Int,
    val excluded_reason: String
) : Parcelable

data class Customers(
    val customer_id: Int,
    val product_id: Int,
    val xc: Double,
    val yc: Double,
    val demand: Int,
    val ready_time: Int,
    val due_time: Int,
    val service_time: Int
)



data class User(
    val user_id: Int,
    val name: String,
    val username: String
)


data class UserDetailsResponse(
    val user_id: Int,
    val name: String,
    val username: String,// email
    val role: String
)

data class AssignDriverRequest(
    @SerializedName("route_number")
    val routeNumber: Int,

    @SerializedName("driver_user_id")
    val driverUserId: Int
)