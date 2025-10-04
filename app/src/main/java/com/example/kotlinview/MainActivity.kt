package com.example.kotlinview

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import com.example.kotlinview.databinding.ActivityMainBinding
import com.google.android.material.navigation.NavigationBarView

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // NavController del NavHost
        val navHost =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHost.navController

        // Labels visibles
        binding.bottomNav.labelVisibilityMode = NavigationBarView.LABEL_VISIBILITY_LABELED

        // Listener de selección (incluye Map)
        binding.bottomNav.setOnItemSelectedListener { item ->
            val targetDestId = when (item.itemId) {
                R.id.homeFragment -> R.id.homeFragment
                R.id.createExperienceFragment -> R.id.createExperienceFragment
                R.id.profileFragment -> R.id.profileFragment
                R.id.tab_map_map -> R.id.navigation_map_map
                else -> null
            }

            if (targetDestId == null) return@setOnItemSelectedListener false
            if (navController.currentDestination?.id == targetDestId) return@setOnItemSelectedListener true

            val options = NavOptions.Builder()
                .setLaunchSingleTop(true)
                .setRestoreState(true)
                .setPopUpTo(
                    navController.graph.startDestinationId,
                    inclusive = false,
                    saveState = true
                )
                .build()

            return@setOnItemSelectedListener try {
                navController.navigate(targetDestId, null, options)
                true
            } catch (_: Exception) {
                false
            }
        }

        // Reselección: no-op
        binding.bottomNav.setOnItemReselectedListener { /* no-op */ }

        // Mantén sincronía con el destino
        navController.addOnDestinationChangedListener { _, dest, _ ->
            when (dest.id) {
                R.id.homeFragment ->
                    binding.bottomNav.menu.findItem(R.id.homeFragment)?.isChecked = true
                R.id.createExperienceFragment ->
                    binding.bottomNav.menu.findItem(R.id.createExperienceFragment)?.isChecked = true
                R.id.profileFragment ->
                    binding.bottomNav.menu.findItem(R.id.profileFragment)?.isChecked = true
                R.id.navigation_map_map ->
                    binding.bottomNav.menu.findItem(R.id.tab_map_map)?.isChecked = true
            }
        }

        if (savedInstanceState == null) {
            binding.bottomNav.selectedItemId = R.id.homeFragment
        }
    }
}
