package com.example.kotlinview.ui.map

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.example.kotlinview.R
import com.example.kotlinview.databinding.FragmentMapMapBinding

data class MapExperienceMap(
    val id: String,
    val title: String,
    val hostName: String,
    val location: String,
    val verified: Boolean,
    val rating: Double,
    val reviewCount: Int,
    val teachingSkills: List<String>,
    val learningSkills: List<String>
)

class MapFragmentMap : Fragment() {

    private var _binding: FragmentMapMapBinding? = null
    private val binding get() = _binding!!

    private val mockExperiences = listOf(
        MapExperienceMap(
            id = "1",
            title = "Learn Coffee Harvesting & Teach Photography",
            hostName = "Carlos Mendoza",
            location = "Tolima, Colombia",
            verified = true,
            rating = 4.9,
            reviewCount = 18,
            teachingSkills = listOf("Photography Techniques", "Digital Editing"),
            learningSkills = listOf("Coffee Harvesting", "Traditional Farming Methods")
        ),
        MapExperienceMap(
            id = "2",
            title = "Fishing on Magdalena River & Practice English",
            hostName = "María Gutierrez",
            location = "Honda, Colombia",
            verified = true,
            rating = 4.7,
            reviewCount = 12,
            teachingSkills = listOf("English Conversation", "Basic Grammar"),
            learningSkills = listOf("Traditional Fishing", "River Navigation")
        ),
        MapExperienceMap(
            id = "3",
            title = "Traditional Arepa Cooking & Learn Web Design",
            hostName = "Ana Sofia Rodriguez",
            location = "Medellín, Colombia",
            verified = false,
            rating = 4.8,
            reviewCount = 24,
            teachingSkills = listOf("Web Design Basics", "HTML/CSS Introduction"),
            learningSkills = listOf("Arepa Making", "Traditional Cooking Methods")
        )
    )

    private var selected: MapExperienceMap? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMapMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // back
        binding.toolbarMap.setNavigationOnClickListener { requireActivity().onBackPressed() }

        // grid overlay (8x8 thin lines, light ash-gray)
        addGridOverlay(binding.gridOverlayMap, rows = 8, cols = 8)

        // pins
        binding.pin1Map.setOnClickListener { select(mockExperiences[0]) }
        binding.pin2Map.setOnClickListener { select(mockExperiences[1]) }
        binding.pin3Map.setOnClickListener { select(mockExperiences[2]) }

        // zoom (mock)
        binding.ivZoomInMap?.setOnClickListener { /* no-op */ }
        binding.ivZoomOutMap?.setOnClickListener { /* no-op */ }

        // close button
        binding.btnCloseInfoMap.setOnClickListener { hideInfo() }

        // tapping outside (background/grid) closes the card
        binding.mapBgMap.setOnClickListener { hideInfo() }
        binding.gridOverlayMap.setOnClickListener { hideInfo() }

        // details
        binding.btnViewDetailsMap.setOnClickListener {
            selected?.let {
                // TODO: navigate to details if/when ready
            }
        }
    }

    private fun hideInfo() {
        selected = null
        // reset pins to default (rufous)
        val rufous = ColorStateList.valueOf(requireContext().getColor(R.color.rufous))
        listOf(binding.pin1Map, binding.pin2Map, binding.pin3Map).forEach { it.backgroundTintList = rufous }
        binding.infoCardMap.isGone = true
    }

    private fun bringCardToFront() {
        binding.infoCardMap.bringToFront()
        binding.infoCardMap.parent?.let { (it as View).requestLayout() }
        binding.infoCardMap.invalidate()
    }


    private fun addGridOverlay(container: FrameLayout, rows: Int, cols: Int) {
        container.removeAllViews()

        // vertical lines
        for (c in 1 until cols) {
            val v = View(requireContext())
            v.setBackgroundColor(getColorAWithAlpha(R.color.ash_gray, alpha = 0.25f))
            container.addView(v, FrameLayout.LayoutParams(1, FrameLayout.LayoutParams.MATCH_PARENT))
            v.post {
                val x = (container.width * (c / cols.toFloat())).toInt()
                v.translationX = x.toFloat()
            }
        }
        // horizontal lines
        for (r in 1 until rows) {
            val h = View(requireContext())
            h.setBackgroundColor(getColorAWithAlpha(R.color.ash_gray, alpha = 0.25f))
            container.addView(h, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, 1))
            h.post {
                val y = (container.height * (r / rows.toFloat())).toInt()
                h.translationY = y.toFloat()
            }
        }
    }

    private fun getColorAWithAlpha(colorRes: Int, alpha: Float): Int {
        val base = requireContext().getColor(colorRes)
        val a = (alpha * 255).toInt().coerceIn(0, 255)
        val r = (base shr 16) and 0xFF
        val g = (base shr 8) and 0xFF
        val b = base and 0xFF
        return Color.argb(a, r, g, b)
    }

    private fun select(exp: MapExperienceMap) {
        selected = exp

        val rufous = ColorStateList.valueOf(requireContext().getColor(R.color.rufous))
        val selectedTint = ColorStateList.valueOf(requireContext().getColor(R.color.british_racing_green))
        listOf(binding.pin1Map, binding.pin2Map, binding.pin3Map).forEach { it.backgroundTintList = rufous }
        when (exp.id) {
            "1" -> binding.pin1Map.backgroundTintList = selectedTint
            "2" -> binding.pin2Map.backgroundTintList = selectedTint
            "3" -> binding.pin3Map.backgroundTintList = selectedTint
        }

        binding.tvTitleMap.text = exp.title
        binding.tvRatingMap.text = "%.1f".format(exp.rating)
        binding.tvHostMap.text = exp.hostName
        binding.ivVerifiedMap.isVisible = exp.verified
        binding.tvLocationMap.text = exp.location
        binding.tvLearnMap.text = "Learn: ${exp.learningSkills.joinToString(", ")}"
        binding.tvTeachMap.text = "Teach: ${exp.teachingSkills.joinToString(", ")}"

        bringCardToFront()

        binding.infoCardMap.isGone = false
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
