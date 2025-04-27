package com.example.mapbox

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.mapbox.data.api.RouteServiceClient
import model.OptimizeRoutesResponse
import model.Route
import model.UniqueRoutesResponse
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class OfficerActivity : AppCompatActivity() {

    private lateinit var tvDepotFile: TextView
    private lateinit var tvCustomerFile: TextView
    private lateinit var btnCreateRoute: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var routeListView: ListView
    private lateinit var btnShowMap: Button
    private lateinit var depotFileLauncher: ActivityResultLauncher<Intent>
    private lateinit var customerFileLauncher: ActivityResultLauncher<Intent>
    private var lastFetchedRoutes: List<Route> = emptyList()


    private var depotFileUri: Uri? = null
    private var customerFileUri: Uri? = null

    companion object {
        private const val DEPOT_REQUEST_CODE = 1001
        private const val CUSTOMER_REQUEST_CODE = 1002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_officer)

        tvDepotFile = findViewById(R.id.tvDepotFile)
        tvCustomerFile = findViewById(R.id.tvCustomerFile)
        btnCreateRoute = findViewById(R.id.btnCreateRoute)
        progressBar = findViewById(R.id.progressBar)
        routeListView = findViewById(R.id.routeListView)
        btnShowMap = findViewById(R.id.btnShowMap)

        setupButtonListeners()


        depotFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                depotFileUri = result.data?.data
                tvDepotFile.text = "Depo CSV: ${getFileName(depotFileUri)}"
            }
        }

        customerFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                customerFileUri = result.data?.data
                tvCustomerFile.text = "Müşteri CSV: ${getFileName(customerFileUri)}"
            }
        }

    }

    private fun setupButtonListeners() {
        findViewById<Button>(R.id.btnSelectDepot).setOnClickListener {
            selectCSVFile(DEPOT_REQUEST_CODE)
        }

        findViewById<Button>(R.id.btnSelectCustomer).setOnClickListener {
            selectCSVFile(CUSTOMER_REQUEST_CODE)
        }

        btnCreateRoute.setOnClickListener {
            if (validateFiles()) {
                createRoute()
            }
        }
    }

    private fun selectCSVFile(requestCode: Int) {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "text/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }

        when (requestCode) {
            DEPOT_REQUEST_CODE -> depotFileLauncher.launch(intent)
            CUSTOMER_REQUEST_CODE -> customerFileLauncher.launch(intent)
        }
    }




    private fun getFileName(uri: Uri?): String {
        return uri?.lastPathSegment?.split("/")?.last() ?: "Dosya seçilmedi"
    }

    private fun validateFiles(): Boolean {
        if (depotFileUri == null || customerFileUri == null) {
            Toast.makeText(this, "Lütfen her iki CSV dosyasını da seçin", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun createRoute() {
        progressBar.visibility = View.VISIBLE
        btnCreateRoute.isEnabled = false

        try {
            val depotInputStream = depotFileUri?.let { contentResolver.openInputStream(it) }
            val customerInputStream = customerFileUri?.let { contentResolver.openInputStream(it) }

            if (depotInputStream != null && customerInputStream != null) {
                val depotRequestBody = depotInputStream.readBytes()
                    .toRequestBody("text/csv".toMediaTypeOrNull())
                val customerRequestBody = customerInputStream.readBytes()
                    .toRequestBody("text/csv".toMediaTypeOrNull())

                val depotPart = MultipartBody.Part.createFormData("vehicle_info_csv", "depot.csv", depotRequestBody)
                val customerPart = MultipartBody.Part.createFormData("nodes_csv", "customers.csv", customerRequestBody)

                RouteServiceClient.routeApi.optimizeRoutes(customerPart, depotPart)
                    .enqueue(object : Callback<OptimizeRoutesResponse> {
                        override fun onResponse(
                            call: Call<OptimizeRoutesResponse>,
                            response: Response<OptimizeRoutesResponse>
                        ) {
                            if (response.isSuccessful) {
                                // Optimize başarılı ➔ Şimdi GET istek yap
                                fetchRoutesFromServer()
                            } else {
                                Toast.makeText(this@OfficerActivity, "API hatası: ${response.code()}", Toast.LENGTH_SHORT).show()
                                progressBar.visibility = View.GONE
                                btnCreateRoute.isEnabled = true
                            }
                        }

                        override fun onFailure(call: Call<OptimizeRoutesResponse>, t: Throwable) {
                            Toast.makeText(this@OfficerActivity, "Sunucu hatası: ${t.message}", Toast.LENGTH_SHORT).show()
                            progressBar.visibility = View.GONE
                            btnCreateRoute.isEnabled = true
                        }
                    })
            } else {
                Toast.makeText(this, "Dosyalar okunamadı", Toast.LENGTH_SHORT).show()
                progressBar.visibility = View.GONE
                btnCreateRoute.isEnabled = true
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Hata: ${e.message}", Toast.LENGTH_LONG).show()
            progressBar.visibility = View.GONE
            btnCreateRoute.isEnabled = true
        }
    }
    private fun fetchRoutesFromServer() {
        RouteServiceClient.routeApi.getUniqueRoutes()
            .enqueue(object : Callback<UniqueRoutesResponse> {
                override fun onResponse(
                    call: Call<UniqueRoutesResponse>,
                    response: Response<UniqueRoutesResponse>
                ) {
                    if (response.isSuccessful) {
                        lastFetchedRoutes = response.body()?.route_customers?.firstOrNull() ?: emptyList()

                        val stops = lastFetchedRoutes.map { it.customer_name }

                        displayRoute(stops)
                    } else {
                        Toast.makeText(this@OfficerActivity, "GET API hatası: ${response.code()}", Toast.LENGTH_SHORT).show()
                    }
                    progressBar.visibility = View.GONE
                    btnCreateRoute.isEnabled = true
                }

                override fun onFailure(call: Call<UniqueRoutesResponse>, t: Throwable) {
                    Toast.makeText(this@OfficerActivity, "GET sunucu hatası: ${t.message}", Toast.LENGTH_SHORT).show()
                    progressBar.visibility = View.GONE
                    btnCreateRoute.isEnabled = true
                }
            })
    }


    private fun displayRoute(stops: List<String>) {
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, stops)
        routeListView.adapter = adapter
        btnShowMap.visibility = View.VISIBLE

        btnShowMap.setOnClickListener {
            val intent = Intent(this@OfficerActivity, RenderRouteLineActivity::class.java)
            intent.putParcelableArrayListExtra(
                "ROUTE_CUSTOMERS",
                ArrayList(lastFetchedRoutes) // aşağıda nasıl tutulacağını yazacağım
            )
            startActivity(intent)
        }
    }
}