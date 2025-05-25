package com.example.mapbox

import com.mapbox.maps.plugin.annotation.generated.*
import com.mapbox.maps.plugin.annotation.annotations
import android.graphics.BitmapFactory
import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.compose.runtime.mutableStateListOf
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.mapbox.data.api.RouteServiceClient.routeApi
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.common.location.Location
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.MapView
import com.mapbox.maps.plugin.LocationPuck2D
import com.mapbox.maps.plugin.animation.camera
import com.mapbox.maps.plugin.locationcomponent.createDefault2DPuck
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.navigation.base.ExperimentalPreviewMapboxNavigationAPI
import com.mapbox.navigation.base.extensions.applyDefaultNavigationOptions
import com.mapbox.navigation.base.options.NavigationOptions
import com.mapbox.navigation.base.route.NavigationRoute
import com.mapbox.navigation.base.route.NavigationRouterCallback
import com.mapbox.navigation.base.route.RouterFailure
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.directions.session.RoutesObserver
import com.mapbox.navigation.core.lifecycle.MapboxNavigationApp
import com.mapbox.navigation.core.lifecycle.MapboxNavigationObserver
import com.mapbox.navigation.core.lifecycle.requireMapboxNavigation
import com.mapbox.navigation.core.replay.route.ReplayProgressObserver
import com.mapbox.navigation.core.replay.route.ReplayRouteMapper
import com.mapbox.navigation.core.trip.session.LocationMatcherResult
import com.mapbox.navigation.core.trip.session.LocationObserver
import com.mapbox.navigation.ui.maps.camera.NavigationCamera
import com.mapbox.navigation.ui.maps.camera.data.MapboxNavigationViewportDataSource
import com.mapbox.navigation.ui.maps.location.NavigationLocationProvider
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineApi
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineView
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineApiOptions
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineViewOptions
import com.mapbox.navigation.ui.maps.route.line.model.RouteLineColorResources
import model.AssignDriverRequest
import model.Route
import model.RouteInfoResponse
import model.User
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class OfficerRouteDisplayActivity : ComponentActivity() {
    private lateinit var mapView: MapView
    private lateinit var viewportDataSource: MapboxNavigationViewportDataSource
    private lateinit var navigationCamera: NavigationCamera
    private lateinit var routeLineApi: MapboxRouteLineApi
    private lateinit var routeLineView: MapboxRouteLineView
    private lateinit var replayProgressObserver: ReplayProgressObserver
    private val navigationLocationProvider = NavigationLocationProvider()
    lateinit var pointAnnotationManager: PointAnnotationManager
    private var currentRouteNumber: Int = -1
    private val routeColors = listOf(Color.BLUE, Color.RED, Color.GREEN, Color.MAGENTA, Color.CYAN)


    private val locationPermissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                permissions ->
            when {
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true -> {
                    initializeMapComponents()
                }
                else -> {
                    Toast.makeText(
                        this,
                        "Location permissions denied. Please enable permissions in settings.",
                        Toast.LENGTH_LONG
                    )
                        .show()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        currentRouteNumber = intent.getIntExtra("ROUTE_NUMBER", -1)


        if (
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            initializeMapComponents()
        } else {
            locationPermissionRequest.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    private fun showDriverSelectionDialog(routeNumber: Int) {
        val call = routeApi.getAvailableDrivers()
        call.enqueue(object : Callback<List<User>> {
            override fun onResponse(call: Call<List<User>>, response: Response<List<User>>) {
                if (response.isSuccessful) {
                    val drivers = response.body() ?: emptyList()
                    val driverNames = drivers.map { "${it.name}" }.toTypedArray()

                    var selectedIndex = 0
                    AlertDialog.Builder(this@OfficerRouteDisplayActivity)
                        .setTitle("Sürücü Seç")
                        .setSingleChoiceItems(driverNames, 0) { _, which ->
                            selectedIndex = which
                        }
                        .setPositiveButton("Ata") { _, _ ->
                            val selectedDriverId = drivers[selectedIndex].user_id
                            assignDriver(routeNumber, selectedDriverId)
                        }
                        .setNegativeButton("İptal", null)
                        .show()
                } else {
                    Toast.makeText(this@OfficerRouteDisplayActivity, "Sürücü listesi alınamadı", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<List<User>>, t: Throwable) {
                Toast.makeText(this@OfficerRouteDisplayActivity, "Ağ hatası: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun assignDriver(routeNumber: Int, driverId: Int) {
        val request = AssignDriverRequest(routeNumber, driverId)
        val call = routeApi.assignDriverToRoute(request)
        call.enqueue(object : Callback<Void> {
            override fun onResponse(call: Call<Void>, response: Response<Void>) {
                if (response.isSuccessful) {
                    Toast.makeText(this@OfficerRouteDisplayActivity, "Sürücü başarıyla atandı", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@OfficerRouteDisplayActivity, "Hata: ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<Void>, t: Throwable) {
                Toast.makeText(this@OfficerRouteDisplayActivity, "Ağ hatası: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }


    private fun initializeMapComponents() {
        val frameLayout = FrameLayout(this)
        frameLayout.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )

        mapView = MapView(this)
        frameLayout.addView(mapView)

        val routeInfoTextView = TextView(this).apply {
            id = View.generateViewId()
            setBackgroundColor(Color.parseColor("#AA000000"))
            setTextColor(Color.WHITE)
            textSize = 16f
            setPadding(20, 10, 20, 10)
            textAlignment = TextView.TEXT_ALIGNMENT_CENTER
            text = "🕒 Süre: ...   ⛽ Maliyet: ..."

            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.CENTER_HORIZONTAL
            )
        }
        frameLayout.addView(routeInfoTextView)

        // Route bilgilerini backend'den alır ve TextView'ı günceller
        routeApi.getRouteInfoByNumber(currentRouteNumber)
            .enqueue(object : Callback<RouteInfoResponse> {
                override fun onResponse(call: Call<RouteInfoResponse>, response: Response<RouteInfoResponse>) {
                    if (response.isSuccessful) {
                        val info = response.body()
                        if (info != null) {
                            updateRouteInfo(info.route_duration, info.route_fuel_cost, routeInfoTextView)
                        }
                    }
                }

                override fun onFailure(call: Call<RouteInfoResponse>, t: Throwable) {
                    Toast.makeText(this@OfficerRouteDisplayActivity, "Rota bilgileri alınamadı", Toast.LENGTH_SHORT).show()
                }
            })

        val assignDriverButton = Button(this).apply {
            text = "Bu rotaya sürücü ata"
            setPadding(20, 10, 20, 10)
            textSize = 18f
            setBackgroundColor(ContextCompat.getColor(context, android.R.color.holo_green_dark))
            setTextColor(ContextCompat.getColor(context, android.R.color.white))

            val buttonParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            buttonParams.bottomMargin = 50
            buttonParams.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            layoutParams = buttonParams

            setOnClickListener {
                showDriverSelectionDialog(currentRouteNumber)
            }
        }
        frameLayout.addView(assignDriverButton)

        val selectedColor = routeColors.getOrElse(currentRouteNumber % routeColors.size) { Color.BLACK }


        setContentView(frameLayout)

        // Drawable'dan marker'ı yükler
        val bitmap = BitmapFactory.decodeResource(resources, R.drawable.red_marker)

        val annotationPlugin = mapView.annotations
        pointAnnotationManager = annotationPlugin.createPointAnnotationManager()

        mapView.mapboxMap.setCamera(
            CameraOptions.Builder()
                .center(Point.fromLngLat(32.8532, 39.9215))
                .zoom(14.0)
                .build()
        )

        mapView.location.apply {
            setLocationProvider(navigationLocationProvider)
            locationPuck = LocationPuck2D()
            enabled = true
        }

        viewportDataSource = MapboxNavigationViewportDataSource(mapView.mapboxMap)

        val pixelDensity = this.resources.displayMetrics.density
        viewportDataSource.followingPadding = EdgeInsets(
            180.0 * pixelDensity,
            40.0 * pixelDensity,
            150.0 * pixelDensity,
            40.0 * pixelDensity
        )

        navigationCamera = NavigationCamera(mapView.mapboxMap, mapView.camera, viewportDataSource)

        routeLineApi = MapboxRouteLineApi(MapboxRouteLineApiOptions.Builder().build())

        routeLineView = MapboxRouteLineView(
            MapboxRouteLineViewOptions.Builder(this)
                .routeLineColorResources(
                    RouteLineColorResources.Builder()
                        .routeCasingColor(selectedColor)
                        .build()

                )
                .build()
        )
    }

    private val routesObserver = RoutesObserver { routeUpdateResult ->
        if (routeUpdateResult.navigationRoutes.isNotEmpty()) {
            routeLineApi.setNavigationRoutes(routeUpdateResult.navigationRoutes) { value ->
                mapView.mapboxMap.style?.apply { routeLineView.renderRouteDrawData(this, value) }
            }

            viewportDataSource.onRouteChanged(routeUpdateResult.navigationRoutes.first())
            viewportDataSource.evaluate()
            navigationCamera.requestNavigationCameraToOverview()
        }
    }


    private val locationObserver =
        object : LocationObserver {
            override fun onNewRawLocation(rawLocation: Location) {}

            override fun onNewLocationMatcherResult(locationMatcherResult: LocationMatcherResult) {
                val enhancedLocation = locationMatcherResult.enhancedLocation
                navigationLocationProvider.changePosition(
                    location = enhancedLocation,
                    keyPoints = locationMatcherResult.keyPoints,
                )

                viewportDataSource.onLocationChanged(enhancedLocation)
                viewportDataSource.evaluate()
                navigationCamera.requestNavigationCameraToFollowing()
            }
        }

    @OptIn(ExperimentalPreviewMapboxNavigationAPI::class)
    private val mapboxNavigation: MapboxNavigation by
    requireMapboxNavigation(
        onResumedObserver =
            object : MapboxNavigationObserver {
                @SuppressLint("MissingPermission")
                override fun onAttached(mapboxNavigation: MapboxNavigation) {
                    mapboxNavigation.registerRoutesObserver(routesObserver)
                    mapboxNavigation.registerLocationObserver(locationObserver)

                    replayProgressObserver =
                        ReplayProgressObserver(mapboxNavigation.mapboxReplayer)
                    mapboxNavigation.registerRouteProgressObserver(replayProgressObserver)
                    mapboxNavigation.startReplayTripSession()
                }

                override fun onDetached(mapboxNavigation: MapboxNavigation) {}
            },
        onInitialize = this::initNavigation
    )

    val routeListState = mutableStateListOf<List<Route>>()

    val routeCoordinates = mutableListOf<Point>()

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    @OptIn(ExperimentalPreviewMapboxNavigationAPI::class)
    private fun initNavigation() {
        MapboxNavigationApp.setup(NavigationOptions.Builder(this).build())

        mapView.location.apply {
            setLocationProvider(navigationLocationProvider)
            this.locationPuck = createDefault2DPuck()
            enabled = true
        }

        val routeCustomers = intent.extras?.getParcelableArrayList<Route>(
            "ROUTE_CUSTOMERS",
            Route::class.java
        ) ?: arrayListOf()



        routeListState.clear()
        routeListState.add(routeCustomers)

        val redMarker = BitmapFactory.decodeResource(resources, R.drawable.red_marker)
        val blueMarker = BitmapFactory.decodeResource(resources, R.drawable.blue_marker)

        if (routeCustomers.isNotEmpty()) {
            routeCoordinates.clear()
            routeCustomers.forEach { customer ->
                val point = Point.fromLngLat(customer.coordinates.lon, customer.coordinates.lat)
                routeCoordinates.add(point)

                val iconBitmap = if (customer.customer_name == "Depo") blueMarker else redMarker

                val annotationOptions = PointAnnotationOptions()
                    .withPoint(point)
                    .withIconImage(iconBitmap)
                    .withIconSize(0.5)

                pointAnnotationManager.create(annotationOptions)
            }
            Log.d("RenderRouteLine", "Fetched coordinates: $routeCoordinates")
        }

        mapboxNavigation.requestRoutes(
            RouteOptions.builder()
                .applyDefaultNavigationOptions()
                .coordinatesList(routeCoordinates)
                .alternatives(false)
                .continueStraight(false)
                .build(),
            object : NavigationRouterCallback {
                override fun onCanceled(routeOptions: RouteOptions, routerOrigin: String) {}

                override fun onFailure(reasons: List<RouterFailure>, routeOptions: RouteOptions) {}

                override fun onRoutesReady(routes: List<NavigationRoute>, routerOrigin: String) {
                    mapboxNavigation.setNavigationRoutes(routes)

                }
            }

        )
    }

    private fun updateRouteInfo(duration: Double, fuelCost: Double, textView: TextView) {
        textView.text = "⛽ Maliyet: ${"%.2f".format(fuelCost)} TL  🕒 Süre: ${"%.2f".format(duration)} dk"
    }


}
