package model


data class UniqueRoutesResponse(
    val route_customers: List<List<Route>>
)

data class OptimizeRoutesResponse(
    val route_customers: List<List<Customer>>
)

