package com.example.kotlinview.ui.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
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
import com.example.kotlinview.core.ServiceLocator
import com.example.kotlinview.data.map.ExperienceDtoMap
import com.example.kotlinview.databinding.FragmentMapMapBinding
import com.google.android.gms.location.LocationServices
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

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

    // Log usage once per fragment lifecycle
    private var hasLoggedUsage = false

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
        // OSMDroid init
        Configuration.getInstance().userAgentValue = requireContext().packageName

        osmdroidMap = binding.mapViewMap.apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setBuiltInZoomControls(false) // pinch to zoom only
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
                st.error?.let { msg ->
                    Snackbar.make(requireView(), msg, Snackbar.LENGTH_LONG).show()
                    viewModelMap.clearError()
                }
            }
        }

        ensureLocationPermissionThenLoad()
    }

    // ----- permissions & location -----

    private fun hasLocationPermission(): Boolean {
        val ctx = requireContext()
        val fine = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
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
                    // don't auto-follow; we center manually on button press
                }
                binding.mapViewMap.overlays.add(myLocationOverlay)
                binding.mapViewMap.invalidate()
            } catch (_: SecurityException) { /* ignore */ }
        }
    }

    private fun centerAndFetch(lat: Double, lng: Double) {
        osmdroidMap?.controller?.setCenter(GeoPoint(lat, lng))
        // Keep top-5 nearest (your current choice)
        viewModelMap.fetchNearest(lat, lng, 5)
    }

    // Smooth zoom + pan helper
    private fun zoomAndCenter(point: GeoPoint, zoom: Double = 16.0) {
        osmdroidMap?.controller?.apply {
            setZoom(zoom)          // set desired zoom level first
            animateTo(point)       // then animate the pan to the target
        }
        osmdroidMap?.invalidate()
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

                // Zoom IN to the pin (not just pan)
                zoomAndCenter(point, zoom = 16.0)
            }
            .addOnFailureListener {
                val fallback = myLocationOverlay?.myLocation ?: GeoPoint(4.7110, -74.0721)
                zoomAndCenter(fallback, zoom = 16.0)
                Snackbar.make(requireView(), "Location unavailable", Snackbar.LENGTH_SHORT).show()
            }
    }

    // ----- markers & selection -----

    private fun renderMarkers(items: List<ExperienceDtoMap>) {
        val map = osmdroidMap ?: return

        val keepIds = items.map { it.id }.toSet()
        val toRemove = markersById.keys - keepIds
        toRemove.forEach { id ->
            markersById[id]?.let { map.overlays.remove(it) }
            markersById.remove(id)
        }

        items.forEach { dto ->
            val lat = dto.latitude ?: return@forEach
            val lng = dto.longitude ?: return@forEach
            val point = GeoPoint(lat, lng)

            val existing = markersById[dto.id]
            val marker = existing ?: Marker(map).also { m ->
                m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                m.setOnMarkerClickListener { _, _ ->
                    selectDto(dto)
                    true
                }
                map.overlays.add(m)
                markersById[dto.id] = m
            }

            marker.position = point
            marker.title = dto.title ?: "Experience"
            marker.icon = if (dto.id == selectedId) selectedPinDrawable() else defaultPinDrawable()
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

        // Immediate visual update of marker icon
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
    }

    override fun onPause() {
        super.onPause()
        binding.mapViewMap.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        osmdroidMap = null
        myLocationOverlay = null
        markersById.clear()
    }
}
