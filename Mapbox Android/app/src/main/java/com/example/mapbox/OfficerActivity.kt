package com.example.mapbox

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.mapbox.databinding.ActivityOfficerBinding

class OfficerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOfficerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOfficerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // SharedPreferences ile kullanıcı bilgileri alınır
        val prefs: SharedPreferences = getSharedPreferences("AppPrefs", MODE_PRIVATE)

        // Kullanıcı adı ve rolü SharedPreferences'ten alınır
        val userName = prefs.getString("USER_NAME", "Unknown User") ?: "Unknown User"
        val userRole = prefs.getString("USER_ROLE", "Unknown Role") ?: "Unknown Role"

        // TextView'lere kullanıcı bilgilerini set edilir
        val userNameTextView: TextView = findViewById(R.id.userName)
        val userRoleTextView: TextView = findViewById(R.id.userRole)

        userNameTextView.text = userName

        // Kullanıcı rolüne göre gösterilecek metin belirlenir
        when (userRole) {
            "ROLE_OFFICER" -> {
                userRoleTextView.text = "Şube Yetkilisi" // Officer için metin
            }
            "ROLE_DRIVER" -> {
                userRoleTextView.text = "Sürücü" // Driver için metin
            }
            else -> {
                userRoleTextView.text = "Bilinmeyen Rol" // Fallback, eğer rol bilinmiyorsa
            }
        }

        setupButtons()
    }

    private fun setupButtons() {
        val btnAddCustomer = findViewById<Button>(R.id.btnAddCustomer)
        val btnAddDepot = findViewById<Button>(R.id.btnAddDepot)
        val btnOptimizeRoutes = findViewById<Button>(R.id.btnOptimizeRoutes)
        val btnViewRoutes = findViewById<Button>(R.id.btnViewRoutes)

        btnAddCustomer.setOnClickListener {
            startActivity(Intent(this, DataEntryActivity::class.java))
        }

        btnAddDepot.setOnClickListener {
            startActivity(Intent(this, DepotEntryActivity::class.java))
        }

        btnViewRoutes.setOnClickListener {
            startActivity(Intent(this, OfficerRoutePageActivity::class.java))
        }

        btnOptimizeRoutes.setOnClickListener {
            startActivity(Intent(this, OfficerRouteActivity::class.java))
        }
    }
}
