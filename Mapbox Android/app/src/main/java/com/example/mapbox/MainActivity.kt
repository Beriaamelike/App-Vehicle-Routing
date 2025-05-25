package com.example.mapbox

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.runtime.mutableStateListOf
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.common.location.Location
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.MapView
import com.mapbox.maps.plugin.LocationPuck2D
import com.mapbox.maps.plugin.animation.MapAnimationOptions.Companion.mapAnimationOptions
import com.mapbox.maps.plugin.animation.camera
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
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
import model.Route


@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class MainActivity : ComponentActivity() {
    private lateinit var mapView: MapView
    private lateinit var viewportDataSource: MapboxNavigationViewportDataSource
    private lateinit var navigationCamera: NavigationCamera
    private lateinit var routeLineApi: MapboxRouteLineApi
    private lateinit var routeLineView: MapboxRouteLineView
    private lateinit var replayProgressObserver: ReplayProgressObserver
    private lateinit var nextStopInfo: TextView
    lateinit var pointAnnotationManager: PointAnnotationManager
    private val navigationLocationProvider = NavigationLocationProvider()
    private val replayRouteMapper = ReplayRouteMapper()
    private val pointAnnotations = mutableMapOf<Point, com.mapbox.maps.plugin.annotation.generated.PointAnnotation>()
    private var routeCustomers: List<Route> = emptyList()
    private var currentStopIndex = 1
    private lateinit var startRouteButton: Button


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

        if (
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            initializeMapComponents()
        } else {
            locationPermissionRequest.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    private fun initializeMapComponents() {

        val frameLayout = FrameLayout(this)
        frameLayout.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )

        mapView = MapView(this)

        frameLayout.addView(mapView)


        val annotationPlugin = mapView.annotations
        pointAnnotationManager = annotationPlugin.createPointAnnotationManager()


        startRouteButton = Button(this).apply {
            text = "Rotayı Başlat"
            setPadding(20, 10, 20, 10)
            textSize = 18f
            setBackgroundColor(ContextCompat.getColor(context, android.R.color.holo_blue_dark))
            setTextColor(ContextCompat.getColor(context, android.R.color.white))

            val buttonParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            buttonParams.bottomMargin = 50
            buttonParams.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            layoutParams = buttonParams

            setOnClickListener {
                startSimulatedMovement()
                visibility = View.GONE
            }

        }

        frameLayout.addView(startRouteButton)

        nextStopInfo = TextView(this).apply {
            textSize = 16f
            setTextColor(Color.BLACK)
            setBackgroundColor(Color.WHITE)
            setPadding(30, 20, 30, 20)
            text = "Sıradaki Durak: —"

            val params = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            params.topMargin = 160
            layoutParams = params
        }



        frameLayout.addView(nextStopInfo)



        setContentView(frameLayout)

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
                        .routeCasingColor(Color.BLACK)
                        .build()
                )
                .build()
        )
    }

    private val routesObserver = RoutesObserver { routeUpdateResult ->


        if (routeUpdateResult.navigationRoutes.isNotEmpty()) {
            routeLineApi.setNavigationRoutes(routeUpdateResult.navigationRoutes) { value ->
                mapView.mapboxMap.style?.apply {
                    routeLineView.renderRouteDrawData(this, value)
                }
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

                checkIfUserReachedAnyLocation(enhancedLocation)

                viewportDataSource.onLocationChanged(enhancedLocation)
                viewportDataSource.evaluate()

                val cameraOptions = CameraOptions.Builder()
                    .center(Point.fromLngLat(enhancedLocation.longitude, enhancedLocation.latitude))
                    .zoom(17.5)
                    .pitch(60.0)
                    .bearing(enhancedLocation.bearing?.toDouble())
                    .build()

                mapView.camera.easeTo(cameraOptions, mapAnimationOptions {
                    duration(1000)
                })

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

                    replayProgressObserver = ReplayProgressObserver(mapboxNavigation.mapboxReplayer)
                    mapboxNavigation.registerRouteProgressObserver(replayProgressObserver)
                    mapboxNavigation.startReplayTripSession()
                }
                override fun onDetached(mapboxNavigation: MapboxNavigation) {}
            },
        onInitialize = this::initNavigation
    )

    val routeListState = mutableStateListOf<List<Route>>()

    val routeCoordinates = mutableListOf<Point>()

    private val reachedCustomers = mutableSetOf<Point>()


    private fun checkIfUserReachedAnyLocation(userLocation: Location) {
        val greenTick = BitmapFactory.decodeResource(resources, R.drawable.green_tick_marker)

        for (point in routeCoordinates) {
            if (reachedCustomers.contains(point)) continue

            val distance = haversine(
                userLocation.latitude, userLocation.longitude,
                point.latitude(), point.longitude()
            )

            if (distance < 50) {
                reachedCustomers.add(point)

                pointAnnotations[point]?.let { pointAnnotationManager.delete(it) }

                val annotationOptions = PointAnnotationOptions()
                    .withPoint(point)
                    .withIconImage(greenTick)
                    .withIconSize(0.5)

                val greenAnnotation = pointAnnotationManager.create(annotationOptions)
                pointAnnotations[point] = greenAnnotation

                if (point == routeCoordinates.getOrNull(currentStopIndex)) {
                    Toast.makeText(this@MainActivity, "✅Müşteri ID ${routeCustomers[currentStopIndex].customer_name} ziyaret edildi", Toast.LENGTH_SHORT).show()
                    currentStopIndex++
                    updateNextStopUI()
                }

            }

        }
    }


    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371000.0 // Dünya yarıçapı (metre)
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return R * c
    }


    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    @OptIn(ExperimentalPreviewMapboxNavigationAPI::class)
    private fun initNavigation() {
        MapboxNavigationApp.setup(NavigationOptions.Builder(this).build())

        mapView.location.apply {
            setLocationProvider(navigationLocationProvider)
            this.locationPuck = createDefault2DPuck()
            enabled = true
        }

        routeCustomers = intent.extras
            ?.getParcelableArrayList<Route>("ROUTE_CUSTOMERS", Route::class.java)
            ?.toList() ?: emptyList()

        updateNextStopUI()
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

                val annotation = pointAnnotationManager.create(annotationOptions)
                pointAnnotations[point] = annotation
            }
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


    private fun updateNextStopUI() {
        if (currentStopIndex < routeCustomers.size) {
            val customer = routeCustomers[currentStopIndex]
            val name = customer.customer_name
            val productId = customer.product_id
            val demand = customer.demand

            if (name.equals("Depo", ignoreCase = true)) {
                nextStopInfo.text = "Sıradaki Durak: Depo"
            } else {
                nextStopInfo.text =
                    "Sıradaki Durak:\nMüşteri ID: $name\n📦 Ürün ID: $productId\n⚖️ Ağırlık: $demand kg"
            }
        } else {
            nextStopInfo.text = "Rota Tamamlandı 🎉"

            android.app.AlertDialog.Builder(this)
                .setTitle("Rota Başarıyla Tamamlandı🎉")
                .setPositiveButton("Tamam") { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
        }
    }


    @OptIn(ExperimentalPreviewMapboxNavigationAPI::class)
    private fun startSimulatedMovement() {
        val routes = mapboxNavigation.getNavigationRoutes()
        if (routes.isNotEmpty()) {
            val replayData = replayRouteMapper.mapDirectionsRouteGeometry(routes.first().directionsRoute)
            mapboxNavigation.mapboxReplayer.pushEvents(replayData)
            mapboxNavigation.mapboxReplayer.seekTo(replayData[0])

            // Navigasyon hızını 3 katına çıkarır
            mapboxNavigation.mapboxReplayer.playbackSpeed(3.0) //navigasyon hızı

            mapboxNavigation.mapboxReplayer.play()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }

} 