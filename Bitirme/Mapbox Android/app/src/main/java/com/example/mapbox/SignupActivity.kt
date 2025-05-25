package com.example.mapbox

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.mapbox.data.api.UserServiceClient
import model.RegisterRequest
import model.RegisterResponse
import retrofit2.Call
import retrofit2.Response

class SignupActivity : AppCompatActivity() {

    private lateinit var etUsername: EditText
    private lateinit var etname: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnSignup: Button
    private lateinit var tvLogin: TextView
    private lateinit var roleRadioGroup: RadioGroup
    private lateinit var radioDriver: RadioButton
    private lateinit var radioAdmin: RadioButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        etUsername = findViewById(R.id.etUsername)
        etname = findViewById(R.id.etname)
        etPassword = findViewById(R.id.etPassword)
        btnSignup = findViewById(R.id.btnSignup)
        tvLogin = findViewById(R.id.tvLogin)
        roleRadioGroup = findViewById(R.id.roleRadioGroup)
        radioDriver = findViewById(R.id.radioDriver)
        radioAdmin = findViewById(R.id.radioAdmin)




        tvLogin.setOnClickListener {
            finish()
        }



        btnSignup.setOnClickListener {
            val name = etname.text.toString()
            val username = etUsername.text.toString()
            val password = etPassword.text.toString()
            val role = when (roleRadioGroup.checkedRadioButtonId) {
                R.id.radioDriver -> "ROLE_DRIVER"
                R.id.radioAdmin -> "ROLE_OFFICER"
                else -> ""
            }

            if (name.isEmpty() || username.isEmpty() || password.isEmpty() || role.isEmpty()) {
                showAlert("Hata", "Lütfen tüm alanları doldurun.")
                return@setOnClickListener
            }


            performSignup(name, username, password, role)
        }

    }

    private fun performSignup(name:String, username: String, password: String, role: String) {
        val registerRequest = RegisterRequest(name, username, password, listOf(role))

        val apiService = UserServiceClient.userApi

        val call = apiService.register(registerRequest)

        call.enqueue(object : retrofit2.Callback<RegisterResponse> {
            override fun onResponse(
                call: Call<RegisterResponse>,
                response: Response<RegisterResponse>
            ) {
                if (response.isSuccessful) {
                    val registerResponse = response.body()
                    showAlert("Başarılı", registerResponse?.message ?: "Kayıt işlemi tamamlandı!") {
                        finish()
                    }
                } else {
                    val errorMessage = try {
                        response.errorBody()?.string() ?: "Bilinmeyen hata."
                    } catch (e: Exception) {
                        "Hata mesajı alınamadı."
                    }
                    showAlert("Hata", "Kayıt sırasında bir hata oluştu:\n$errorMessage")
                }
            }

            override fun onFailure(call: Call<RegisterResponse>, t: Throwable) {
                showAlert("Bağlantı Hatası", "Sunucuya erişilemedi: ${t.localizedMessage}")
            }
        })

    }


    private fun showAlert(title: String, message: String, callback: (() -> Unit)? = null) {
        Toast.makeText(this, "Kayıt Başarılı", Toast.LENGTH_LONG).show()
    }

}
