package com.example.kotlinview

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.kotlinview.databinding.ActivityMainBinding
import com.google.android.material.navigation.NavigationBarView

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Labels visibles siempre
        binding.bottomNav.labelVisibilityMode = NavigationBarView.LABEL_VISIBILITY_LABELED

        // (Opcional) marcar "Home" activo al inicio
        binding.bottomNav.menu.findItem(R.id.tab_discovery)?.isChecked = true

        // IMPORTANTE: sin edge-to-edge y sin listeners de insets, para que el padding del XML
        // (4dp) no sea modificado en runtime.
    }
}