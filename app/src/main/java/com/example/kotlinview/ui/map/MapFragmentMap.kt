package com.example.kotlinview.ui.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.kotlinview.R
import com.example.kotlinview.core.NetworkMonitor
import com.example.kotlinview.core.ServiceLocator
import com.example.kotlinview.data.local.ExperienceLocalStore
import com.example.kotlinview.data.map.ExperienceDtoMap
import com.example.kotlinview.databinding.FragmentMapMapBinding
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class MapFragmentMap : Fragment() {

    private var _binding: FragmentMapMapBinding? = null
    private val binding get() = _binding!!

    private val viewModelMap: MapViewModelMap by viewModels {
        MapVmFactoryMap(ServiceLocator.experiencesRepository)
    }

    private var osmdroidMap: MapView? = null
    private var myLocationOverlay: MyLocationNewOverlay? = null
    private var selectedId: String? = null
    private val markersById = mutableMapOf<String, Marker>()

    // Local store (Room + MMKV + DataStore)
    private lateinit var localStore: ExperienceLocalStore

    // Log usage once per fragment lifecycle
    private var hasLoggedUsage = false

    // Offline dialog guard
    private var hasShownOfflineDialog = false
    private val OFFLINE_MSG =
        "There is no internet connection. Please try again when the connection is restored."

    // --- Auto-move refresh (policy-controlled via DataStore) ---
    private var moveCallback: LocationCallback? = null
    private var lastRefreshLat: Double? = null
    private var lastRefreshLng: Double? = null
    private var lastRefreshAtMs: Long = 0L

    // Policy knobs loaded from DataStore (with safe defaults)
    private var policyLoaded = false
    private var policyAutoRefreshEnabled: Boolean = true
    private var policyMoveDistanceM: Double = 250.0
    private var policyMinRefreshIntervalMs: Long = 10_000L

    private val requestLocationPerms = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        val fine = granted[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarse = granted[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fine || coarse) {
            enableMyLocationAndCenter()
        } else {
            centerAndFetch(4.7110, -74.0721) // Bogotá fallback
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Ensure ServiceLocator has context (once per process is fine)
        ServiceLocator.init(requireContext().applicationContext)
        localStore = ServiceLocator.provideExperienceLocalStore(requireContext())

        // Load policy from DataStore (non-blocking)
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            runCatching { localStore.readPolicyMeta() }
                .onSuccess { meta ->
                    policyLoaded = true
                    policyAutoRefreshEnabled = meta.autoRefreshEnabled
                    policyMoveDistanceM = meta.moveDistanceM
                    policyMinRefreshIntervalMs = meta.refreshMinIntervalMs
                }
                .onFailure {
                    policyLoaded = true // keep defaults
                }
        }

        // OSMDroid init
        Configuration.getInstance().userAgentValue = requireContext().packageName

        osmdroidMap = binding.mapViewMap.apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setBuiltInZoomControls(false)
            setMultiTouchControls(true)
            controller.setZoom(12.0)
        }

        binding.toolbarMap.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        // Go-to-my-location button
        binding.btnMyLocationMap.setOnClickListener { goToMyLocation() }

        // Tap anywhere on the map (not a marker) to close the info card
        val tapToHideOverlay = object : Overlay() {
            override fun onSingleTapConfirmed(e: MotionEvent, mapView: MapView?): Boolean {
                if (binding.infoCardMap.isVisible) {
                    hideInfo()
                    return true
                }
                return false
            }
        }
        binding.mapViewMap.overlays.add(0, tapToHideOverlay)

        viewLifecycleOwner.lifecycleScope.launch {
            viewModelMap.state.collectLatest { st ->
                binding.progressMap.isVisible = st.isLoading
                renderMarkers(st.items)
                if (selectedId != null && st.items.none { it.id == selectedId }) hideInfo()
                st.error?.let { msg -> showError(msg) }
            }
        }

        ensureLocationPermissionThenLoad()
    }

    // ----- permissions & location -----

    private fun hasLocationPermission(): Boolean {
        val ctx = requireContext()
        val fine = ContextCompat.checkSelfPermission(
            ctx,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            ctx,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    private fun ensureLocationPermissionThenLoad() {
        if (hasLocationPermission()) {
            enableMyLocationAndCenter()
        } else {
            requestLocationPerms.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableMyLocationAndCenter() {
        if (!hasLocationPermission()) {
            centerAndFetch(4.7110, -74.0721)
            return
        }

        try {
            val fused = LocationServices.getFusedLocationProviderClient(requireContext())
            fused.lastLocation
                .addOnSuccessListener { loc ->
                    val lat = loc?.latitude ?: 4.7110
                    val lng = loc?.longitude ?: -74.0721
                    centerAndFetch(lat, lng)
                }
                .addOnFailureListener {
                    centerAndFetch(4.7110, -74.0721)
                }
        } catch (_: SecurityException) {
            centerAndFetch(4.7110, -74.0721)
        }

        if (myLocationOverlay == null && hasLocationPermission()) {
            try {
                myLocationOverlay = MyLocationNewOverlay(
                    GpsMyLocationProvider(requireContext()),
                    binding.mapViewMap
                ).apply {
                    enableMyLocation()
                }
                binding.mapViewMap.overlays.add(myLocationOverlay)
                binding.mapViewMap.invalidate()
            } catch (_: SecurityException) { /* ignore */ }
        }
    }

    private fun centerAndFetch(lat: Double, lng: Double) {
        // Seed movement anchors
        lastRefreshLat = lat
        lastRefreshLng = lng
        lastRefreshAtMs = System.currentTimeMillis()

        // Persist anchors to DataStore (best-effort)
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                localStore.updatePolicyMeta { m ->
                    m.copy(
                        lastNearestRefreshMs = System.currentTimeMillis(),
                        lastLocationLat = lat,
                        lastLocationLng = lng
                    )
                }
            }
        }

        // Nudge user marker (overlay) and recenter map
        nudgeUserMarker()
        osmdroidMap?.controller?.setCenter(GeoPoint(lat, lng))

        // Policy-aware fetch
        loadNearestPolicyAware(lat, lng, topK = 5)
    }

    private fun nudgeUserMarker() {
        myLocationOverlay?.apply {
            if (!isMyLocationEnabled) enableMyLocation()
        }
        osmdroidMap?.invalidate()
    }

    // Smooth zoom + pan helper
    private fun zoomAndCenter(point: GeoPoint, zoom: Double = 16.0) {
        osmdroidMap?.controller?.apply {
            setZoom(zoom)
            animateTo(point)
        }
        nudgeUserMarker()
    }

    @SuppressLint("MissingPermission")
    private fun goToMyLocation() {
        if (!hasLocationPermission()) {
            ensureLocationPermissionThenLoad()
            return
        }
        val fused = LocationServices.getFusedLocationProviderClient(requireContext())
        fused.lastLocation
            .addOnSuccessListener { loc ->
                val point = if (loc != null) GeoPoint(loc.latitude, loc.longitude)
                else myLocationOverlay?.myLocation ?: GeoPoint(4.7110, -74.0721)
                zoomAndCenter(point, zoom = 16.0)
            }
            .addOnFailureListener {
                val fallback = myLocationOverlay?.myLocation ?: GeoPoint(4.7110, -74.0721)
                zoomAndCenter(fallback, zoom = 16.0)
                Snackbar.make(requireView(), "Location unavailable", Snackbar.LENGTH_SHORT).show()
            }
    }

    // ----- policy-aware nearest (online/offline) -----

    private fun bucketKeyFor(lat: Double, lng: Double): String =
        "${"%.2f".format(lat)}_${"%.2f".format(lng)}"

    private fun loadNearestPolicyAware(lat: Double, lng: Double, topK: Int) {
        val ctx = requireContext()
        val repo = ServiceLocator.experiencesRepository
        val local = localStore
        val localSync = MapLocalSync(local)

        viewModelMap.setLoading(true)

        viewLifecycleOwner.lifecycleScope.launch {
            val online = NetworkMonitor.isOnline(ctx)
            if (online) {
                // ONLINE: remote → show → persist (Room + KV bucket + DataStore anchors)
                runCatching {
                    val list = withContext(Dispatchers.IO) { repo.getNearest(lat, lng, topK) }
                    viewModelMap.setItems(list)
                    launch(Dispatchers.IO) {
                        localSync.persistFromRemote(
                            items = list,
                            currentLat = lat,
                            currentLng = lng
                        )
                        local.updatePolicyMeta { m ->
                            m.copy(
                                lastNearestRefreshMs = System.currentTimeMillis(),
                                lastLocationLat = lat,
                                lastLocationLng = lng
                            )
                        }
                    }
                }.onFailure { e ->
                    tryLocalFallback(local, lat, lng, topK, "Remote fetch failed: ${e.message}")
                }
            } else {
                // OFFLINE: bucket → Room; else nearest from Room; else dialog
                tryLocalFallback(local, lat, lng, topK, OFFLINE_MSG)
            }
        }
    }

    private suspend fun tryLocalFallback(
        local: ExperienceLocalStore,
        lat: Double,
        lng: Double,
        topK: Int,
        @Suppress("UNUSED_PARAMETER") message: String
    ) {
        val bKey = bucketKeyFor(lat, lng)
        val ids = runCatching { local.getBucketTopIds(bKey) }.getOrDefault(emptyList())

        val fromBucket = if (ids.isNotEmpty()) {
            runCatching { local.getByIds(ids).take(topK) }.getOrDefault(emptyList())
        } else emptyList()

        if (fromBucket.isNotEmpty()) {
            viewModelMap.setItems(fromBucket)
            return
        }

        val fromRoomNearest = runCatching { local.getNearest(lat, lng, topK) }.getOrDefault(emptyList())
        if (fromRoomNearest.isNotEmpty()) {
            viewModelMap.setItems(fromRoomNearest)
            return
        }

        viewModelMap.setItems(emptyList())
        viewModelMap.setError(OFFLINE_MSG)
    }

    // ----- error UI -----

    private fun showError(msg: String) {
        if (msg == OFFLINE_MSG) {
            if (!hasShownOfflineDialog && isAdded) {
                hasShownOfflineDialog = true
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("No connection")
                    .setMessage(OFFLINE_MSG)
                    .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                    .setOnDismissListener { viewModelMap.clearError() }
                    .show()
            } else {
                viewModelMap.clearError()
            }
        } else {
            Snackbar.make(requireView(), msg, Snackbar.LENGTH_LONG).show()
            viewModelMap.clearError()
        }
    }

    // ----- movement-based auto refresh (now: online OR offline) -----

    private fun distanceMeters(aLat: Double, aLng: Double, bLat: Double, bLng: Double): Double {
        val R = 6371000.0
        val dLat = Math.toRadians(bLat - aLat)
        val dLng = Math.toRadians(bLng - aLng)
        val sa = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(aLat)) *
                cos(Math.toRadians(bLat)) *
                sin(dLng / 2) * sin(dLng / 2)
        val c = 2 * atan2(sqrt(sa), sqrt(1 - sa))
        return R * c
    }

    @SuppressLint("MissingPermission")
    private fun startMoveUpdates() {
        if (!policyAutoRefreshEnabled) return
        if (!hasLocationPermission()) return

        val req = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 5000L)
            .setMinUpdateIntervalMillis(3000L)
            .setMinUpdateDistanceMeters(0f)
            .build()

        if (moveCallback == null) {
            moveCallback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    val loc: Location = result.lastLocation ?: return
                    val now = System.currentTimeMillis()

                    // always keep the user marker fresh
                    nudgeUserMarker()

                    // Guard: min time interval
                    if (now - lastRefreshAtMs < policyMinRefreshIntervalMs) return

                    val lastLat = lastRefreshLat
                    val lastLng = lastRefreshLng

                    // First time: seed anchors and persist, do nothing else (initial fetch already ran)
                    if (lastLat == null || lastLng == null) {
                        lastRefreshLat = loc.latitude
                        lastRefreshLng = loc.longitude
                        lastRefreshAtMs = now
                        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                            runCatching {
                                localStore.updatePolicyMeta { m ->
                                    m.copy(
                                        lastNearestRefreshMs = now,
                                        lastLocationLat = loc.latitude,
                                        lastLocationLng = loc.longitude
                                    )
                                }
                            }
                        }
                        return
                    }

                    // Distance guard
                    val d = distanceMeters(lastLat, lastLng, loc.latitude, loc.longitude)
                    if (d < policyMoveDistanceM) return

                    // Update anchors BEFORE fetch
                    lastRefreshLat = loc.latitude
                    lastRefreshLng = loc.longitude
                    lastRefreshAtMs = now
                    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                        runCatching {
                            localStore.updatePolicyMeta { m ->
                                m.copy(
                                    lastNearestRefreshMs = now,
                                    lastLocationLat = loc.latitude,
                                    lastLocationLng = loc.longitude
                                )
                            }
                        }
                    }

                    // Branch by connectivity:
                    if (NetworkMonitor.isOnline(requireContext())) {
                        // Online: remote + persist (center map softly)
                        osmdroidMap?.controller?.setCenter(GeoPoint(loc.latitude, loc.longitude))
                        loadNearestPolicyAware(loc.latitude, loc.longitude, topK = 5)
                    } else {
                        // Offline: recompute locally from Room automatically
                        osmdroidMap?.controller?.setCenter(GeoPoint(loc.latitude, loc.longitude))
                        viewLifecycleOwner.lifecycleScope.launch {
                            val localNearest = withContext(Dispatchers.IO) {
                                localStore.getNearest(loc.latitude, loc.longitude, 5)
                            }
                            viewModelMap.setItems(localNearest)
                        }
                    }
                }
            }
        }

        try {
            val fused = LocationServices.getFusedLocationProviderClient(requireContext())
            fused.requestLocationUpdates(req, moveCallback!!, requireActivity().mainLooper)
        } catch (_: SecurityException) {
            // Permission missing at runtime → ignore
        }
    }

    private fun stopMoveUpdates() {
        moveCallback?.let {
            val fused = LocationServices.getFusedLocationProviderClient(requireContext())
            fused.removeLocationUpdates(it)
        }
    }

    // ----- markers & selection -----

    private fun renderMarkers(items: List<ExperienceDtoMap>) {
        val map = osmdroidMap ?: return

        val keepIds = items.mapNotNull { it.id }.toSet()
        val toRemove = markersById.keys - keepIds
        toRemove.forEach { id ->
            markersById[id]?.let { map.overlays.remove(it) }
            markersById.remove(id)
        }

        items.forEach { dto ->
            val id = dto.id ?: return@forEach
            val lat = dto.latitude ?: return@forEach
            val lng = dto.longitude ?: return@forEach
            val point = GeoPoint(lat, lng)

            val existing = markersById[id]
            val marker = existing ?: Marker(map).also { m ->
                m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                m.setOnMarkerClickListener { _, _ ->
                    selectDto(dto)
                    true
                }
                map.overlays.add(m)
                markersById[id] = m
            }

            marker.position = point
            marker.title = dto.title ?: "Experience"
            marker.icon = if (id == selectedId) selectedPinDrawable() else defaultPinDrawable()
        }

        bringCardToFront()
        map.invalidate()
    }

    private fun selectDto(dto: ExperienceDtoMap) {
        selectedId = dto.id

        binding.tvTitleMap.text = dto.title ?: "Experience"
        binding.tvRatingMap.text = String.format("%.1f", dto.avgRating ?: 0.0)
        binding.tvLocationMap.text = dto.department ?: "Colombia"

        binding.tvHostMap.text = dto.hostName ?: "Host"
        binding.ivVerifiedMap.isVisible = dto.hostVerified == true

        val learn = if (dto.skillsToLearn.isNotEmpty()) dto.skillsToLearn.joinToString(", ") else "—"
        val teach = if (dto.skillsToTeach.isNotEmpty()) dto.skillsToTeach.joinToString(", ") else "—"
        binding.tvLearnMap.text = learn
        binding.tvTeachMap.text = teach

        updateAllMarkerIcons()
        osmdroidMap?.invalidate()
        osmdroidMap?.postInvalidate()

        bringCardToFront()
        binding.infoCardMap.isGone = false
    }

    private fun hideInfo() {
        selectedId = null
        binding.infoCardMap.isGone = true
        updateAllMarkerIcons()
        osmdroidMap?.invalidate()
    }

    private fun updateAllMarkerIcons() {
        val map = osmdroidMap ?: return
        markersById.forEach { (id, marker) ->
            marker.icon = null
            marker.icon = if (id == selectedId) selectedPinDrawable() else defaultPinDrawable()
        }
        map.postInvalidate()
    }

    private fun defaultPinDrawable() =
        ContextCompat.getDrawable(requireContext(), R.drawable.ic_map_pin_map)?.mutate()

    private fun selectedPinDrawable() =
        ContextCompat.getDrawable(requireContext(), R.drawable.ic_map_pin_map_selected)?.mutate()

    private fun bringCardToFront() {
        binding.infoCardMap.bringToFront()
        (binding.infoCardMap.parent as? View)?.requestLayout()
        binding.infoCardMap.invalidate()
    }

    override fun onResume() {
        super.onResume()
        if (!hasLoggedUsage) {
            hasLoggedUsage = true
            ServiceLocator.incrementFeatureUsage("map_feature")
        }
        binding.mapViewMap.onResume()
        // Start movement updates once policy is loaded
        if (policyLoaded) startMoveUpdates() else {
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                var spins = 0
                while (!policyLoaded && spins < 10) {
                    kotlinx.coroutines.delay(50)
                    spins++
                }
                withContext(Dispatchers.Main) { startMoveUpdates() }
            }
        }
        // keep user marker live after returning
        nudgeUserMarker()
    }

    override fun onPause() {
        super.onPause()
        stopMoveUpdates()
        binding.mapViewMap.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        osmdroidMap = null
        myLocationOverlay = null
        markersById.clear()
        hasShownOfflineDialog = false
        moveCallback = null
    }
}
