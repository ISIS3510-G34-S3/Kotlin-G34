package com.example.kotlinview.ui.home

data class FilterOptions(
    val activityTypes: List<String> = emptyList(),
    // Mantengo duration como String? para no romper llamadas existentes
    val duration: String? = null,
    val location: String? = null,
    // Estos dos existen en tu sheet y los seguimos pasando
    val skillsQuery: String? = null,
    val accessibility: Boolean = false,
    // NUEVO: disponibilidad en epoch millis (UTC). Nulos si no se filtra por fecha
    val startAtMs: Long? = null,
    val endAtMs: Long? = null
)
