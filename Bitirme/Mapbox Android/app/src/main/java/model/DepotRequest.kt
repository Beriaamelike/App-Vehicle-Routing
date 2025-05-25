package model

data class DepotRequest(
    val x: Double,
    val y: Double,
    val capacity: Int,
    val fleet_size: Int,
    val max_working_time: Int,
    val fuel_consumption: Double
)
