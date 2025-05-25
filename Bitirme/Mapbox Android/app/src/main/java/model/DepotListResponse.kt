package model

data class DepotListResponse(
    val depots: List<DepotItem>
)

data class DepotItem(
    val id: Int,
    val x: Double,
    val y: Double,
    val capacity: Int,
    val fleet_size: Int,
    val max_working_time: Int
)
