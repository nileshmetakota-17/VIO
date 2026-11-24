package com.example.vio.core.sensors

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

fun metersBetween(latitude: Double, longitude: Double, otherLatitude: Double, otherLongitude: Double): Double {
    val earth = 6_371_000.0
    val startLat = Math.toRadians(latitude)
    val endLat = Math.toRadians(otherLatitude)
    val dLat = Math.toRadians(otherLatitude - latitude)
    val dLon = Math.toRadians(otherLongitude - longitude)
    val a = sin(dLat / 2) * sin(dLat / 2) + cos(startLat) * cos(endLat) * sin(dLon / 2) * sin(dLon / 2)
    return 2 * earth * asin(sqrt(a))
}

fun zoneStartMeters(distanceMeters: Double, zoneLengthMeters: Double): Double {
    val length = zoneLengthMeters.coerceAtLeast(1.0)
    return floor(distanceMeters.coerceAtLeast(0.0) / length) * length
}

fun zoneLabelFor(distanceMeters: Double, zoneLengthMeters: Double): String {
    val length = zoneLengthMeters.coerceAtLeast(1.0)
    val start = zoneStartMeters(distanceMeters, length).toInt()
    val end = (start + length).toInt()
    return "$start–$end m"
}
