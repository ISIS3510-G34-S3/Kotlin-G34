package com.example.kotlinview

import android.app.Application
import android.os.Build
import android.util.Log
import com.example.kotlinview.analytics.DeviceDistributionReporter
import com.example.kotlinview.core.ServiceLocator

class DeviceAnalyticsApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Make sure ServiceLocator / Firebase are ready very early.
        ServiceLocator.init(applicationContext)

        // Compose a stable, human-readable device label (manufacturer + model).
        // e.g., "Samsung SM-G991B" or "Google Pixel 7"
        val manufacturer = Build.MANUFACTURER?.trim().orEmpty()
        val model = Build.MODEL?.trim().orEmpty()
        val deviceLabel = when {
            manufacturer.isNotEmpty() && model.isNotEmpty() -> "$manufacturer $model"
            model.isNotEmpty() -> model
            else -> Build.DEVICE?.takeIf { it.isNotBlank() } ?: "UnknownDevice"
        }

        // Report this device once (per install) to Firestore device_distribution.
        DeviceDistributionReporter.recordInstallIfNeeded(
            appContext = applicationContext,
            deviceLabel = deviceLabel
        ) { success ->
            Log.d("KotlinViewApp", "device_distribution first-open report success=$success")
        }
    }
}
