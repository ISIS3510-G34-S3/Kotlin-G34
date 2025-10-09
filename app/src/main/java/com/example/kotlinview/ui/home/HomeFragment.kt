package com.example.kotlinview.ui.home

import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.kotlinview.core.ServiceLocator
import com.example.kotlinview.databinding.FragmentHomeBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: ExperienceAdapter
    private val isHostUser: Boolean = false

    // Debounce for feature usage increment
    private var lastFeaturePingMs = 0L

    private val vm: HomeViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)

        setupHeader()
        setupRecycler()

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        vm.loadFeed(limit = 20)

        viewLifecycleOwner.lifecycleScope.launch {
            vm.state.collectLatest { st ->
                binding.progress.isVisible = st.loading

                st.error?.let {
                    Toast.makeText(requireContext(), "Error loading feed", Toast.LENGTH_SHORT).show()
                }
                adapter.submitList(st.items)
            }
        }
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
                // TODO: aplicar filtros reales en el ViewModel (cuando se implemente)
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
            // Aquí podrías navegar a detalle si lo tienes:
            // findNavController().navigate(R.id.action_home_to_detail, bundleOf("id" to exp.id))
        }
        binding.rvExperiences.layoutManager = LinearLayoutManager(requireContext())
        binding.rvExperiences.adapter = adapter
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

