package com.example.mapbox.data.api


import model.AssignDriverRequest
import model.OptimizeRoutesResponse
import model.RouteInfoResponse
import model.UniqueRoutesResponse
import model.User
import model.UserDetailsResponse
import okhttp3.MultipartBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface RouteApi {
    @GET("get_routes")
    fun getUniqueRoutes(): Call<UniqueRoutesResponse>

    @GET("get_route_info/{route_number}")
    fun getRouteInfoByNumber(@Path("route_number") routeNumber: Int): Call<RouteInfoResponse>


    @GET("get_recent_routes")
    fun getOptimizeRoutes(): Call<UniqueRoutesResponse>

    @Multipart
    @POST("optimize_routes")
    fun optimizeRoutes(
        @Part nodesCsv: MultipartBody.Part,
        @Part vehicleInfoCsv: MultipartBody.Part
    ): Call<OptimizeRoutesResponse>

    @POST("optimize_routes_from_db")
    fun optimizeRoutesFromDb(): Call<OptimizeRoutesResponse>

    @GET("getUserDetails")
    fun getUserDetails(@Query("email") email: String): Call<UserDetailsResponse>


    @GET("drivers")
    fun getAvailableDrivers(): Call<List<User>>

    @POST("assign-driver")
    fun assignDriverToRoute(@Body request: AssignDriverRequest): Call<Void>

    @GET("driver-routes/{driver_id}")
    fun getRoutesByDriver(@Path("driver_id") driverId: Int): Call<List<Int>>


}