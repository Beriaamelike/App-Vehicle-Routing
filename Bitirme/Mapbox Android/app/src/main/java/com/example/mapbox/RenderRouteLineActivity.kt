package com.example.mapbox


import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.mutableStateListOf
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.mapbox.data.api.RouteServiceClient
import com.example.mapbox.data.api.UserServiceClient
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.plugin.animation.MapAnimationOptions
import com.mapbox.maps.plugin.animation.camera
import com.mapbox.maps.plugin.locationcomponent.LocationComponentPlugin
import com.mapbox.maps.plugin.locationcomponent.OnIndicatorPositionChangedListener
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.navigation.base.ExperimentalPreviewMapboxNavigationAPI
import com.mapbox.navigation.base.extensions.applyDefaultNavigationOptions
import com.mapbox.navigation.base.extensions.applyLanguageAndVoiceUnitOptions
import com.mapbox.navigation.base.options.NavigationOptions
import com.mapbox.navigation.base.route.NavigationRoute
import com.mapbox.navigation.base.route.NavigationRouterCallback
import com.mapbox.navigation.base.route.RouterFailure
import com.mapbox.navigation.base.route.RouterOrigin
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.directions.session.RoutesObserver
import com.mapbox.navigation.core.lifecycle.MapboxNavigationApp
import com.mapbox.navigation.core.lifecycle.MapboxNavigationObserver
import com.mapbox.navigation.core.lifecycle.requireMapboxNavigation
import com.mapbox.navigation.core.preview.RoutesPreviewObserver
import com.mapbox.navigation.core.replay.route.ReplayProgressObserver
import com.mapbox.navigation.core.replay.route.ReplayRouteMapper
import com.mapbox.navigation.core.routealternatives.AlternativeRouteMetadata
import com.mapbox.navigation.core.trip.session.LocationMatcherResult
import com.mapbox.navigation.core.trip.session.LocationObserver
import com.mapbox.navigation.core.trip.session.RouteProgressObserver
import com.example.mapbox.databinding.MapboxActivityRouteLineBinding
import com.mapbox.navigation.ui.maps.NavigationStyles
import com.mapbox.navigation.ui.maps.location.NavigationLocationProvider
import com.mapbox.navigation.ui.maps.route.RouteLayerConstants.TOP_LEVEL_ROUTE_LINE_LAYER_ID
import com.mapbox.navigation.ui.maps.route.arrow.api.MapboxRouteArrowApi
import com.mapbox.navigation.ui.maps.route.arrow.api.MapboxRouteArrowView
import com.mapbox.navigation.ui.maps.route.arrow.model.RouteArrowOptions
import com.mapbox.navigation.ui.maps.route.callout.api.DefaultRouteCalloutAdapter
import com.mapbox.navigation.ui.maps.route.callout.model.DefaultRouteCalloutAdapterOptions
import com.mapbox.navigation.ui.maps.route.callout.model.RouteCalloutType
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineApi
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineView
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineApiOptions
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineViewOptions
import com.mapbox.navigation.ui.maps.route.line.model.RouteLineColorResources
import kotlinx.coroutines.launch
import model.UniqueRoutesResponse
import java.util.Date
import kotlin.time.Duration.Companion.minutes
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import model.Route


@OptIn(ExperimentalPreviewMapboxNavigationAPI::class)
class RenderRouteLineActivity : AppCompatActivity() {



    private lateinit var locationComponent: LocationComponentPlugin

    private lateinit var replayProgressObserver: ReplayProgressObserver

    private val viewBinding: MapboxActivityRouteLineBinding by lazy {
        MapboxActivityRouteLineBinding.inflate(layoutInflater)
    }

    private val navigationLocationProvider by lazy {
        NavigationLocationProvider()
    }


    private val routeLineViewOptions: MapboxRouteLineViewOptions by lazy {
        MapboxRouteLineViewOptions.Builder(this)
            .routeLineColorResources(
                RouteLineColorResources.Builder()
                    .routeDefaultColor(Color.GRAY) // default renk
                    .routeCasingColor(Color.DKGRAY) // dış kenar rengi
                    .build()
            )
            .routeLineBelowLayerId("road-label-navigation")
            .build()
    }


    private val routeLineApiOptions: MapboxRouteLineApiOptions by lazy {
        MapboxRouteLineApiOptions.Builder()

            .vanishingRouteLineEnabled(true)

            .isRouteCalloutsEnabled(true)
            .build()
    }


