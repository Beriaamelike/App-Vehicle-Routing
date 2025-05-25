package com.example.mapbox.data.api

import model.DepotListResponse
import model.DepotRequest
import model.DepotResponse
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface DepotApi {

    @POST("add_depot")
    fun addDepot(@Body depot: DepotRequest): Call<DepotResponse>

    @GET("get_depot")
    fun getDepot(): Call<DepotListResponse>
}