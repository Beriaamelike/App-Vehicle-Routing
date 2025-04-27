package com.example.mapbox.data.api

import model.LoginRequest
import model.LoginResponse
import model.RegisterRequest
import model.RegisterResponse
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface UserApi {
    // POST request for the register endpoint
    @POST("register") // Make sure this matches your actual API endpoint
    fun register(@Body registerRequest: RegisterRequest): Call<RegisterResponse>

    @POST("get-token")
    fun login(@Body loginRequest: LoginRequest): Call<Void>
}
