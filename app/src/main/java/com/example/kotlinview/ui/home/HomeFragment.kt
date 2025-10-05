package com.example.kotlinview.ui.home

import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.kotlinview.core.ServiceLocator
import com.example.kotlinview.databinding.FragmentHomeBinding
import java.util.Date
import java.util.UUID
import kotlin.random.Random

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: ExperienceAdapter
    private val isHostUser: Boolean = false

    private val items = mutableListOf<Experience>()
    private var loadingMore = false
    private var page = 0
    private val pageSize = 8

    // Debounce for feature usage increment
    private var lastFeaturePingMs = 0L

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)

        setupHeader()
        setupRecycler()
        loadMore()

        return binding.root
    }

    override fun onResume() {
        super.onResume()
        maybeIncrementUsage()
    }

    private fun maybeIncrementUsage() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastFeaturePingMs > 3000) {
            ServiceLocator.incrementFeatureUsage("experience_catalogue_feature")
            lastFeaturePingMs = now
        }
    }

    private fun setupHeader() {
        binding.btnMyExperiences.isVisible = isHostUser

        binding.btnFilters.setOnClickListener {
            val sheet = FilterBottomSheet.newInstance()
            sheet.onApply = { opts ->
                Toast.makeText(
                    requireContext(),
                    "Applied: ${opts.activityTypes.joinToString()} / ${opts.duration ?: "-"} / ${opts.location}",
                    Toast.LENGTH_SHORT
                ).show()
                // TODO: apply filters to real datasource / ViewModel
            }
            sheet.onClear = {
                Toast.makeText(requireContext(), "Cleared filters", Toast.LENGTH_SHORT).show()
            }
            sheet.show(childFragmentManager, "filters")
        }

        binding.btnMapView.setOnClickListener {
            Toast.makeText(requireContext(), "Map View (TODO)", Toast.LENGTH_SHORT).show()
        }

        binding.etSearch.setOnEditorActionListener { v, _, _ ->
            Toast.makeText(requireContext(), "Search: ${v.text}", Toast.LENGTH_SHORT).show()
            true
        }
    }

    private fun setupRecycler() {
        adapter = ExperienceAdapter { exp ->
            Toast.makeText(requireContext(), "Selected: ${exp.title}", Toast.LENGTH_SHORT).show()
        }
        binding.rvExperiences.layoutManager = LinearLayoutManager(requireContext())
        binding.rvExperiences.adapter = adapter

        binding.rvExperiences.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0) return
                val lm = rv.layoutManager as LinearLayoutManager
                val lastVisible = lm.findLastVisibleItemPosition()
                val total = adapter.itemCount
                if (!loadingMore && lastVisible >= total - 3) {
                    loadMore()
                }
            }
        })
    }

    private fun loadMore() {
        loadingMore = true
        val newItems = mockPage(page, pageSize)
        items.addAll(newItems)
        adapter.submitList(items.toList())
        page += 1
        loadingMore = false
    }

    private fun mockPage(page: Int, size: Int): List<Experience> {
        val base = listOf(
            Experience(
                id = "1", title = "Learn Coffee Harvesting & Teach Photography",
                hostName = "Carlos Mendoza", location = "Tolima, Colombia",
                verified = true, rating = 4.9, reviewCount = 18,
                description = "Experience traditional coffee harvesting...",
                duration = "2 days", activityType = "Agriculture & Arts",
                skills = listOf("Coffee Production", "Traditional Farming"),
                teachingSkills = listOf("Photography Techniques", "Digital Editing"),
                learningSkills = listOf("Coffee Harvesting", "Traditional Farming Methods"),
                accessibility = false, availableDates = listOf(Date(), Date())
            ),
            Experience(
                id = "2", title = "Fishing on Magdalena River & Practice English",
                hostName = "María Gutierrez", location = "Honda, Colombia",
                verified = true, rating = 4.7, reviewCount = 12,
                description = "Learn traditional river fishing techniques...",
                duration = "4 hours", activityType = "Outdoor & Language",
                skills = listOf("River Fishing", "Local History"),
                teachingSkills = listOf("English Conversation", "Basic Grammar"),
                learningSkills = listOf("Traditional Fishing", "River Navigation"),
                accessibility = true, availableDates = listOf(Date(), Date())
            ),
            Experience(
                id = "3", title = "Traditional Arepa Cooking & Learn Web Design",
                hostName = "Ana Sofia Rodriguez", location = "Medellín, Colombia",
                verified = false, rating = 4.8, reviewCount = 24,
                description = "Master the art of making authentic Colombian arepas...",
                duration = "3 hours", activityType = "Cooking & Technology",
                skills = listOf("Traditional Recipes", "Colombian Cuisine"),
                teachingSkills = listOf("Web Design Basics", "HTML/CSS Introduction"),
                learningSkills = listOf("Arepa Making", "Traditional Cooking Methods"),
                accessibility = true, availableDates = listOf(Date(), Date())
            ),
            Experience(
                id = "4", title = "Coffee Farm Tour & Language Exchange",
                hostName = "Diego Herrera", location = "Armenia, Colombia",
                verified = true, rating = 4.6, reviewCount = 31,
                description = "Explore a working coffee farm...",
                duration = "5 hours", activityType = "Agriculture & Language",
                skills = listOf("Coffee Production", "Sustainable Farming"),
                teachingSkills = listOf("English/Spanish Exchange", "Cultural Exchange"),
                learningSkills = listOf("Coffee Processing", "Farm Management"),
                accessibility = false, availableDates = listOf(Date(), Date())
            ),
            Experience(
                id = "5", title = "Handicraft Workshop & Digital Marketing",
                hostName = "Isabella Torres", location = "Cartagena, Colombia",
                verified = true, rating = 4.9, reviewCount = 16,
                description = "Create traditional Wayuu bags...",
                duration = "6 hours", activityType = "Crafts & Business",
                skills = listOf("Traditional Handicrafts", "Wayuu Techniques"),
                teachingSkills = listOf("Digital Marketing", "Social Media Strategy"),
                learningSkills = listOf("Handicraft Making", "Cultural Traditions"),
                accessibility = true, availableDates = listOf(Date(), Date())
            ),
            Experience(
                id = "6", title = "Salsa Dancing & Spanish Conversation",
                hostName = "Luis Rodriguez", location = "Cali, Colombia",
                verified = true, rating = 4.7, reviewCount = 22,
                description = "Learn authentic salsa dancing steps...",
                duration = "3 hours", activityType = "Dance & Language",
                skills = listOf("Salsa Dancing", "Colombian Culture"),
                teachingSkills = listOf("English Conversation", "Travel Tips"),
                learningSkills = listOf("Salsa Dancing", "Spanish Language"),
                accessibility = true, availableDates = listOf(Date(), Date())
            ),
            Experience(
                id = "7", title = "Emerald Mining & Geology Lessons",
                hostName = "Pedro Vargas", location = "Boyacá, Colombia",
                verified = true, rating = 4.5, reviewCount = 14,
                description = "Discover the world of emerald mining...",
                duration = "1 day", activityType = "Mining & Education",
                skills = listOf("Emerald Mining", "Local Geology"),
                teachingSkills = listOf("Geological Science", "Sustainable Practices"),
                learningSkills = listOf("Traditional Mining", "Gemstone Identification"),
                accessibility = false, availableDates = listOf(Date(), Date())
            ),
            Experience(
                id = "8", title = "Beach Cleanup & Marine Conservation",
                hostName = "Sofia Martinez", location = "Santa Marta, Colombia",
                verified = true, rating = 4.8, reviewCount = 19,
                description = "Help protect our coastline...",
                duration = "4 hours", activityType = "Environmental & Education",
                skills = listOf("Marine Conservation", "Environmental Protection"),
                teachingSkills = listOf("Environmental Science", "Conservation Methods"),
                learningSkills = listOf("Beach Conservation", "Marine Ecosystem"),
                accessibility = true, availableDates = listOf(Date(), Date())
            )
        )

        return base.shuffled().map {
            it.copy(
                id = UUID.randomUUID().toString(),
                rating = listOf(4.5, 4.6, 4.7, 4.8, 4.9)[Random.nextInt(5)],
                reviewCount = it.reviewCount + page * 3
            )
        }.take(size)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
