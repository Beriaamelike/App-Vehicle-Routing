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
import android.widget.Button
import android.widget.FrameLayout
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
import model.User
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import com.mapbox.navigation.ui.maps.route.line.model.*


@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class OfficerFullRouteDisplayActivity : ComponentActivity() {
    private lateinit var mapView: MapView
    private lateinit var viewportDataSource: MapboxNavigationViewportDataSource
    private lateinit var navigationCamera: NavigationCamera
    private lateinit var routeLineApi: MapboxRouteLineApi
    private lateinit var routeLineView: MapboxRouteLineView
    private lateinit var replayProgressObserver: ReplayProgressObserver
    private val navigationLocationProvider = NavigationLocationProvider()
    private val replayRouteMapper = ReplayRouteMapper()
    lateinit var pointAnnotationManager: PointAnnotationManager


    private var currentRouteNumber: Int = -1

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



    private fun initializeMapComponents() {
        val frameLayout = FrameLayout(this)
        frameLayout.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )

        mapView = MapView(this)
        frameLayout.addView(mapView)


        setContentView(frameLayout)

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
                        .routeDefaultColor(Color.BLACK)
                        .routeCasingColor(Color.BLACK)
                        .alternativeRouteDefaultColor(Color.GREEN)
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

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    @OptIn(ExperimentalPreviewMapboxNavigationAPI::class)
    private fun initNavigation() {
        MapboxNavigationApp.setup(NavigationOptions.Builder(this).build())

        mapView.location.apply {
            setLocationProvider(navigationLocationProvider)
            locationPuck = createDefault2DPuck()
            enabled = true
        }

        val allRoutes = intent.extras?.getParcelableArrayList<Route>("ALL_ROUTES", Route::class.java) ?: arrayListOf()
        val groupedByRoute = allRoutes.groupBy { it.route_number }

        val redMarker = BitmapFactory.decodeResource(resources, R.drawable.red_marker)
        val blueMarker = BitmapFactory.decodeResource(resources, R.drawable.blue_marker)

        val collectedRoutes = mutableListOf<NavigationRoute>()
        var completedCount = 0

        groupedByRoute.forEach { (_, routeList) ->
            val coordinates = routeList.map {
                Point.fromLngLat(it.coordinates.lon, it.coordinates.lat)
            }

            routeList.forEach { customer ->
                val iconBitmap = if (customer.customer_name == "Depot") blueMarker else redMarker
                val annotationOptions = PointAnnotationOptions()
                    .withPoint(Point.fromLngLat(customer.coordinates.lon, customer.coordinates.lat))
                    .withIconImage(iconBitmap)
                    .withIconSize(0.5)
                pointAnnotationManager.create(annotationOptions)
            }

            val routeOptions = RouteOptions.builder()
                .applyDefaultNavigationOptions()
                .coordinatesList(coordinates)
                .alternatives(false)
                .continueStraight(false)
                .build()

            mapboxNavigation.requestRoutes(routeOptions, object : NavigationRouterCallback {
                override fun onRoutesReady(routes: List<NavigationRoute>, routerOrigin: String) {
                    collectedRoutes.addAll(routes)
                    completedCount++

                    // Tüm route_number gruplarının dönüşü tamamlandığında çiz
                    if (completedCount == groupedByRoute.size) {
                        routeLineApi.setNavigationRoutes(collectedRoutes) { drawResult ->
                            mapView.mapboxMap.getStyle { style ->
                                routeLineView.renderRouteDrawData(style, drawResult)
                            }
                        }
                    }
                }

                override fun onCanceled(routeOptions: RouteOptions, routerOrigin: String) {}
                override fun onFailure(reasons: List<RouterFailure>, routeOptions: RouteOptions) {}
            })
        }
    }


}
