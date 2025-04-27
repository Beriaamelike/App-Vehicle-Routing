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
import com.example.mapbox.data.api.RouteServiceClient
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
import model.UniqueRoutesResponse
import java.util.Date
import kotlin.time.Duration.Companion.minutes
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import model.Route


/**
 * This example demonstrates the usage of the route line, route arrow API's, route callout API and UI elements.
 *
 * Before running the example make sure you have put your access_token in the correct place
 * inside [app/src/main/res/values/mapbox_access_token.xml]. If not present then add this file
 * at the location mentioned above and add the following content to it
 *
 * <?xml version="1.0" encoding="utf-8"?>
 * <resources xmlns:tools="http://schemas.android.com/tools">
 *     <string name="mapbox_access_token"><PUT_YOUR_ACCESS_TOKEN_HERE></string>
 * </resources>
 *
 * The example assumes that you have granted location permissions and does not enforce it. However,
 * the permission is essential for proper functioning of this example. The example also uses replay
 * location engine to facilitate navigation without actually physically moving.
 *
 * The example uses camera API's exposed by the Maps SDK rather than using the API's exposed by the
 * Navigation SDK. This is done to make the example concise and keep the focus on actual feature at
 * hand. To learn more about how to use the camera API's provided by the Navigation SDK look at
 * [ShowCameraTransitionsActivity]
 *
 * How to use this example:
 * - The example uses a list of predefined coordinates that will be used to fetch a route.
 * - When the example starts, the camera transitions to fit route origin and destination, the route between them fetches
 * - Click on Start navigation to make the puck follows the primary route.
 * - Click on Route overview to make camera fir the entire route and switch route callout type (to display route duration)
 * - Click on Following mode to switch back to the navigation mode where callouts displays relative difference duration.
 */
@OptIn(ExperimentalPreviewMapboxNavigationAPI::class)
class RenderRouteLineActivity : AppCompatActivity() {



    private lateinit var locationComponent: LocationComponentPlugin

    /**
     * Debug observer that makes sure the replayer has always an up-to-date information to generate mock updates.
     */
    private lateinit var replayProgressObserver: ReplayProgressObserver

    /**
     * Bindings to the example layout.
     */
    private val viewBinding: MapboxActivityRouteLineBinding by lazy {
        MapboxActivityRouteLineBinding.inflate(layoutInflater)
    }

    /**
     * [NavigationLocationProvider] is a utility class that helps to provide location updates generated by the Navigation SDK
     * to the Maps SDK in order to update the user location indicator on the map.
     */
    private val navigationLocationProvider by lazy {
        NavigationLocationProvider()
    }

    /**
     * RouteLine: Additional route line options are available through the
     * [MapboxRouteLineViewOptions] and [MapboxRouteLineApiOptions].
     * Notice here the [MapboxRouteLineViewOptions.routeLineBelowLayerId] option. The map is made up of layers. In this
     * case the route line will be placed below the "road-label" layer which is a good default
     * for the most common Mapbox navigation related maps. You should consider if this should be
     * changed for your use case especially if you are using a custom map style.
     */
    private val routeLineViewOptions: MapboxRouteLineViewOptions by lazy {
        MapboxRouteLineViewOptions.Builder(this)
            .routeLineColorResources(
                RouteLineColorResources.Builder()
                    .routeDefaultColor(Color.parseColor("#3F51B5")) // Sadece birincil rota rengi
                    .routeCasingColor(Color.LTGRAY)
                    .build()
            )
            .routeLineBelowLayerId("road-label-navigation")
            .build()
    }

    private val routeLineApiOptions: MapboxRouteLineApiOptions by lazy {
        MapboxRouteLineApiOptions.Builder()
            /**
             * Remove this line and [onPositionChangedListener] if you don't wish to show the
             * vanishing route line feature
             */
            .vanishingRouteLineEnabled(true)
            /**
             * Remove this line if you don't wish to show the route callout feature
             */
            .isRouteCalloutsEnabled(true)
            .build()
    }

    /**
     * RouteLine: This class is responsible for rendering route line related mutations generated
     * by the [routeLineApi]
     */
    private val routeLineView: MapboxRouteLineView by lazy {
        MapboxRouteLineView(routeLineViewOptions)
    }

    private val routeLineApi: MapboxRouteLineApi by lazy {
        MapboxRouteLineApi(routeLineApiOptions)
    }
    /**
     * RouteArrow: This class is responsible for generating data related to maneuver arrows. The
     * data generated must be rendered by the [routeArrowView] in order to apply mutations to
     * the map.
     */
    private val routeArrowApi: MapboxRouteArrowApi by lazy {
        MapboxRouteArrowApi()
    }

