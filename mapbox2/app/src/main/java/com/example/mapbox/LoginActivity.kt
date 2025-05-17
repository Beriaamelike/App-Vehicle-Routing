package com.example.mapbox

import android.content.Intent
import android.os.Bundle
import android.util.Base64
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.mapbox.data.api.UserApi
import com.example.mapbox.data.api.UserServiceClient
import model.LoginRequest
import model.LoginResponse
import com.example.mapbox.databinding.ActivityLoginBinding
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import androidx.core.content.edit
import com.example.mapbox.data.api.RouteServiceClient.routeApi
import model.UserDetailsResponse
import com.auth0.jwt.JWT
import com.auth0.jwt.interfaces.DecodedJWT

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var authService: UserApi

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        authService = UserServiceClient.userApi

        setupViews()
    }

    private fun setupViews() {
        binding.loginButton.setOnClickListener {
            val email = binding.emailInput.text.toString().trim()
            val password = binding.passwordInput.text.toString().trim()

            if (validateInputs(email, password)) {
                loginUser(email, password)
            }
        }
    }

    private fun validateInputs(email: String, password: String): Boolean {
        validateEmail(email)
        if (email.isEmpty()) {
            binding.emailInput.error = "Email is required"
            return false
        }

        if (password.isEmpty()) {
            binding.passwordInput.error = "Password is required"
            return false
        }


        return true
    }

    private fun validateEmail(email: String): Boolean {
        val emailPattern = "[a-zA-Z0-9._-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,4}"
        return email.matches(emailPattern.toRegex())
    }


    private fun loginUser(email: String, password: String) {
        // Önceki verileri temizle
        getSharedPreferences("AppPrefs", MODE_PRIVATE).edit() { clear() }

        val loginRequest = LoginRequest(username = email, password = password)

        authService.login(loginRequest).enqueue(object : Callback<LoginResponse> {
            override fun onResponse(call: Call<LoginResponse>, response: Response<LoginResponse>) {
                if (response.isSuccessful) {
                    val jwt = response.body()?.jwt
                    if (!jwt.isNullOrEmpty()) {
                        fetchUserDetails(jwt, email)
                    } else {
                        showError("JWT boş geldi")
                    }
                } else {
                    handleHttpError(response.code())
                }
            }

            override fun onFailure(call: Call<LoginResponse>, t: Throwable) {
                showError("Network error: ${t.message}")
            }
        })

    }


    private fun fetchUserDetails(token: String, email: String) {
        routeApi.getUserDetails(email).enqueue(object : Callback<UserDetailsResponse> {
            override fun onResponse(call: Call<UserDetailsResponse>, response: Response<UserDetailsResponse>) {
                if (response.isSuccessful) {
                    val userDetails = response.body()
                    if (userDetails != null) {
                        saveUserDetails(token, userDetails)
                        navigateToHome()
                    } else {
                        showError("User details are null")
                    }
                } else {
                    showError("User details fetch error: ${response.code()} - ${response.message()}")
                }
            }

            override fun onFailure(call: Call<UserDetailsResponse>, t: Throwable) {
                showError("Network error: ${t.message}")
            }
        })
    }



    private fun saveUserDetails(token: String, userDetails: UserDetailsResponse) {
        val role = decodeJwt(token)
        getSharedPreferences("AppPrefs", MODE_PRIVATE).edit {
            putString("AUTH_TOKEN", token)  // Token'ı kaydediyoruz
            putString("USER_ROLE", role)
            putString("USER_NAME", userDetails.name)
            putString("USER_EMAIL", userDetails.username) // Email bilgisi
            putInt("USER_ID", userDetails.user_id)
        }
    }




    fun decodeJwt(jwt: String): String {
        val decodedJWT: DecodedJWT = JWT.decode(jwt)
        return decodedJWT.getClaim("role").asString()  // 'role' claim'ini alıyoruz
    }

    private fun saveToken(token: String) {
        // JWT'den rol bilgisini alıyoruz
        val role = decodeJwt(token)

        getSharedPreferences("AppPrefs", MODE_PRIVATE).edit {
            putString("AUTH_TOKEN", token)  // Token'ı kaydediyoruz
            putString("USER_ROLE", role)    // Role bilgisini kaydediyoruz
        }
    }

    private fun handleHttpError(errorCode: Int) {
        val errorMessage = when (errorCode) {
            204 -> "No content received (empty response)"
            400 -> "Bad request"
            401 -> "Unauthorized"
            500 -> "Server error"
            else -> "HTTP error: $errorCode"
        }
        showError(errorMessage)
    }

    private fun navigateToHome() {
        // Kullanıcı rolüne göre yönlendirme yapılabilir.
        // Örneğin:
        val prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)
        val role = prefs.getString("USER_ROLE", "")

        when (role) {
            "ROLE_DRIVER" -> {
                val intent = Intent(this, DriverActivity::class.java)
                startActivity(intent)
            }
            "ROLE_OFFICER" -> {
                val intent = Intent(this, OfficerActivity::class.java)
                startActivity(intent)
            }
            else -> {
                Toast.makeText(this, "Rol bilgisi okunamadı veya bilinmiyor", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
