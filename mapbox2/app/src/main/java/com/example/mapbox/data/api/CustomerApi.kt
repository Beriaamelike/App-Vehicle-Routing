package com.example.mapbox.data.api

import model.CustomerResponse
import model.Customers
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface CustomerApi {

    @POST("add_customer")
    fun addCustomer(@Body customer: Customers): Call<Void>

    @GET("/get_customers")
    fun getAllCustomers(): Call<CustomerResponse>
}