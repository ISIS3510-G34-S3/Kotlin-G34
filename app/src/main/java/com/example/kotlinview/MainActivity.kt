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

        // Listener de selección: solo navega para ítems que SÍ existen en el nav_graph.
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.homeFragment,
                R.id.createExperienceFragment -> {
                    // Evita re-navegar al mismo destino
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
                else -> {
                    // Ítems sin destino aún: no hacer nada y no cambiar selección
                    false
                }
            }
        }

        // Reselección: no hacer nada
        binding.bottomNav.setOnItemReselectedListener { /* no-op */ }

        // Mantén sincronizada la selección al navegar (por back, etc.)
        navController.addOnDestinationChangedListener { _, dest, _ ->
            when (dest.id) {
                R.id.homeFragment -> binding.bottomNav.menu.findItem(R.id.homeFragment).isChecked = true
                R.id.createExperienceFragment -> binding.bottomNav.menu.findItem(R.id.createExperienceFragment).isChecked = true
            }
        }

        // Selección inicial
        if (savedInstanceState == null) {
            binding.bottomNav.selectedItemId = R.id.homeFragment
        }
    }
}
