package com.example.kotlinview.ui.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
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

    private val requestLocationPerms = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        val fine = granted[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarse = granted[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fine || coarse) enableMyLocationAndCenter()
        else centerAndFetch(4.7110, -74.0721) // Bogotá fallback
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMapMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        Configuration.getInstance().userAgentValue = requireContext().packageName

        osmdroidMap = binding.mapViewMap.apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setBuiltInZoomControls(false) // only our buttons
            setMultiTouchControls(true)   // pan & pinch
            controller.setZoom(12.0)
        }

        // keep UI simple; prototypes hidden in XML already
        binding.toolbarMap.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        binding.btnZoomInMap.setOnClickListener { osmdroidMap?.controller?.zoomIn() }
        binding.btnZoomOutMap.setOnClickListener { osmdroidMap?.controller?.zoomOut() }
        binding.btnCloseInfoMap.setOnClickListener { hideInfo() }

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
                    enableFollowLocation()
                }
                binding.mapViewMap.overlays.add(myLocationOverlay)
                binding.mapViewMap.invalidate()
            } catch (_: SecurityException) { /* ignore */ }
        }
    }

    private fun centerAndFetch(lat: Double, lng: Double) {
        osmdroidMap?.controller?.setCenter(GeoPoint(lat, lng))
        viewModelMap.fetchNearest(lat, lng, 20)
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
            marker.icon = tintedPinDrawable(
                if (dto.id == selectedId) R.color.british_racing_green else R.color.rufous
            )
        }

        bringCardToFront()
        map.invalidate()
    }

    private fun selectDto(dto: ExperienceDtoMap) {
        selectedId = dto.id

        binding.tvTitleMap.text = dto.title ?: "Experience"
        binding.tvRatingMap.text = String.format("%.1f", dto.avgRating ?: 0.0)
        binding.tvLocationMap.text = dto.department ?: "Colombia"

        // host
        binding.tvHostMap.text = dto.hostName ?: "Host"
        binding.ivVerifiedMap.isVisible = dto.hostVerified == true

        // skills
        val learn = if (dto.skillsToLearn.isNotEmpty()) dto.skillsToLearn.joinToString(", ") else "—"
        val teach = if (dto.skillsToTeach.isNotEmpty()) dto.skillsToTeach.joinToString(", ") else "—"
        binding.tvLearnMap.text = "Learn: $learn"
        binding.tvTeachMap.text = "Teach: $teach"

        updateAllMarkerTints()
        bringCardToFront()
        binding.infoCardMap.isGone = false
    }

    private fun hideInfo() {
        selectedId = null
        binding.infoCardMap.isGone = true
        updateAllMarkerTints()
        osmdroidMap?.invalidate()
    }

    private fun updateAllMarkerTints() {
        markersById.forEach { (id, marker) ->
            marker.icon = tintedPinDrawable(
                if (id == selectedId) R.color.british_racing_green else R.color.rufous
            )
        }
    }

    private fun tintedPinDrawable(colorRes: Int) =
        ContextCompat.getDrawable(requireContext(), R.drawable.ic_map_pin_map)?.mutate()?.also { d ->
            DrawableCompat.setTint(d, requireContext().getColor(colorRes))
        }

    private fun bringCardToFront() {
        binding.infoCardMap.bringToFront()
        (binding.infoCardMap.parent as? View)?.requestLayout()
        binding.infoCardMap.invalidate()
    }

    // (grid helpers removed)

    private fun getColorAWithAlpha(colorRes: Int, alpha: Float): Int {
        val base = requireContext().getColor(colorRes)
        val a = (alpha * 255).toInt().coerceIn(0, 255)
        val r = (base shr 16) and 0xFF
        val g = (base shr 8) and 0xFF
        val b = base and 0xFF
        return Color.argb(a, r, g, b)
    }

    override fun onResume() {
        super.onResume()
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