    private val routeLineView: MapboxRouteLineView by lazy {
        MapboxRouteLineView(routeLineViewOptions)
    }

    private val routeLineApi: MapboxRouteLineApi by lazy {
        MapboxRouteLineApi(routeLineApiOptions)
    }

    private val routeArrowApi: MapboxRouteArrowApi by lazy {
        MapboxRouteArrowApi()
    }


    private val routeArrowOptions by lazy {
        RouteArrowOptions.Builder(this)
            .withSlotName(TOP_LEVEL_ROUTE_LINE_LAYER_ID)
            .build()
    }

    private val routeArrowView: MapboxRouteArrowView by lazy {
        MapboxRouteArrowView(routeArrowOptions)
    }

    private val routeCalloutAdapterOptions: DefaultRouteCalloutAdapterOptions by lazy {
        DefaultRouteCalloutAdapterOptions.Builder()
            .similarDurationDelta(1.minutes)
            .routeCalloutType(RouteCalloutType.ROUTES_OVERVIEW)
            .build()
    }

    private val routeCalloutAdapter: DefaultRouteCalloutAdapter by lazy {
        DefaultRouteCalloutAdapter(this, routeCalloutAdapterOptions) { data ->
            reorderRoutes(data.route)
        }
    }

    private val routesObserver: RoutesObserver = RoutesObserver { routeUpdateResult ->

        updateRoutes(routeUpdateResult.navigationRoutes, emptyList())
    }

    private fun updateRoutes(routesList: List<NavigationRoute>, alternativesMetadata: List<AlternativeRouteMetadata>) {
        routeLineApi.setNavigationRoutes(routesList, emptyList()) { value ->
            viewBinding.mapView.mapboxMap.style?.apply {
                routeLineView.renderRouteDrawData(this, value)
            }
        }
    }

    private val routesPreviewObserver: RoutesPreviewObserver = RoutesPreviewObserver { update ->
        val preview = update.routesPreview ?: return@RoutesPreviewObserver

        updateRoutes(preview.routesList, preview.alternativesMetadata)
    }


    private val onPositionChangedListener = OnIndicatorPositionChangedListener { point ->
        val result = routeLineApi.updateTraveledRouteLine(point)
        viewBinding.mapView.mapboxMap.style?.apply {

            routeLineView.renderRouteLineUpdate(this, result)
        }
    }

    private val routeProgressObserver = RouteProgressObserver { routeProgress ->

        routeLineApi.updateWithRouteProgress(routeProgress) { result ->
            viewBinding.mapView.mapboxMap.style?.apply {
                routeLineView.renderRouteLineUpdate(this, result)
            }
        }

        val arrowUpdate = routeArrowApi.addUpcomingManeuverArrow(routeProgress)
        viewBinding.mapView.mapboxMap.style?.apply {

            routeArrowView.renderManeuverUpdate(this, arrowUpdate)
        }
    }

    private val locationObserver = object : LocationObserver {
        override fun onNewRawLocation(rawLocation: com.mapbox.common.location.Location) {}
        override fun onNewLocationMatcherResult(locationMatcherResult: LocationMatcherResult) {
            val enhancedLocation = locationMatcherResult.enhancedLocation
            navigationLocationProvider.changePosition(
                enhancedLocation,
                locationMatcherResult.keyPoints,
            )
            updateCamera(
                Point.fromLngLat(
                    enhancedLocation.longitude, enhancedLocation.latitude
                ),
                enhancedLocation.bearing
            )
        }
    }

    private val mapboxNavigation: MapboxNavigation by requireMapboxNavigation(
        onResumedObserver = object : MapboxNavigationObserver {
            @SuppressLint("MissingPermission")
            override fun onAttached(mapboxNavigation: MapboxNavigation) {
                mapboxNavigation.registerRoutesObserver(routesObserver)
                mapboxNavigation.registerRoutesPreviewObserver(routesPreviewObserver)
                mapboxNavigation.registerLocationObserver(locationObserver)
                mapboxNavigation.registerRouteProgressObserver(routeProgressObserver)

                replayProgressObserver = ReplayProgressObserver(mapboxNavigation.mapboxReplayer)
                mapboxNavigation.registerRouteProgressObserver(replayProgressObserver)

                mapboxNavigation.startReplayTripSession()

                fetchRoute()
            }

            override fun onDetached(mapboxNavigation: MapboxNavigation) {
                mapboxNavigation.unregisterRoutesObserver(routesObserver)
                mapboxNavigation.unregisterRoutesPreviewObserver(routesPreviewObserver)
                mapboxNavigation.unregisterLocationObserver(locationObserver)
                mapboxNavigation.unregisterRouteProgressObserver(routeProgressObserver)
                mapboxNavigation.unregisterRouteProgressObserver(replayProgressObserver)
                mapboxNavigation.mapboxReplayer.finish()
            }
        },
        onInitialize = this::initNavigation
    )

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(viewBinding.root)

