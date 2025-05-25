package com.example.mapbox.data.api

import retrofit2.Retrofit

object DepotServiceClient {

    private const val BASE_URL = "http://localhost:8000/"

    private val retrofit: Retrofit by lazy {
        RetrofitFactory.createRetrofit(DepotServiceClient.BASE_URL)
    }

    val depotApi: DepotApi by lazy {
        retrofit.create(DepotApi::class.java)
    }
}