    /**
     * RouteArrow: Customization of the maneuver arrow(s) can be done using the
     * [RouteArrowOptions]. Here the above layer ID is used to determine where in the map layer
     * stack the arrows appear. Above the layer of the route traffic line is being used here. Your
     * use case may necessitate adjusting this to a different layer position.
     */
    private val routeArrowOptions by lazy {
        RouteArrowOptions.Builder(this)
            .withSlotName(TOP_LEVEL_ROUTE_LINE_LAYER_ID)
            .build()
    }

    /**
     * RouteArrow: This class is responsible for rendering the arrow related mutations generated
     * by the [routeArrowApi]
     */
    private val routeArrowView: MapboxRouteArrowView by lazy {
        MapboxRouteArrowView(routeArrowOptions)
    }

    /**
     * RouteCallout: Customization of the default adapter can be done using the
     * [DefaultRouteCalloutAdapterOptions]. Here the similar duration delta threshold determines when routes consider as
     * similar, and the route callout type determines that we want to show duration of each route on related callout.
     */
    private val routeCalloutAdapterOptions: DefaultRouteCalloutAdapterOptions by lazy {
        DefaultRouteCalloutAdapterOptions.Builder()
            .similarDurationDelta(1.minutes)
            .routeCalloutType(RouteCalloutType.ROUTES_OVERVIEW)
            .build()
    }

    /**
     * RouteCallout: This class is responsible for rendering route callout related mutations generated
     * by the [routeLineApi]
     */
    private val routeCalloutAdapter: DefaultRouteCalloutAdapter by lazy {
        DefaultRouteCalloutAdapter(this, routeCalloutAdapterOptions) { data ->
            reorderRoutes(data.route)
        }
    }

    /**
     * RouteLine: This is one way to keep the route(s) appearing on the map in sync with
     * MapboxNavigation. When this observer is called the route data is used to draw route(s)
     * on the map.
     */
    private val routesObserver: RoutesObserver = RoutesObserver { routeUpdateResult ->
        // Pass empty list for alternatives metadata
        updateRoutes(routeUpdateResult.navigationRoutes, emptyList())
    }

    private fun updateRoutes(routesList: List<NavigationRoute>, alternativesMetadata: List<AlternativeRouteMetadata>) {
        routeLineApi.setNavigationRoutes(routesList, emptyList()) { value ->  // Force empty alternatives
            viewBinding.mapView.mapboxMap.style?.apply {
                routeLineView.renderRouteDrawData(this, value)
            }
        }
    }

    private val routesPreviewObserver: RoutesPreviewObserver = RoutesPreviewObserver { update ->
        val preview = update.routesPreview ?: return@RoutesPreviewObserver

        updateRoutes(preview.routesList, preview.alternativesMetadata)
    }



    /**
     * RouteLine: This listener is necessary only when enabling the vanishing route line feature
     * which changes the color of the route line behind the puck during navigation. If this
     * option is set to `false` (the default) in MapboxRouteLineApiOptions then it is not necessary
     * to use this listener.
     */
    private val onPositionChangedListener = OnIndicatorPositionChangedListener { point ->
        val result = routeLineApi.updateTraveledRouteLine(point)
        viewBinding.mapView.mapboxMap.style?.apply {
            // Render the result to update the map.
            routeLineView.renderRouteLineUpdate(this, result)
        }
    }

