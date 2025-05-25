package com.example.mapbox

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
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
    private var flatRoutes: List<Route> = emptyList()
    private var routeDurations: List<Double> = listOf()
    private var routeFuelCosts: List<Double> = listOf()




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
                        val infoText = """
                        Depo Konumu: (${latestDepot.x}, ${latestDepot.y})
                        Araç Sayısı: ${latestDepot.fleet_size}
                        Araç Kapasitesi: ${latestDepot.capacity} kg
                        Maksimum Çalışma Süresi: ${latestDepot.max_working_time} dk
                    """.trimIndent()

                        tvDepotInfo.text = infoText
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

    //Rota oluşturulur
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
                        val stats = response.body()
                        routeDurations = stats?.route_durations ?: listOf()
                        routeFuelCosts = stats?.route_fuel_costs ?: listOf()
                        Toast.makeText(this@OfficerRouteActivity, "Rotalar oluşturuldu", Toast.LENGTH_SHORT).show()
                        showStatisticsAlert(response.body())
                        fetchRoutesFromServer()
                    }
                    else {
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
                        flatRoutes = allRoutes.flatten()


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
                val duration = routeDurations.getOrNull(routeNumber) ?: 0.0
                val fuelCost = routeFuelCosts.getOrNull(routeNumber) ?: 0.0

                val intent = Intent(this, OfficerRouteDisplayActivity::class.java)
                intent.putParcelableArrayListExtra("ROUTE_CUSTOMERS", ArrayList(selectedRoute))
                intent.putExtra("ROUTE_NUMBER", routeNumber)
                intent.putExtra("ROUTE_DURATION", duration)
                intent.putExtra("ROUTE_FUEL_COST", fuelCost)
                intent.putParcelableArrayListExtra("ALL_ROUTES", ArrayList(flatRoutes))
                startActivity(intent)
            }

        }
        recyclerView.adapter = routeAdapter
    }

    private fun showStatisticsAlert(stats: OptimizeRoutesResponse?) {
        if (stats == null) return

        val routeDetails = StringBuilder()
        stats.route_customers.forEachIndexed { index, route ->
            routeDetails.append("\nRota ${index + 1}:\n\n" + "⛽ Yakıt Maliyeti: ${stats.route_fuel_costs[index]} TL\n" + "⏱️ Süre: ${stats.route_distances_km[index]} dk\n" + "⏱️ Süre: ${stats.route_durations[index]} dk\n\n")
            route.forEach { customer ->
                if (customer.customer == "Depo") {
                    routeDetails.append("• ${customer.customer}\n")
                } else {
                    routeDetails.append("• ${customer.customer} - Ürün ID: ${customer.product_id} - Ağırlık: ${customer.demand} kg\n")
                }
            }
        }


        val unassignedDetails = StringBuilder()
        if (!stats.unassigned_customers.isNullOrEmpty()) {
            unassignedDetails.append("\n🚫 Dahil Edilemeyen Müşteriler:\n")
            stats.unassigned_customers.forEach {
                unassignedDetails.append("• ${it.customer}\n" + "❗ Sebep: ${it.excluded_reason}\n")
            }
        }


        val message = """
        📍 Toplam Mesafe: ${stats.total_distance_km} km
        ⏱️ Toplam Süre: ${stats.total_duration_minutes} dk
        💰 Toplam Yakıt Maliyeti: ${stats.total_fuel_cost} TL
        🔢 Rota Sayısı: ${stats.route_durations.size}
       
        $routeDetails

        ${if (unassignedDetails.isNotEmpty()) unassignedDetails.toString() else "Tüm müşteriler rotalara dahil edildi."}
    """.trimIndent()

        val textView = TextView(this).apply {
            text = message
            setPadding(32, 16, 32, 16)
            textSize = 14f
            setTextColor(Color.BLACK)
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        }

        val scrollView = ScrollView(this).apply {
            addView(textView)
        }

        AlertDialog.Builder(this)
            .setTitle("Rota Optimizasyon Sonuçları")
            .setView(scrollView)
            .setPositiveButton("Tamam", null)
            .show()
    }

    }
