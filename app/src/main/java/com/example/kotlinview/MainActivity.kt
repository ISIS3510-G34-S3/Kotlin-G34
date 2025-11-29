package com.example.kotlinview

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import com.example.kotlinview.databinding.ActivityMainBinding
import com.google.android.material.navigation.NavigationBarView
import com.google.firebase.auth.FirebaseAuth
import com.example.kotlinview.data.experiences.BookingRetryManager

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    companion object {
        private const val REQ_NOTIF_PERMISSION = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        BookingRetryManager.init(applicationContext)

        val navHost =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment

        val navController = navHost.navController

        if (FirebaseAuth.getInstance().currentUser != null &&
            navController.currentDestination?.id == R.id.loginFragment) {
            navController.navigate(
                R.id.homeFragment,
                null,
                androidx.navigation.NavOptions.Builder()
                    .setPopUpTo(R.id.loginFragment, true)
                    .build()
            )
        }

        binding.bottomNav.labelVisibilityMode = NavigationBarView.LABEL_VISIBILITY_LABELED

        binding.bottomNav.setOnItemSelectedListener { item ->
            val targetDestId = when (item.itemId) {
                R.id.homeFragment -> R.id.homeFragment
                R.id.createExperienceFragment -> R.id.createExperienceFragment
                R.id.profileFragment -> R.id.profileFragment
                // Tu caso especial de Map:
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

        binding.bottomNav.setOnItemReselectedListener { /* no-op */ }

        navController.addOnDestinationChangedListener { _, dest, _ ->
            when (dest.id) {
                // 🔒 En Login ocultamos completamente la barra y desmarcamos todo
                R.id.loginFragment -> {
                    binding.bottomNav.visibility = View.GONE
                    binding.bottomNav.menu.setGroupCheckable(0, false, true)
                }

                // ✅ En destinos con tabs, mostramos barra y marcamos el tab correspondiente
                R.id.homeFragment -> {
                    binding.bottomNav.visibility = View.VISIBLE
                    binding.bottomNav.menu.setGroupCheckable(0, true, true)
                    binding.bottomNav.menu.findItem(R.id.homeFragment)?.isChecked = true
                }

                R.id.createExperienceFragment -> {
                    binding.bottomNav.visibility = View.VISIBLE
                    binding.bottomNav.menu.setGroupCheckable(0, true, true)
                    binding.bottomNav.menu.findItem(R.id.createExperienceFragment)?.isChecked = true
                }

                R.id.profileFragment -> {
                    binding.bottomNav.visibility = View.VISIBLE
                    binding.bottomNav.menu.setGroupCheckable(0, true, true)
                    binding.bottomNav.menu.findItem(R.id.profileFragment)?.isChecked = true
                }

                R.id.navigation_map_map -> {
                    binding.bottomNav.visibility = View.VISIBLE
                    binding.bottomNav.menu.setGroupCheckable(0, true, true)
                    binding.bottomNav.menu.findItem(R.id.tab_map_map)?.isChecked = true
                }

                else -> binding.bottomNav.visibility = View.VISIBLE
            }
        }


    }
}
