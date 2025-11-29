package com.example.kotlinview.ui.home

import android.os.Bundle
import android.os.SystemClock
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
import com.example.kotlinview.R
import android.view.ViewGroup.LayoutParams
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Network
import android.content.Context
import androidx.core.os.bundleOf
import androidx.navigation.fragment.findNavController
import com.example.kotlinview.ui.detail.ExperienceDetailFragment

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: ExperienceAdapter
    private val isHostUser: Boolean = false

    // Debounce for feature usage increment
    private var lastFeaturePingMs = 0L

    private val vm: HomeViewModel by viewModels()

    private var connectivityManager: ConnectivityManager? = null

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            // En cuanto vuelva la red, pedimos reintento inmediato
            activity?.runOnUiThread {
                vm.triggerImmediateRetry()
            }
        }
    }


    private var currentBanner: View? = null

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

        // Estado principal del feed
        viewLifecycleOwner.lifecycleScope.launch {
            vm.state.collectLatest { st ->
                binding.progress.isVisible = st.loading

                st.error?.let {
                    Toast.makeText(requireContext(), "Error loading feed", Toast.LENGTH_SHORT).show()
                }

                adapter.submitList(st.items) {
                    binding.rvExperiences.scrollToPosition(0)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            vm.bannerMessage.collectLatest { msg ->
                if (msg.isNullOrEmpty()) {
                    dismissBanner()
                } else {
                    showGradientBanner(msg)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        maybeIncrementUsage()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.rvExperiences.adapter = null
        _binding = null
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
                val dept = opts.location?.substringBefore(',')?.trim()
                vm.loadFeed(
                    limit = 20,
                    department = if (dept.isNullOrBlank()) null else dept,
                    startAtMs = opts.startAtMs,
                    endAtMs = opts.endAtMs
                )
            }
            sheet.onClear = {
                vm.loadFeed(limit = 20) // sin filtros
            }
            sheet.show(childFragmentManager, "filters")
        }

        binding.etSearch.setOnEditorActionListener { v, _, _ ->
            Toast.makeText(requireContext(), "Search: ${v.text}", Toast.LENGTH_SHORT).show()
            true
        }
    }

    private fun setupRecycler() {
        val lm = LinearLayoutManager(requireContext())
        binding.rvExperiences.layoutManager = lm

        adapter = ExperienceAdapter { exp ->
            val args = bundleOf(
                "experienceId"      to exp.id,
                "experienceTitle"   to exp.title,
                "hostName"          to exp.hostName,
                "department"        to exp.department,
                "duration"          to exp.duration,
                "pricePerPerson"    to exp.pricePerPerson,
                "imageUrl"          to exp.imageUrl
            )

            findNavController().navigate(
                R.id.experienceDetailFragment,
                args
            )
        }

        binding.rvExperiences.adapter = adapter
    }


    /* ===================== Conectivity ===================== */
    private fun isDeviceOnline(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun showGradientBanner(message: String) {

        dismissBanner()

        val contentRoot = requireActivity().findViewById<ViewGroup>(android.R.id.content)

        fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

        val outer = android.widget.FrameLayout(requireContext()).apply {
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                setColor(android.graphics.Color.TRANSPARENT)
                cornerRadius = dp(16).toFloat()
                setStroke(dp(2), android.graphics.Color.WHITE)
            }
            setPadding(dp(2), dp(2), dp(2), dp(2)) // para que se vea el borde
            elevation = 24f
            layoutParams = android.widget.FrameLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.CENTER
                val m = dp(24)
                setMargins(m, m, m, m)
            }

            isClickable = false
            isFocusable = false
            alpha = 0f
            scaleX = 0.98f
            scaleY = 0.98f
        }

        val inner = android.widget.FrameLayout(requireContext()).apply {
            setBackgroundResource(R.drawable.gradient_hero)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            minimumWidth = dp(280)
            isClickable = false
            isFocusable = false
        }

        val tv = android.widget.TextView(requireContext()).apply {
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
            setTextColor(android.graphics.Color.WHITE)
            text = message
            setLineSpacing(0f, 1.15f)
            setPadding(dp(4), dp(4), dp(4), dp(4))
            maxWidth = dp(360)
            isClickable = false
            isFocusable = false
        }

        inner.addView(tv)
        outer.addView(inner)
        contentRoot.addView(outer)
        currentBanner = outer

        // Animación de entrada
        outer.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(180)
            .start()
    }

    private fun dismissBanner() {
        val parent = currentBanner?.parent as? ViewGroup
        currentBanner?.animate()?.cancel()
        if (parent != null && currentBanner != null) {
            parent.removeView(currentBanner)
        }
        currentBanner = null
    }

    override fun onStart() {
        super.onStart()
        connectivityManager = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        connectivityManager?.registerDefaultNetworkCallback(networkCallback)
    }

    override fun onStop() {
        super.onStop()
        try {
            connectivityManager?.unregisterNetworkCallback(networkCallback)
        } catch (_: Exception) { }
    }
}


