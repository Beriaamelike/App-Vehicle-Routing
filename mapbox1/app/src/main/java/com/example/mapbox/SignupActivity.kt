package com.example.mapbox

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.mapbox.data.api.UserServiceClient
import model.RegisterRequest
import model.RegisterResponse
import retrofit2.Call
import retrofit2.Response

class SignupActivity : AppCompatActivity() {

    private lateinit var etUsername: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnSignup: Button
    private lateinit var tvLogin: TextView
    private lateinit var ivBack: ImageView
    private lateinit var ivPasswordToggle: ImageView
    private lateinit var spinnerRole: Spinner // Declare the Spinner as lateinit
    private var isPasswordVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        // Initialize views
        etUsername = findViewById(R.id.etUsername)
        etPassword = findViewById(R.id.etPassword)
        btnSignup = findViewById(R.id.btnSignup)
        tvLogin = findViewById(R.id.tvLogin)
        ivBack = findViewById(R.id.ivBack)
        ivPasswordToggle = findViewById(R.id.ivPasswordToggle)

        // Initialize the Spinner
        spinnerRole = findViewById(R.id.spinnerRole) // Make sure this line is added here

        // Setup role spinner
        val roles = arrayOf("Ben Kimim?", "Sürücü", "Şube Yetkilisi")
        val roleValues = arrayOf("", "ROLE_USER", "ROLE_ADMIN")

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, roles)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerRole.adapter = adapter

        // Set click listeners
        ivBack.setOnClickListener { finish() }

        tvLogin.setOnClickListener {
            // Navigate to LoginActivity
            finish() // Close current activity
        }

        ivPasswordToggle.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            etPassword.transformationMethod = if (isPasswordVisible) {
                null
            } else {
                android.text.method.PasswordTransformationMethod.getInstance()
            }
            ivPasswordToggle.setImageResource(
                if (isPasswordVisible) R.drawable.ic_eye else R.drawable.ic_eye_off
            )
        }

        btnSignup.setOnClickListener {
            val username = etUsername.text.toString()
            val password = etPassword.text.toString()
            val selectedRolePosition = spinnerRole.selectedItemPosition
            val role = if (selectedRolePosition > 0) roleValues[selectedRolePosition] else ""

            // Validate input fields
            if (username.isEmpty() || password.isEmpty() || selectedRolePosition == 0) {
                showAlert("Hata", "Lütfen tüm alanları doldurun.")
                return@setOnClickListener
            }

            // Call the performSignup method
            performSignup(username, password, role)
        }

    }

    private fun performSignup(username: String, password: String, role: String) {
        // Create the RegisterRequest object with the provided data
        val registerRequest = RegisterRequest(username, password, listOf(role))

        // Get the API service instance
        val apiService = UserServiceClient.userApi

        // Make the network call asynchronously
        val call = apiService.register(registerRequest)

        call.enqueue(object : retrofit2.Callback<RegisterResponse> {
            override fun onResponse(
                call: Call<RegisterResponse>,
                response: Response<RegisterResponse>
            ) {
                if (response.isSuccessful) {
                    // Handle successful registration (response.body() contains the response data)
                    val registerResponse = response.body()
                    showAlert("Başarılı", registerResponse?.message ?: "Kayıt işlemi tamamlandı!") {
                        finish() // Close the activity
                    }
                } else {
                    // Handle server error or non-2xx responses
                    showAlert("Hata", "Kayıt sırasında bir hata oluştu.")
                }
            }

            override fun onFailure(call: Call<RegisterResponse>, t: Throwable) {
                // Handle network failure (e.g., no internet connection)
                showAlert("Hata", "Bağlantı hatası: ${t.message}")
            }
        })
    }


    private fun showAlert(title: String, message: String, callback: (() -> Unit)? = null) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
                callback?.invoke()
            }
            .show()
    }

}
