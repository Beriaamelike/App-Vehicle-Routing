package com.example.mapbox.data.api

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RouteServiceClient {
    // Marker API'nizin base URL'i (farklı olabilir)
    private const val BASE_URL = "http://192.168.11.124:8000/" // Örnek olarak farklı bir port

    private val retrofit: Retrofit by lazy {
        RetrofitFactory.createRetrofit(BASE_URL)
    }


    val routeApi: RouteApi by lazy {
        retrofit.create(RouteApi::class.java)
    }
}