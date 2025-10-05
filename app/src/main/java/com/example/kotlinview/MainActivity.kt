package com.example.kotlinview

import android.os.Bundle
import android.view.View
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

        val navHost =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHost.navController

        binding.bottomNav.labelVisibilityMode = NavigationBarView.LABEL_VISIBILITY_LABELED

        // Navega solo en tabs con destino; el resto no hace nada
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.homeFragment,
                R.id.createExperienceFragment,
                R.id.profileFragment -> {
                    if (navController.currentDestination?.id != item.itemId) {
                        val options = NavOptions.Builder()
                            .setLaunchSingleTop(true)
                            .setRestoreState(true)
                            .setPopUpTo(
                                navController.graph.startDestinationId,
                                inclusive = false,
                                saveState = true
                            )
                            .build()
                        navController.navigate(item.itemId, null, options)
                    }
                    true
                }
                else -> false // Map (u otros sin destino) no reaccionan
            }
        }
        binding.bottomNav.setOnItemReselectedListener { /* no-op */ }

        // Mostrar/ocultar navbar según el destino
        navController.addOnDestinationChangedListener { _, dest, _ ->
            when (dest.id) {
                R.id.loginFragment -> {
                    // Oculta completamente el navbar en Login
                    binding.bottomNav.visibility = View.GONE
                    binding.bottomNav.menu.setGroupCheckable(0, false, true)
                }
                R.id.homeFragment -> {
                    binding.bottomNav.visibility = View.VISIBLE
                    binding.bottomNav.menu.setGroupCheckable(0, true, true)
                    binding.bottomNav.menu.findItem(R.id.homeFragment).isChecked = true
                }
                R.id.createExperienceFragment -> {
                    binding.bottomNav.visibility = View.VISIBLE
                    binding.bottomNav.menu.setGroupCheckable(0, true, true)
                    binding.bottomNav.menu.findItem(R.id.createExperienceFragment).isChecked = true
                }
                R.id.profileFragment -> {
                    binding.bottomNav.visibility = View.VISIBLE
                    binding.bottomNav.menu.setGroupCheckable(0, true, true)
                    binding.bottomNav.menu.findItem(R.id.profileFragment).isChecked = true
                }
                else -> {
                    // Otros destinos: muestra navbar por defecto
                    binding.bottomNav.visibility = View.VISIBLE
                }
            }
        }

        // ⚠️ No fuerces selección inicial de Home; debe arrancar en Login
        // if (savedInstanceState == null) binding.bottomNav.selectedItemId = R.id.homeFragment // NO
    }
}
