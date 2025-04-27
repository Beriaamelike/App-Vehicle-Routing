package com.example.mapbox

import android.content.Context
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import androidx.core.content.edit

class AuthInterceptor(context: Context) : Interceptor {
    private val sharedPref = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // Eğer token varsa header'a ekle
        val token = sharedPref.getString("AUTH_TOKEN", null)
        val requestBuilder = originalRequest.newBuilder()

        token?.let {
            requestBuilder.addHeader("Authorization", "Bearer $it")
        }

        // Header'lara diğer ortak bilgileri ekleyebilirsiniz
        requestBuilder.addHeader("Accept", "application/json")

        val response = chain.proceed(requestBuilder.build())

        // Gelen response'dan token'ı yakalayıp kaydetmek için:
        if (response.headers["Authorization"] != null) {
            val newToken = response.headers["Authorization"]!!.removePrefix("Bearer ")
            sharedPref.edit() { putString("AUTH_TOKEN", newToken) }
        }

        return response
    }
}