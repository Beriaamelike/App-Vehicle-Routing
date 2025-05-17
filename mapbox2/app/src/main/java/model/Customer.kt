package model

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize



@Parcelize
data class Route(
    val route_number: Int,
    val route_order: Int,
    val customer_id: Int,
    val customer_name: String,
    val coordinates: Coordinate,
    val demand: Double
) : Parcelable

@Parcelize
data class Coordinate(
    val lat: Double,
    val lon: Double
) : Parcelable


@Parcelize
data class Customer(
    val customer: String,
    val coordinates: Coordinate,
    val demand: Int
) : Parcelable


data class Customers(
    val id: Int? = null,
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