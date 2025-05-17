package com.example.mapbox

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.mapbox.data.api.CustomerServiceClient
import com.example.mapbox.data.api.DepotServiceClient
import com.example.mapbox.data.api.RouteServiceClient
import model.CustomerResponse
import model.DepotListResponse
import model.OptimizeRoutesResponse
import model.Route
import model.UniqueRoutesResponse
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class OfficerRouteActivity : AppCompatActivity() {

    private lateinit var tvDepotInfo: TextView
    private lateinit var tvCustomerCount: TextView
    private lateinit var btnCreateRoute: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var recyclerView: RecyclerView
    private lateinit var customerRecyclerView: RecyclerView
    private lateinit var routeAdapter: RouteAdapter
    private lateinit var customerAdapter: CustomerListAdapter
    private var groupedRoutes: Map<Int, List<Route>> = emptyMap()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_officer_route)

        tvDepotInfo = findViewById(R.id.tvDepotInfo)
        tvCustomerCount = findViewById(R.id.tvCustomerCount)
        btnCreateRoute = findViewById(R.id.btnCreateRoute)
        progressBar = findViewById(R.id.progressBar)
        recyclerView = findViewById(R.id.routesRecyclerView)
        customerRecyclerView = findViewById(R.id.customerRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        customerRecyclerView.layoutManager = LinearLayoutManager(this)

        setupButtonListeners()
        loadDepotAndCustomerInfo()
    }

    private fun setupButtonListeners() {
        btnCreateRoute.setOnClickListener {
            createRoute()
        }
    }

    private fun loadDepotAndCustomerInfo() {

        // Depo bilgisi
        DepotServiceClient.depotApi.getDepot().enqueue(object : Callback<DepotListResponse> {
            override fun onResponse(call: Call<DepotListResponse>, response: Response<DepotListResponse>) {
                if (response.isSuccessful) {
                    val depotList = response.body()?.depots ?: emptyList()
                    if (depotList.isNotEmpty()) {
                        val latestDepot = depotList.last()
                        tvDepotInfo.text = "Depo: (${latestDepot.x}, ${latestDepot.y})"
                    } else {
                        tvDepotInfo.text = "Depo bilgisi bulunamadı"
                    }
                } else {
                    tvDepotInfo.text = "Depo API hatası: ${response.code()}"
                }
            }

            override fun onFailure(call: Call<DepotListResponse>, t: Throwable) {
                tvDepotInfo.text = "Depo yüklenemedi: ${t.message}"
            }
        })

        // Müşteri bilgisi
        CustomerServiceClient.customerApi.getAllCustomers().enqueue(object : Callback<CustomerResponse> {
            override fun onResponse(call: Call<CustomerResponse>, response: Response<CustomerResponse>) {
                if (response.isSuccessful) {
                    val customers = response.body()?.customers ?: emptyList()
                    tvCustomerCount.text = "Toplam Müşteri: ${customers.size}"
                    customerAdapter = CustomerListAdapter(customers)
                    customerRecyclerView.adapter = customerAdapter
                }
            }



            override fun onFailure(call: Call<CustomerResponse>, t: Throwable) {
                Toast.makeText(this@OfficerRouteActivity, "Müşteriler yüklenemedi: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })


    }

    private fun createRoute() {
        progressBar.visibility = View.VISIBLE
        btnCreateRoute.isEnabled = false

        RouteServiceClient.routeApi.optimizeRoutesFromDb()
            .enqueue(object : Callback<OptimizeRoutesResponse> {
                override fun onResponse(
                    call: Call<OptimizeRoutesResponse>,
                    response: Response<OptimizeRoutesResponse>
                ) {
                    if (response.isSuccessful) {
                        Toast.makeText(this@OfficerRouteActivity, "Rotalar oluşturuldu", Toast.LENGTH_SHORT).show()
                        fetchRoutesFromServer()
                    } else {
                        Toast.makeText(this@OfficerRouteActivity, "API hatası: ${response.code()}", Toast.LENGTH_SHORT).show()
                        progressBar.visibility = View.GONE
                        btnCreateRoute.isEnabled = true
                    }
                }

                override fun onFailure(call: Call<OptimizeRoutesResponse>, t: Throwable) {
                    Toast.makeText(this@OfficerRouteActivity, "Sunucu hatası: ${t.message}", Toast.LENGTH_SHORT).show()
                    progressBar.visibility = View.GONE
                    btnCreateRoute.isEnabled = true
                }
            })
    }

    private fun showError() {
        Toast.makeText(this, "Rotalar oluşturuldu", Toast.LENGTH_LONG).show()
    }

    private fun fetchRoutesFromServer() {
        RouteServiceClient.routeApi.getOptimizeRoutes()
            .enqueue(object : Callback<UniqueRoutesResponse> {
                override fun onResponse(
                    call: Call<UniqueRoutesResponse>,
                    response: Response<UniqueRoutesResponse>
                ) {
                    if (response.isSuccessful) {
                        val allRoutes = response.body()?.route_customers ?: emptyList()
                        val flatRoutes = allRoutes.flatten()

                        if (flatRoutes.isNotEmpty()) {
                            groupedRoutes = flatRoutes.groupBy { it.route_number }
                            setupRecyclerView()
                        } else {
                            Toast.makeText(this@OfficerRouteActivity, "Hiç rota bulunamadı!", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this@OfficerRouteActivity, "GET API hatası: ${response.code()}", Toast.LENGTH_SHORT).show()
                    }

                    progressBar.visibility = View.GONE
                    btnCreateRoute.isEnabled = true
                }

                override fun onFailure(call: Call<UniqueRoutesResponse>, t: Throwable) {
                    Toast.makeText(this@OfficerRouteActivity, "GET sunucu hatası: ${t.message}", Toast.LENGTH_SHORT).show()
                    progressBar.visibility = View.GONE
                    btnCreateRoute.isEnabled = true
                }
            })
    }

    private fun setupRecyclerView() {
        val routeTitles = groupedRoutes.keys.sorted().map { "Rota $it" }
        routeAdapter = RouteAdapter(routeTitles) { routeTitle ->
            val routeNumber = routeTitle.removePrefix("Rota ").toIntOrNull()
            if (routeNumber != null) {
                val selectedRoute = groupedRoutes[routeNumber] ?: emptyList()
                val intent = Intent(this, OfficerRouteDisplayActivity::class.java)
                intent.putParcelableArrayListExtra("ROUTE_CUSTOMERS", ArrayList(selectedRoute))
                intent.putExtra("ROUTE_NUMBER", routeNumber)  // 👈 direkt route_number da gönderiliyor
                startActivity(intent)

            }
        }
        recyclerView.adapter = routeAdapter
    }
}
