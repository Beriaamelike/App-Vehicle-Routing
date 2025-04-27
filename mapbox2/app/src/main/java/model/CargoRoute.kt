package model

data class CargoRoute(
    val id: String,
    val driverName: String,
    val vehiclePlate: String,
    val stops: List<String>,
    val date: String,
    val status: String
)