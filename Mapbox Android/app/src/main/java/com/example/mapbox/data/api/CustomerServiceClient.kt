package com.example.mapbox.data.api

import retrofit2.Retrofit

object CustomerServiceClient {

    private const val BASE_URL = "http://localhost:8000/"

    private val retrofit: Retrofit by lazy {
        RetrofitFactory.createRetrofit(CustomerServiceClient.BASE_URL)
    }

    val customerApi: CustomerApi by lazy {
        retrofit.create(CustomerApi::class.java)
    }
}