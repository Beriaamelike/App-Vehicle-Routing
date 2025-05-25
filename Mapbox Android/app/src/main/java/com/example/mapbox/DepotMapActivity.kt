package com.example.mapbox


import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.gestures.addOnMapClickListener
import com.mapbox.maps.plugin.gestures.gestures

class DepotMapActivity : AppCompatActivity() {

    private lateinit var mapView: MapView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_depot_map)

        mapView = findViewById(R.id.mapView)

        mapView.getMapboxMap().loadStyleUri(Style.MAPBOX_STREETS) {
            mapView.gestures.addOnMapClickListener { point ->
                val resultIntent = Intent().apply {
                    putExtra("latitude", point.latitude())
                    putExtra("longitude", point.longitude())
                }
                setResult(Activity.RESULT_OK, resultIntent)
                Toast.makeText(this, "Koordinat seçildi: ${point.latitude()}, ${point.longitude()}", Toast.LENGTH_SHORT).show()
                finish()
                true
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onStop()
    }
}