    private val routeProgressObserver = RouteProgressObserver { routeProgress ->
        // RouteLine: This line is only necessary if the vanishing route line feature
        // is enabled.
        routeLineApi.updateWithRouteProgress(routeProgress) { result ->
            viewBinding.mapView.mapboxMap.style?.apply {
                routeLineView.renderRouteLineUpdate(this, result)
            }
        }

        // RouteArrow: The next maneuver arrows are driven by route progress events.
        // Generate the next maneuver arrow update data and pass it to the view class
        // to visualize the updates on the map.
        val arrowUpdate = routeArrowApi.addUpcomingManeuverArrow(routeProgress)
        viewBinding.mapView.mapboxMap.style?.apply {
            // Render the result to update the map.
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


        // Verileri API'den çekmek için fetchRouteData çağrılıyor
        fetchRouteData()

        viewBinding.mapView.mapboxMap.loadStyle(NavigationStyles.NAVIGATION_DAY_STYLE) {
            // Route layers initialization
            routeLineView.initializeLayers(it)

        }

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



    private fun updateCamera(point: Point, bearing: Double?) {
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
        // if we clicked on some route callout that is not primary,
        // we make this route primary and all the others - alternative
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
        // routeCoordinates listesi boş olursa işlemi başlatma
        if (routeCoordinates.isNotEmpty()) {
            with(mapboxNavigation.mapboxReplayer) {
                play()
                pushEvents(
                    listOf(
                        ReplayRouteMapper.mapToUpdateLocation(
                            Date().time.toDouble(),
                            routeCoordinates.first() // İlk koordinatla başlat
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
            @RequiresApi(Build.VERSION_CODES.TIRAMISU)
            override fun onResponse(call: Call<UniqueRoutesResponse>, response: Response<UniqueRoutesResponse>) {
                if (response.isSuccessful) {
                    val routeData = response.body()?.route_customers?.firstOrNull() ?: emptyList()
                    Log.d("RenderRouteLine", "Received ${routeData.size} points from API")
                    routeData.forEach { customer ->
                        Log.d("RenderRouteLine", "Point: ${customer.coordinates.lon}, ${customer.coordinates.lat}")
                    }

                    routeListState.clear()
                    routeListState.add(routeData)

                    routeCoordinates.clear()
                    routeData.forEach { customer ->
                        routeCoordinates.add(Point.fromLngLat(customer.coordinates.lon, customer.coordinates.lat))
                    }

                    Log.d("RenderRouteLine", "Fetched coordinates: $routeCoordinates")



                    /*
                                        // TEST VERİLERİİİİİİİİİ

                                        val testCoordinates = listOf(
                                            Point.fromLngLat(32.853232352136736, 39.921553192153254), // kızılay avm
                                            Point.fromLngLat(32.88978499631443, 39.943985207307676), // Saimekadın kız yurdu
                                            Point.fromLngLat(32.84647056747842, 39.931582657577955), // gazi müh fakültesi
                                            Point.fromLngLat(32.853232352136736, 39.921553192153254), // kızılay avm
                                            Point.fromLngLat(32.853232352136736, 39.921553192153254), // kızılay avm
                                            Point.fromLngLat(32.87279697173746, 39.937789770413545), // ulucanlar cezaevi müzesi
                                            Point.fromLngLat(32.86016546747694, 39.90216468230411), // kuğulu park

                                            Point.fromLngLat(32.853232352136736, 39.921553192153254) // kızılay avm

                                        )


                                        Log.d("TEST_ROUTE", "Test koordinatları: $testCoordinates")
                                        requestRoute(testCoordinates)

                    */
                    if (routeCoordinates.isNotEmpty()) {
                        // First request the route
                        requestRoute(routeCoordinates)

                        // Then start replay only after we have coordinates
                        // replayOriginLocation()
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

    private fun initNavigation() {
        MapboxNavigationApp.setup(
            NavigationOptions.Builder(this)
                // Don't set routeAlternativesOptions at all to disable alternatives
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

        // Split coordinates into segments when consecutive points are the same (depot stops)
        val routeSegments = mutableListOf<List<Point>>()
        var currentSegment = mutableListOf(routeCoordinates.first())

        for (i in 1 until routeCoordinates.size) {
            if (routeCoordinates[i] == routeCoordinates[i-1]) {
                if (currentSegment.size >= 2) {
                    routeSegments.add(currentSegment)
                }
                currentSegment = mutableListOf(routeCoordinates[i])
            } else {
                currentSegment.add(routeCoordinates[i])
            }
        }

        if (currentSegment.size >= 2) {
            routeSegments.add(currentSegment)
        }

        Log.d("RenderRouteLine", "Split into ${routeSegments.size} route segments")

        val allRoutes = mutableListOf<NavigationRoute>()
        val colorMap = mutableMapOf<String, Int>()

        routeSegments.forEachIndexed { index, segment ->
            val routeOptions = RouteOptions.builder()
                .applyDefaultNavigationOptions()
                .applyLanguageAndVoiceUnitOptions(this)
                .coordinatesList(segment)
                .alternatives(false) // Explicitly disable alternatives
                .build()

            Log.d("RenderRouteLine", "Requesting route segment $index with points: $segment")

            mapboxNavigation.requestRoutes(routeOptions, object : NavigationRouterCallback {
                override fun onRoutesReady(routes: List<NavigationRoute>, routerOrigin: String) {
                    routes.firstOrNull()?.let { primaryRoute ->
                        // Assign a unique color based on segment index
                        val routeColor = when (index % 6) {
                            0 -> Color.RED
                            1 -> Color.BLUE
                            2 -> Color.GREEN
                            3 -> Color.YELLOW
                            4 -> Color.MAGENTA
                            5 -> Color.CYAN
                            else -> Color.GRAY
                        }

                        allRoutes.add(primaryRoute)
                        colorMap[primaryRoute.id] = routeColor

                        if (allRoutes.size == routeSegments.size) {
                            // Update the map with all routes
                            mapboxNavigation.setNavigationRoutes(allRoutes)
                            updateCamera(routeSegments.first().first(), null)
                        }
                    }
                }

                override fun onFailure(reasons: List<RouterFailure>, routeOptions: RouteOptions) {
                    Log.e("RenderRouteLine", "Route request failed: ${reasons.joinToString()}")
                }

                override fun onCanceled(routeOptions: RouteOptions, routerOrigin: String) {
                    Log.d("RenderRouteLine", "Route request canceled for segment $index")
                }
            })
        }
    }



}