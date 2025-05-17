package com.example.mapbox.data.api

import model.LoginRequest
import model.LoginResponse
import model.RegisterRequest
import model.RegisterResponse
import model.UserDetailsResponse
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface UserApi {
    // POST request for the register endpoint
    @POST("register") // Make sure this matches your actual API endpoint
    fun register(@Body registerRequest: RegisterRequest): Call<RegisterResponse>

    @POST("login")
    fun login(@Body request: LoginRequest): Call<LoginResponse>




}
