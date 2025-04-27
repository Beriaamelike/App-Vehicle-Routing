package com.example.mapbox

import android.content.Intent
import android.os.Bundle
import android.util.Log
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_driver)

        recyclerView = findViewById(R.id.routesRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        fetchRoutesFromApi()
    }

    private fun fetchRoutesFromApi() {
        RouteServiceClient.routeApi.getUniqueRoutes().enqueue(object : Callback<UniqueRoutesResponse> {
            override fun onResponse(call: Call<UniqueRoutesResponse>, response: Response<UniqueRoutesResponse>) {
                if (response.isSuccessful) {
                    val allRoutes = response.body()?.route_customers ?: emptyList()
                    routeCustomers = allRoutes.flatten().filter { it.route_number == 1 }

                    if (routeCustomers.isNotEmpty()) {
                        setupRecyclerView()
                    } else {
                        Toast.makeText(this@DriverActivity, "Route 1 bulunamadı!", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this@DriverActivity, "API hatası!", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<UniqueRoutesResponse>, t: Throwable) {
                Toast.makeText(this@DriverActivity, "Sunucuya ulaşılamıyor: ${t.message}", Toast.LENGTH_SHORT).show()
                Log.e("DriverActivity", "API error: ${t.message}")
            }
        })
    }

    private fun setupRecyclerView() {
        routeAdapter = RouteAdapter(listOf("Rota 1")) {
            val intent = Intent(this, MainActivity::class.java)
            intent.putParcelableArrayListExtra("ROUTE_CUSTOMERS", ArrayList(routeCustomers))
            startActivity(intent)
        }
        recyclerView.adapter = routeAdapter
    }
}