        fetchRouteData()


        viewBinding.startNavigation.setOnClickListener {
            mapboxNavigation.moveRoutesFromPreviewToNavigator()
            updateRouteCalloutType(RouteCalloutType.NAVIGATION)

            viewBinding.startNavigation.isVisible = false
            viewBinding.routeOverview.isVisible = true
        }

        viewBinding.routeOverview.setOnClickListener {
            if (routeCalloutAdapter.options.routeCalloutType == RouteCalloutType.ROUTES_OVERVIEW) {
                updateRouteCalloutType(RouteCalloutType.NAVIGATION)
                viewBinding.routeOverview.text = "Route overview"
            } else {
                updateRouteCalloutType(RouteCalloutType.ROUTES_OVERVIEW)
                viewBinding.routeOverview.text = "Following mode"
            }
        }
    }


    private fun updateRouteCalloutType(@RouteCalloutType.Type type: Int) {
        routeCalloutAdapter.updateOptions(
            routeCalloutAdapterOptions.toBuilder()
                .routeCalloutType(type)
                .build()
        )
    }


    override fun onDestroy() {
        super.onDestroy()
        routeLineView.cancel()
        routeLineApi.cancel()
        locationComponent.removeOnIndicatorPositionChangedListener(onPositionChangedListener)
    }

    @OptIn(ExperimentalPreviewMapboxNavigationAPI::class)
    private fun fetchRoute() {
        mapboxNavigation.requestRoutes(
            RouteOptions.builder()
                .applyDefaultNavigationOptions()
                .applyLanguageAndVoiceUnitOptions(this)
                .coordinatesList(routeCoordinates)
                .layersList(listOf(mapboxNavigation.getZLevel(), null))
                .alternatives(false)
                .build(),

            object : NavigationRouterCallback {
                override fun onRoutesReady(
                    routes: List<NavigationRoute>,
                    @RouterOrigin routerOrigin: String
                ) {
                    viewBinding.startNavigation.isVisible = true
                    mapboxNavigation.setRoutesPreview(routes)
                }

                override fun onFailure(
                    reasons: List<RouterFailure>,
                    routeOptions: RouteOptions
                ) {
                    Log.d(LOG_TAG, "onFailure: $reasons")
                }

                override fun onCanceled(
                    routeOptions: RouteOptions,
                    @RouterOrigin routerOrigin: String
                ) {
                    Log.d(LOG_TAG, "onCanceled")
                }
            }
        )
    }


    fun drawAllRoutes(routeCustomers: List<List<Map<String, Any>>>) {
        routeCustomers.forEachIndexed { index, routeData ->

            val coordinates = routeData.map { customer ->
                val coords = customer["coordinates"] as Map<*, *>
                Point.fromLngLat(coords["lon"] as Double, coords["lat"] as Double)
            }

            val routeOptions = RouteOptions.builder()
                .applyDefaultNavigationOptions()
                .coordinatesList(coordinates)
                .alternatives(false)
                .build()

            mapboxNavigation.requestRoutes(routeOptions, object : NavigationRouterCallback {
                override fun onRoutesReady(routes: List<NavigationRoute>, routerOrigin: String) {
                    routes.firstOrNull()?.let { route ->
                        routeLineApi.setNavigationRoutes(listOf(route), emptyList()) { drawResult ->
                            viewBinding.mapView.mapboxMap.getStyle { style ->
                                routeLineView.renderRouteDrawData(style, drawResult)
                            }
                        }
                    }
                }

                override fun onFailure(reasons: List<RouterFailure>, routeOptions: RouteOptions) {
                    Log.e("RouteDraw", "Route $index failed: $reasons")
                }

                override fun onCanceled(routeOptions: RouteOptions, routerOrigin: String) {
                    Log.d("RouteDraw", "Route $index canceled")
                }
            })
        }
    }




    private fun updateCamera(point: Point, bearing: Double?) {
        if (routeCoordinates.isEmpty()) {
            Log.e("RenderRouteLine", "updateCamera failed: routeCoordinates is empty")
            return
        }

        val cameraOptions = if (routeCalloutAdapter.options.routeCalloutType == RouteCalloutType.ROUTES_OVERVIEW) {
            viewBinding.mapView.mapboxMap.cameraForCoordinates(
                listOf(
                    point,
                    routeCoordinates.last()
                ),
                CameraOptions.Builder()
                    .bearing(bearing)
                    .padding(EdgeInsets(100.0, 100.0, 100.0, 100.0))
                    .build(),
                null,
                null,
                null,
            )
        } else {
            CameraOptions.Builder()
                .center(point)
                .bearing(bearing)
                .pitch(45.0)
                .zoom(15.0)
                .padding(EdgeInsets(200.0, 200.0, 200.0, 200.0))
                .build()
        }

        val mapAnimationOptionsBuilder = MapAnimationOptions.Builder()
        viewBinding.mapView.camera.easeTo(
            cameraOptions,
            mapAnimationOptionsBuilder.build(),
        )
    }


    private fun reorderRoutes(clickedRoute: NavigationRoute) {

        if (clickedRoute != routeLineApi.getPrimaryNavigationRoute()) {
            if (mapboxNavigation.getRoutesPreview() == null) {
                val reOrderedRoutes = mapboxNavigation.getNavigationRoutes()
                    .filter { clickedRoute.id != it.id }
                    .toMutableList()
                    .also { list ->
                        list.add(0, clickedRoute)
                    }
                mapboxNavigation.setNavigationRoutes(reOrderedRoutes)
            } else {
                mapboxNavigation.changeRoutesPreviewPrimaryRoute(clickedRoute)
            }
        }
    }

    private companion object {
        val LOG_TAG: String = RenderRouteLineActivity::class.java.simpleName
    }
    val routeListState = mutableStateListOf<List<Route>>()


    val routeCoordinates = mutableListOf<Point>()

    private fun replayOriginLocation() {
        if (routeCoordinates.isNotEmpty()) {
            with(mapboxNavigation.mapboxReplayer) {
                play()
                pushEvents(
                    listOf(
                        ReplayRouteMapper.mapToUpdateLocation(
                            Date().time.toDouble(),
                            routeCoordinates.first()
                        )
                    )
                )
                playFirstLocation()
                playbackSpeed(3.0)
            }
        } else {
            Log.e("RenderRouteLine", "Cannot start replay, routeCoordinates is empty.")
        }
    }

    private fun fetchRouteData() {
        RouteServiceClient.routeApi.getUniqueRoutes().enqueue(object : Callback<UniqueRoutesResponse> {
            override fun onResponse(call: Call<UniqueRoutesResponse>, response: Response<UniqueRoutesResponse>) {
                if (response.isSuccessful) {
                    val allRoutes = response.body()?.route_customers ?: emptyList()
                    if (allRoutes.isEmpty()) {
                        Log.e("RenderRouteLine", "No routes found in API")
                        return
                    }

                    val collectedRoutes = mutableListOf<NavigationRoute>()
                    val allCoords = mutableListOf<Point>()
                    var completed = 0

                    allRoutes.forEach { routeData ->
                        val segmentCoordinates = routeData.map {
                            Point.fromLngLat(it.coordinates.lon, it.coordinates.lat)
                        }

                        allCoords.addAll(segmentCoordinates)

                        val routeOptions = RouteOptions.builder()
                            .applyDefaultNavigationOptions()
                            .applyLanguageAndVoiceUnitOptions(this@RenderRouteLineActivity)
                            .coordinatesList(segmentCoordinates)
                            .alternatives(false)
                            .build()

                        mapboxNavigation.requestRoutes(routeOptions, object : NavigationRouterCallback {
                            override fun onRoutesReady(routes: List<NavigationRoute>, routerOrigin: String) {
                                routes.firstOrNull()?.let {
                                    collectedRoutes.add(it)
                                }
                                completed++
                                if (completed == allRoutes.size) {
                                    drawAllCollectedRoutes(collectedRoutes, allCoords)
                                }
                            }

                            override fun onFailure(reasons: List<RouterFailure>, routeOptions: RouteOptions) {
                                Log.e("RenderRouteLine", "Route failed: $reasons")
                                completed++
                                if (completed == allRoutes.size) {
                                    drawAllCollectedRoutes(collectedRoutes, allCoords)
                                }
                            }

                            override fun onCanceled(routeOptions: RouteOptions, routerOrigin: String) {
                                Log.d("RenderRouteLine", "Route canceled")
                                completed++
                                if (completed == allRoutes.size) {
                                    drawAllCollectedRoutes(collectedRoutes, allCoords)
                                }
                            }
                        })
                    }
                } else {
                    Log.e("RenderRouteLine", "Failed to fetch route data")
                }
            }

            override fun onFailure(call: Call<UniqueRoutesResponse>, t: Throwable) {
                Log.e("RenderRouteLine", "Error fetching route data: ${t.message}")
            }
        })
    }

    private fun drawAllCollectedRoutes(routes: List<NavigationRoute>, coords: List<Point>) {
        if (routes.isEmpty()) {
            Log.e("RenderRouteLine", "No valid routes to draw")
            return
        }

        routeCoordinates.clear()
        routeCoordinates.addAll(coords)

        routeLineApi.setNavigationRoutes(routes, emptyList()) { drawResult ->
            viewBinding.mapView.mapboxMap.getStyle { style ->
                routeLineView.renderRouteDrawData(style, drawResult)
            }
        }

        updateCamera(coords.first(), null)

        mapboxNavigation.setRoutesPreview(routes)
    }


    private fun initNavigation() {
        MapboxNavigationApp.setup(
            NavigationOptions.Builder(this)
                .build()
        )

        locationComponent = viewBinding.mapView.location.apply {
            setLocationProvider(navigationLocationProvider)
            addOnIndicatorPositionChangedListener(onPositionChangedListener)
            enabled = true
        }
    }


    private fun requestRoute(routeCoordinates: List<Point>) {
        if (routeCoordinates.size < 2) {
            Log.e("RenderRouteLine", "Need at least 2 points for a route")
            return
        }

        val routeSegments = mutableListOf<List<Point>>()
        var currentSegment = mutableListOf(routeCoordinates.first())
        for (i in 1 until routeCoordinates.size) {
            if (routeCoordinates[i] == routeCoordinates[i - 1]) {
                if (currentSegment.size >= 2) routeSegments.add(currentSegment)
                currentSegment = mutableListOf(routeCoordinates[i])
            } else {
                currentSegment.add(routeCoordinates[i])
            }
        }
        if (currentSegment.size >= 2) routeSegments.add(currentSegment)

        Log.d("RenderRouteLine", "Split into ${routeSegments.size} route segments")

        val allRoutes = mutableListOf<NavigationRoute>()
        var completedSegments = 0

        val allCoords = mutableListOf<Point>()

        routeSegments.forEachIndexed { index, segment ->
            val routeOptions = RouteOptions.builder()
                .applyDefaultNavigationOptions()
                .applyLanguageAndVoiceUnitOptions(this)
                .coordinatesList(segment)
                .alternatives(false)
                .build()

            allCoords.addAll(segment)

            mapboxNavigation.requestRoutes(routeOptions, object : NavigationRouterCallback {
                override fun onRoutesReady(routes: List<NavigationRoute>, routerOrigin: String) {
                    routes.firstOrNull()?.let { primaryRoute ->
                        allRoutes.add(primaryRoute)
                        completedSegments++

                        if (completedSegments == 1) {
                            updateCamera(segment.first(), null)
                        }

                        if (completedSegments == routeSegments.size) {

                            this@RenderRouteLineActivity.routeCoordinates.clear()
                            this@RenderRouteLineActivity.routeCoordinates.addAll(allCoords)

                            routeLineApi.setNavigationRoutes(allRoutes, emptyList()) { drawResult ->
                                viewBinding.mapView.mapboxMap.getStyle { style ->
                                    routeLineView.renderRouteDrawData(style, drawResult)
                                }
                            }

                            mapboxNavigation.setRoutesPreview(allRoutes)
                        }
                    }
                }

                override fun onFailure(reasons: List<RouterFailure>, routeOptions: RouteOptions) {
                    Log.e("RenderRouteLine", "Route segment $index failed: ${reasons.joinToString()}")
                }

                override fun onCanceled(routeOptions: RouteOptions, routerOrigin: String) {
                    Log.d("RenderRouteLine", "Route segment $index canceled")
                }
            })
        }
    }



}