package com.example.mapbox

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
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
import androidx.compose.runtime.mutableStateListOf
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.mapbox.data.api.RouteServiceClient
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.common.location.Location
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.MapView
import com.mapbox.maps.plugin.LocationPuck2D
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


    // Activity result launcher for location permissions
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

        // check/request location permissions
        if (
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            // Permissions are already granted
            initializeMapComponents()
        } else {
            // Request location permissions
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
                startSimulatedMovement() // butona basınca çağıracağımız fonksiyon
                visibility = View.GONE // butona bastıktan sonra kaybolsun
            }
        }

        frameLayout.addView(startRouteButton)

        // initializeMapComponents içinde eklenmeli:
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
            params.topMargin = 60
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
                        .routeCasingColor(Color.BLACK)   // dış çizgi rengi
                        .build()
                )
                .build()
        )
    }


    // routes observer draws a route line and origin/destination circles on the map
    private val routesObserver = RoutesObserver { routeUpdateResult ->
        if (routeUpdateResult.navigationRoutes.isNotEmpty()) {
            // generate route geometries asynchronously and render them
            routeLineApi.setNavigationRoutes(routeUpdateResult.navigationRoutes) { value ->
                mapView.mapboxMap.style?.apply { routeLineView.renderRouteDrawData(this, value) }
            }

            // update viewportSourceData to include the new route
            viewportDataSource.onRouteChanged(routeUpdateResult.navigationRoutes.first())
            viewportDataSource.evaluate()

            // set the navigationCamera to OVERVIEW
            navigationCamera.requestNavigationCameraToOverview()
        }
    }

    // locationObserver updates the location puck and camera to follow the user's location
    private val locationObserver =
        object : LocationObserver {
            override fun onNewRawLocation(rawLocation: Location) {}

            override fun onNewLocationMatcherResult(locationMatcherResult: LocationMatcherResult) {
                val enhancedLocation = locationMatcherResult.enhancedLocation
                navigationLocationProvider.changePosition(
                    location = enhancedLocation,
                    keyPoints = locationMatcherResult.keyPoints,
                )

                // 👇 Her konum güncellendiğinde bu fonksiyon çağrılacak
                checkIfUserReachedAnyLocation(enhancedLocation)

                viewportDataSource.onLocationChanged(enhancedLocation)
                viewportDataSource.evaluate()
                navigationCamera.requestNavigationCameraToFollowing()
            }

        }

    // define MapboxNavigation
    @OptIn(ExperimentalPreviewMapboxNavigationAPI::class)
    private val mapboxNavigation: MapboxNavigation by
    requireMapboxNavigation(
        onResumedObserver =
            object : MapboxNavigationObserver {
                @SuppressLint("MissingPermission")
                override fun onAttached(mapboxNavigation: MapboxNavigation) {
                    // register observers
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

                // 👇 DURAĞA ULAŞILDIĞINDA BUTONU GÖSTER
                val currentPoint = routeCoordinates.getOrNull(currentStopIndex)

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


    // on initialization of MapboxNavigation, request a route between two fixed points
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    @OptIn(ExperimentalPreviewMapboxNavigationAPI::class)
    private fun initNavigation() {
        MapboxNavigationApp.setup(NavigationOptions.Builder(this).build())

        // initialize location puck
        mapView.location.apply {
            setLocationProvider(navigationLocationProvider)
            this.locationPuck = createDefault2DPuck()
            enabled = true
        }

        routeCustomers = intent.extras
            ?.getParcelableArrayList<Route>("ROUTE_CUSTOMERS", Route::class.java)
            ?.toList() ?: emptyList()

        // UI’yı ilk durakla başlat:
        updateNextStopUI()
        routeListState.clear()
        routeListState.add(routeCustomers)

        val redMarker = BitmapFactory.decodeResource(resources, R.drawable.red_marker)
        val greenMarker = BitmapFactory.decodeResource(resources, R.drawable.green_marker)
        val blueMarker = BitmapFactory.decodeResource(resources, R.drawable.blue_marker)


        if (routeCustomers.isNotEmpty()) {
            routeCoordinates.clear()
            routeCustomers.forEach { customer ->
                val point = Point.fromLngLat(customer.coordinates.lon, customer.coordinates.lat)
                routeCoordinates.add(point)

                val iconBitmap = if (customer.customer_name == "Depot") blueMarker else redMarker

                val annotationOptions = PointAnnotationOptions()
                    .withPoint(point)
                    .withIconImage(iconBitmap)
                    .withIconSize(0.5)

                // ✅ Hem oluştur hem kaydet
                val annotation = pointAnnotationManager.create(annotationOptions)
                pointAnnotations[point] = annotation
            }
        }


        mapboxNavigation.requestRoutes(
            RouteOptions.builder()
                .applyDefaultNavigationOptions()
                .coordinatesList(routeCoordinates) // Tüm noktaları listeye ekleyin
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

    private fun goToNextStop() {
        currentStopIndex++
        updateNextStopUI()

    }



    private fun updateNextStopUI() {
        if (currentStopIndex < routeCustomers.size) {
            val customer = routeCustomers[currentStopIndex]
            nextStopInfo.text = "Sıradaki Durak: ${customer.customer_name}"
        } else {
            nextStopInfo.text = "Rota Tamamlandı 🎉"

        }
    }


    @OptIn(ExperimentalPreviewMapboxNavigationAPI::class)
    private fun startSimulatedMovement() {
        val routes = mapboxNavigation.getNavigationRoutes()
        if (routes.isNotEmpty()) {
            val replayData = replayRouteMapper.mapDirectionsRouteGeometry(routes.first().directionsRoute)
            mapboxNavigation.mapboxReplayer.pushEvents(replayData)
            mapboxNavigation.mapboxReplayer.seekTo(replayData[0])

            // Navigasyon hızını 2 katına çıkar
            mapboxNavigation.mapboxReplayer.playbackSpeed(3.0) //navigasyon hızı

            mapboxNavigation.mapboxReplayer.play()
        }
    }



} 