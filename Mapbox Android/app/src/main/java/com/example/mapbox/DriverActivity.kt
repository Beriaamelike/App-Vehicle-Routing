package com.example.mapbox

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.mapbox.data.api.RouteServiceClient
import model.Route
import model.UniqueRoutesResponse
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class DriverActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var routeAdapter: RouteAdapter
    private var routeCustomers: List<Route> = emptyList()

    // Kullanıcı bilgileri
    private lateinit var userNameTextView: TextView
    private lateinit var userRoleTextView: TextView
    private lateinit var userImageView: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_driver)

        recyclerView = findViewById(R.id.routesRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        userNameTextView = findViewById(R.id.userName)
        userRoleTextView = findViewById(R.id.userRole)
        userImageView = findViewById(R.id.userImage)

        // Kullanıcı bilgilerini SharedPreferences'ten alınır
        val prefs: SharedPreferences = getSharedPreferences("AppPrefs", MODE_PRIVATE)
        val userName = prefs.getString("USER_NAME", "Unknown User") ?: "Unknown User"
        val userRole = prefs.getString("USER_ROLE", "Unknown Role") ?: "Unknown Role"
        val userId = prefs.getInt("USER_ID", -1)

        // Kullanıcı bilgilerini UI'ye set edilir
        userNameTextView.text = userName
        userRoleTextView.text = if (userRole == "ROLE_OFFICER") {
            "Şube Yetkilisi"
        } else {
            "Sürücü"
        }

        fetchRoutesFromApi(userId)
    }

    private fun fetchRoutesFromApi(userId: Int) {
        RouteServiceClient.routeApi.getRoutesByDriver(userId).enqueue(object : Callback<List<Int>> {
            override fun onResponse(call: Call<List<Int>>, response: Response<List<Int>>) {
                if (response.isSuccessful) {
                    val assignedRouteNumbers = response.body() ?: emptyList()

                    // Tüm rotaları alınır ve filtrelenir
                    RouteServiceClient.routeApi.getUniqueRoutes().enqueue(object : Callback<UniqueRoutesResponse> {
                        override fun onResponse(call: Call<UniqueRoutesResponse>, response: Response<UniqueRoutesResponse>) {
                            if (response.isSuccessful) {
                                val allRoutes = response.body()?.route_customers ?: emptyList()
                                routeCustomers = allRoutes.flatten().filter { it.route_number in assignedRouteNumbers }

                                if (routeCustomers.isNotEmpty()) {
                                    setupRecyclerView(assignedRouteNumbers)
                                } else {
                                    Toast.makeText(this@DriverActivity, "Sürücüye ait rota bulunamadı", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(this@DriverActivity, "Rota verisi alınamadı", Toast.LENGTH_SHORT).show()
                            }
                        }

                        override fun onFailure(call: Call<UniqueRoutesResponse>, t: Throwable) {
                            Toast.makeText(this@DriverActivity, "Sunucu hatası: ${t.message}", Toast.LENGTH_SHORT).show()
                        }
                    })

                } else {
                    Toast.makeText(this@DriverActivity, "Sürücü rota API hatası!", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<List<Int>>, t: Throwable) {
                Toast.makeText(this@DriverActivity, "Sunucu hatası: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }


    private fun setupRecyclerView(routeNumbers: List<Int>) {
        val routeTitles = routeNumbers.map { "Rota $it" }

        routeAdapter = RouteAdapter(routeTitles) { selectedRouteTitle ->
            val routeNumber = selectedRouteTitle.removePrefix("Rota ").toIntOrNull()
            val selectedCustomers = routeCustomers.filter { it.route_number == routeNumber }

            val intent = Intent(this, MainActivity::class.java)
            intent.putParcelableArrayListExtra("ROUTE_CUSTOMERS", ArrayList(selectedCustomers))
            intent.putExtra("ROUTE_NUMBER", routeNumber)

            startActivity(intent)
        }

        recyclerView.adapter = routeAdapter
    }

}
