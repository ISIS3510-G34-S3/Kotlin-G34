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

        // Navegación con BottomNav (incluye el nuevo destino de mapa con sufijo _map)
        binding.bottomNav.setOnItemSelectedListener { item ->
            val targetDestId = when (item.itemId) {
                R.id.homeFragment -> R.id.homeFragment
                R.id.createExperienceFragment -> R.id.createExperienceFragment
                R.id.tab_map_map -> R.id.navigation_map_map   // <- new map tab -> map destination
                else -> null
            }

            if (targetDestId == null) return@setOnItemSelectedListener false

            // Evita re-navegar al mismo destino
            if (navController.currentDestination?.id == targetDestId) {
                return@setOnItemSelectedListener true
            }

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

        // Reselección: no hacer nada
        binding.bottomNav.setOnItemReselectedListener { /* no-op */ }

        // Mantener sincronizada la selección al navegar (back stack, acciones programáticas, etc.)
        navController.addOnDestinationChangedListener { _, dest, _ ->
            when (dest.id) {
                R.id.homeFragment ->
                    binding.bottomNav.menu.findItem(R.id.homeFragment)?.isChecked = true
                R.id.createExperienceFragment ->
                    binding.bottomNav.menu.findItem(R.id.createExperienceFragment)?.isChecked = true
                R.id.navigation_map_map ->
                    binding.bottomNav.menu.findItem(R.id.tab_map_map)?.isChecked = true
            }
        }

        // Selección inicial (si el grafo inicia en Home). Si tu startDestination es otro, ajústalo.
        if (savedInstanceState == null) {
            // Si en este branch quieres arrancar en el mapa, puedes marcar la pestaña del mapa:
            // binding.bottomNav.selectedItemId = R.id.tab_map_map
            binding.bottomNav.selectedItemId = R.id.homeFragment
        }
    }
}
