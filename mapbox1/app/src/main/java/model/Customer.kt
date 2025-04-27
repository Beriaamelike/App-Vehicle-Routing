package model

import android.os.Parcelable
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


data class User(
    val id: String,
    val name: String,
    val role: String, // "Officer" veya "Driver"
    val email: String
)


