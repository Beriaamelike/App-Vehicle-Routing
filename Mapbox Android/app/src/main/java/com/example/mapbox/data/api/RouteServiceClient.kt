package com.example.mapbox.data.api

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RouteServiceClient {

    private const val BASE_URL = "http://localhost:8000/"

    private val retrofit: Retrofit by lazy {
        RetrofitFactory.createRetrofit(BASE_URL)
    }


    val routeApi: RouteApi by lazy {
        retrofit.create(RouteApi::class.java)
    }
}