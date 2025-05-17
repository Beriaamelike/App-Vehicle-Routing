package com.example.mapbox

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.mapbox.data.api.CustomerServiceClient
import com.google.android.gms.common.api.Status
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.AutocompleteSupportFragment
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener
import com.mapbox.geojson.Point
import com.mapbox.maps.MapView
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.extension.style.style
import com.mapbox.maps.Style
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.maps.plugin.gestures.gestures
import model.Customers
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.*

class DataEntryActivity : AppCompatActivity() {

    private lateinit var edtCustomerX: EditText
    private lateinit var edtCustomerY: EditText
    private lateinit var edtCustomerDemand: EditText
    private lateinit var edtCustomerReady: EditText
    private lateinit var edtCustomerDue: EditText
    private lateinit var edtCustomerService: EditText
    private lateinit var btnAddCustomer: Button
    private lateinit var btnUploadCSV: Button
    private lateinit var btnSubmit: Button
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: CustomerListAdapter
    private lateinit var mapView: MapView
    private lateinit var pointAnnotationManager: PointAnnotationManager
    private val customers = mutableListOf<Customers>()
    private var selectedLat: Double? = null
    private var selectedLon: Double? = null


    private val csvLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            uri?.let { loadCustomersFromCSV(it) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_data_entry)

        // Google Places initialize
        Places.initialize(applicationContext, getString(R.string.google_maps_key))

        val autocompleteFragment = supportFragmentManager
            .findFragmentById(R.id.autocomplete_fragment) as AutocompleteSupportFragment

        autocompleteFragment.setPlaceFields(listOf(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG))
        autocompleteFragment.setOnPlaceSelectedListener(object : PlaceSelectionListener {
            override fun onPlaceSelected(place: Place) {
                val latLng = place.latLng
                if (latLng != null) {
                    val lat = latLng.latitude
                    val lon = latLng.longitude
                    updateLatLon(lat, lon)
                    showMarker(lat, lon, pointAnnotationManager)
                    mapView.mapboxMap.setCamera(
                        CameraOptions.Builder().center(Point.fromLngLat(lon, lat)).zoom(14.0).build()
                    )
                }
            }

            override fun onError(status: Status) {
                Toast.makeText(this@DataEntryActivity, "Hata: ${status.statusMessage}", Toast.LENGTH_SHORT).show()
            }
        })

        edtCustomerX = findViewById(R.id.edtCustomerX)
        edtCustomerY = findViewById(R.id.edtCustomerY)
        edtCustomerDemand = findViewById(R.id.edtCustomerDemand)
        edtCustomerReady = findViewById(R.id.edtCustomerReady)
        edtCustomerDue = findViewById(R.id.edtCustomerDue)
        edtCustomerService = findViewById(R.id.edtCustomerService)

        btnAddCustomer = findViewById(R.id.btnAddCustomer)
        btnUploadCSV = findViewById(R.id.btnUploadCSV)
        btnSubmit = findViewById(R.id.btnSubmit)

        recyclerView = findViewById(R.id.recyclerViewCustomers)
        adapter = CustomerListAdapter(customers)
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(this)

        mapView = findViewById(R.id.mapView)

        mapView.mapboxMap.loadStyle(style(Style.MAPBOX_STREETS) {}) {
            val annotationPlugin = mapView.annotations
            pointAnnotationManager = annotationPlugin.createPointAnnotationManager()


            // Haritaya tıklayınca koordinat al
            mapView.gestures.addOnMapClickListener { point ->
                updateLatLon(point.latitude(), point.longitude())
                showMarker(point.latitude(), point.longitude(), pointAnnotationManager)
                true
            }

        }


        btnAddCustomer.setOnClickListener { addCustomerManually() }
        btnUploadCSV.setOnClickListener { chooseCSV() }
        btnSubmit.setOnClickListener { submitData() }
    }

    private fun updateLatLon(lat: Double, lon: Double) {
        selectedLat = lat
        selectedLon = lon
        edtCustomerX.setText(lat.toString())
        edtCustomerY.setText(lon.toString())
    }


    private fun showMarker(lat: Double, lon: Double, manager: PointAnnotationManager) {
        manager.deleteAll()
        val point = Point.fromLngLat(lon, lat)
        val bitmap = BitmapFactory.decodeResource(resources, R.drawable.green_marker)
        val marker = PointAnnotationOptions()
            .withPoint(point)
            .withIconImage(bitmap)
            .withIconSize(0.5)
        manager.create(marker)
    }

    private fun chooseCSV() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "text/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        csvLauncher.launch(intent)
    }

    private fun loadCustomersFromCSV(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val reader = BufferedReader(InputStreamReader(inputStream))
            val lines = reader.readLines()

            if (lines.isEmpty()) {
                Toast.makeText(this, "CSV boş", Toast.LENGTH_SHORT).show()
                return
            }

            for ((index, line) in lines.drop(1).withIndex()) {
                val tokens = line.trim().split(",")
                if (tokens.size >= 7) {
                    try {
                        val customer = Customers(
                            id = tokens[0].toInt(),
                            xc = tokens[1].toDouble(),
                            yc = tokens[2].toDouble(),
                            demand = tokens[3].toInt(),
                            ready_time = tokens[4].toInt(),
                            due_time = tokens[5].toInt(),
                            service_time = tokens[6].toInt()
                        )
                        customers.add(customer)
                    } catch (e: Exception) {
                        Toast.makeText(this, "Satır ${index + 2} atlandı: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "Satır ${index + 2} eksik veri içeriyor", Toast.LENGTH_SHORT).show()
                }
            }

            // CSV sonrası
            adapter = CustomerListAdapter(customers.toList())
            recyclerView.adapter = adapter

        } catch (e: Exception) {
            Toast.makeText(this, "CSV yüklenemedi: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }


    private fun addCustomerManually() {
        val x = selectedLat ?: edtCustomerX.text.toString().toDoubleOrNull()
        val y = selectedLon ?: edtCustomerY.text.toString().toDoubleOrNull()

        if (x == null || y == null) {
            Toast.makeText(this, "Lütfen geçerli koordinat girin", Toast.LENGTH_SHORT).show()
            return
        }

        val customer = Customers(
            xc = x,
            yc = y,
            demand = edtCustomerDemand.text.toString().toIntOrNull() ?: 0,
            ready_time = edtCustomerReady.text.toString().toIntOrNull() ?: 0,
            due_time = edtCustomerDue.text.toString().toIntOrNull() ?: 24,
            service_time = edtCustomerService.text.toString().toIntOrNull() ?: 1
        )
        customers.add(customer)
        adapter = CustomerListAdapter(customers.toList())
        recyclerView.adapter = adapter


        // Koordinatları sıfırla
        selectedLat = null
        selectedLon = null
    }



    private fun submitData() {
        if (customers.isEmpty()) {
            Toast.makeText(this, "En az bir müşteri girilmeli", Toast.LENGTH_SHORT).show()
            return
        }

        val api = CustomerServiceClient.customerApi
        customers.forEach { customer ->
            api.addCustomer(customer).enqueue(object : Callback<Void> {
                override fun onResponse(call: Call<Void>, response: Response<Void>) {
                    if (response.isSuccessful) {

                    } else {
                        Toast.makeText(this@DataEntryActivity, "API başarısız: ${response.code()}", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<Void>, t: Throwable) {
                    Toast.makeText(this@DataEntryActivity, "Hata: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            })
        }
        Toast.makeText(this@DataEntryActivity, "Müşteriler eklendi", Toast.LENGTH_SHORT).show()
    }

}
