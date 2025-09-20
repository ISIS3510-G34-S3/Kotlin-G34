package com.example.kotlinview.ui.home

data class FilterOptions(
    val location: String = "",
    val activityTypes: List<String> = emptyList(),
    val duration: String? = null,
    val skillsQuery: String = "",
    val accessibility: Boolean = false,
    val dateStart: String? = null,
    val dateEnd: String? = null
)