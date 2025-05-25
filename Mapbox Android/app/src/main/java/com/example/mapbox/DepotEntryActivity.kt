package com.example.mapbox

import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.mapbox.data.api.DepotServiceClient
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.AutocompleteSupportFragment
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener
import com.mapbox.geojson.Point
import com.mapbox.maps.Style
import com.mapbox.maps.MapView
import com.mapbox.maps.plugin.gestures.gestures
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.maps.extension.style.style
import model.DepotRequest
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import com.google.android.gms.common.api.Status
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager


class DepotEntryActivity : AppCompatActivity() {

    private var selectedLat: Double? = null
    private var selectedLon: Double? = null
    private lateinit var edtDepotCapacity: EditText
    private lateinit var edtFleetSize: EditText
    private lateinit var btnSaveDepot: Button
    private lateinit var mapView: MapView
    private lateinit var pointAnnotationManager: PointAnnotationManager
    private lateinit var edtMaxWorkingTime: EditText
    private lateinit var edtFuelConsumption: EditText



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_depot_entry)

        Log.d("API_KEY", getString(R.string.google_maps_key))


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
                        com.mapbox.maps.CameraOptions.Builder()
                            .center(Point.fromLngLat(lon, lat)).zoom(14.0).build()
                    )
                }
            }

            override fun onError(status: Status) {
                Toast.makeText(this@DepotEntryActivity, "Hata: ${status.statusMessage}", Toast.LENGTH_SHORT).show()
            }
        })


        edtDepotCapacity = findViewById(R.id.edtDepotCapacity)
        edtFleetSize = findViewById(R.id.edtFleetSize)
        edtMaxWorkingTime = findViewById(R.id.edtMaxWorkingTime)
        btnSaveDepot = findViewById(R.id.btnSaveDepot)
        mapView = findViewById(R.id.mapView)
        edtFuelConsumption = findViewById(R.id.edtFuelConsumption)

        mapView.mapboxMap.loadStyle(style(Style.MAPBOX_STREETS) {}) {
            val annotationPlugin = mapView.annotations
            pointAnnotationManager = annotationPlugin.createPointAnnotationManager()

            mapView.gestures.addOnMapClickListener { point ->
                updateLatLon(point.latitude(), point.longitude())
                showMarker(point.latitude(), point.longitude(), pointAnnotationManager)
                true
            }

        }

        btnSaveDepot.setOnClickListener {
            val x = selectedLat
            val y = selectedLon
            val capacity = edtDepotCapacity.text.toString().toIntOrNull()
            val fleetSize = edtFleetSize.text.toString().toIntOrNull()
            val maxWorkingTime = edtMaxWorkingTime.text.toString().toIntOrNull()
            val fuelConsumption = edtFuelConsumption.text.toString().toDoubleOrNull()



            if (x == null || y == null || capacity == null || fleetSize == null || maxWorkingTime == null || fuelConsumption == null) {
                Toast.makeText(this, "Tüm alanları doldurun ve haritadan konum seçin", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val depot = DepotRequest(
                x = x,
                y = y,
                capacity = capacity,
                fleet_size = fleetSize,
                max_working_time = maxWorkingTime,
                fuel_consumption = fuelConsumption
            )

            DepotServiceClient.depotApi.addDepot(depot).enqueue(object : Callback<model.DepotResponse> {
                override fun onResponse(call: Call<model.DepotResponse>, response: Response<model.DepotResponse>) {
                    if (response.isSuccessful) {
                        Toast.makeText(this@DepotEntryActivity, "Depo kaydedildi", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@DepotEntryActivity, "Sunucu hatası: ${response.code()}", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<model.DepotResponse>, t: Throwable) {
                    Toast.makeText(this@DepotEntryActivity, "Bağlantı hatası: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            })
        }
    }

    private fun updateLatLon(lat: Double, lon: Double) {
        selectedLat = lat
        selectedLon = lon
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


    override fun onDestroy() {
        super.onDestroy()

    }


}