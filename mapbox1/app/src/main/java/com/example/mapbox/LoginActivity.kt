package com.example.mapbox

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.mapbox.data.api.UserApi
import com.example.mapbox.data.api.UserServiceClient
import model.LoginRequest
import model.LoginResponse
import com.example.mapbox.databinding.ActivityLoginBinding
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

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
        // Back button
        binding.backButton.setOnClickListener {
            finish()
        }

        // Password visibility toggle
        var isPasswordVisible = false
        binding.passwordToggle.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            binding.passwordInput.inputType = if (isPasswordVisible) {
                android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            } else {
                android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            binding.passwordInput.setSelection(binding.passwordInput.text.length)
        }

        // Forgot password
        binding.forgotPassword.setOnClickListener {
            Toast.makeText(this, "Forgot password clicked", Toast.LENGTH_SHORT).show()
        }

        // Login button
        binding.loginButton.setOnClickListener {
            val email = binding.emailInput.text.toString().trim()
            val password = binding.passwordInput.text.toString().trim()

            if (validateInputs(email, password)) {
                loginUser(email, password)
            }
        }

        // Sign up navigation
        binding.signUpText.setOnClickListener {
            val intent = Intent(this, SignupActivity::class.java)
            startActivity(intent)

        }
    }

    private fun validateInputs(email: String, password: String): Boolean {
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

    private fun loginUser(email: String, password: String) {
        val loginRequest = LoginRequest(username = email, password = password)

        authService.login(loginRequest).enqueue(object : retrofit2.Callback<Void> {
            override fun onResponse(call: retrofit2.Call<Void>, response: retrofit2.Response<Void>) {
                if (response.isSuccessful) {
                    val authHeader = response.headers()["Authorization"]
                    if (!authHeader.isNullOrEmpty() && authHeader.startsWith("Bearer ")) {
                        val token = authHeader.substring(7).trim()
                        if (token.isNotEmpty()) {
                            saveToken(token)
                            navigateToHome()
                        } else {
                            showError("Token is empty")
                        }
                    } else {
                        showError("Authorization header not found")
                    }
                } else {
                    handleHttpError(response.code())
                }
            }

            override fun onFailure(call: retrofit2.Call<Void>, t: Throwable) {
                showError("Network error: ${t.message ?: "Unknown error"}")
            }
        })

    }


    private fun saveToken(token: String) {
        getSharedPreferences("AppPrefs", MODE_PRIVATE).edit().apply {
            putString("AUTH_TOKEN", token)
            apply()
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
        // Navigate to home activity
        Toast.makeText(this, "Login successful!", Toast.LENGTH_SHORT).show()
        val intent = Intent(this, OfficerActivity::class.java)
        startActivity(intent)
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}