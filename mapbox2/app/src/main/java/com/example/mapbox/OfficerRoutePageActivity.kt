package com.example.mapbox


import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.mapbox.R
import com.example.mapbox.RouteAdapter
import com.example.mapbox.data.api.RouteServiceClient
import model.Route
import model.UniqueRoutesResponse
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class OfficerRoutePageActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var routeAdapter: RouteAdapter
    private var groupedRoutes: Map<Int, List<Route>> = emptyMap()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_officer_route_page) // XML ismi uygunsa böyle kalsın

        recyclerView = findViewById(R.id.routesRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        fetchRoutesFromApi()
    }

    private fun fetchRoutesFromApi() {
        RouteServiceClient.routeApi.getUniqueRoutes().enqueue(object : Callback<UniqueRoutesResponse> {
            override fun onResponse(call: Call<UniqueRoutesResponse>, response: Response<UniqueRoutesResponse>) {
                if (response.isSuccessful) {
                    val allRoutes = response.body()?.route_customers ?: emptyList()
                    val flatRoutes = allRoutes.flatten()

                    if (flatRoutes.isNotEmpty()) {
                        groupedRoutes = flatRoutes.groupBy { it.route_number }
                        setupRecyclerView()
                    } else {
                        Toast.makeText(this@OfficerRoutePageActivity, "Hiç rota bulunamadı!", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this@OfficerRoutePageActivity, "API hatası!", Toast.LENGTH_SHORT).show()
                }
            }


            override fun onFailure(call: Call<UniqueRoutesResponse>, t: Throwable) {
                Toast.makeText(this@OfficerRoutePageActivity, "Sunucuya ulaşılamıyor: ${t.message}", Toast.LENGTH_SHORT).show()
                Log.e("OfficerRoutePage", "API error: ${t.message}")
            }
        })
    }

    private fun setupRecyclerView() {
        val routeTitles = groupedRoutes.keys.sorted().map { "Rota $it" }
        routeAdapter = RouteAdapter(routeTitles) { routeTitle ->
            val routeNumber = routeTitle.removePrefix("Rota ").toIntOrNull()
            if (routeNumber != null) {
                val selectedRoute = groupedRoutes[routeNumber] ?: emptyList()

                // Intent ile diğer aktiviteye geç
                val intent = Intent(this, OfficerRouteDisplayActivity::class.java)
                intent.putParcelableArrayListExtra("ROUTE_CUSTOMERS", ArrayList(selectedRoute))
                startActivity(intent)
            }
        }
        recyclerView.adapter = routeAdapter
    }

}