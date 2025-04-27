package com.example.mapbox.data.api


import model.OptimizeRoutesResponse
import model.UniqueRoutesResponse
import okhttp3.MultipartBody
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface RouteApi {
    @GET("get_routes")  // API endpoint'i projenize göre ayarlayın
    fun getUniqueRoutes(): Call<UniqueRoutesResponse>

    @Multipart
    @POST("optimize_routes")
    fun optimizeRoutes(
        @Part nodesCsv: MultipartBody.Part,
        @Part vehicleInfoCsv: MultipartBody.Part
    ): Call<OptimizeRoutesResponse>
}