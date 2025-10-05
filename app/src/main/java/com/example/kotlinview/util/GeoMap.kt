package com.example.kotlinview.util

import kotlin.math.*

object GeoMap {
    fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat/2).pow(2.0) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon/2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1-a))
        return R * c
    }

    // very small bbox (good enough for Firestore prefilter)
    fun latLngBoundingBox(lat: Double, lon: Double, radiusKm: Double): DoubleArray {
        val dLat = radiusKm / 111.0
        val dLon = radiusKm / (111.0 * cos(Math.toRadians(lat)).coerceAtLeast(0.0001))
        val minLat = lat - dLat
        val maxLat = lat + dLat
        val minLon = lon - dLon
        val maxLon = lon + dLon
        return doubleArrayOf(minLat, maxLat, minLon, maxLon)
    }
}
