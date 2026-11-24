package com.example.vio.ui

import java.util.Locale

fun formatMeters(value: Double): String = String.format(Locale.US, "%.0f m", value)

fun formatScore(value: Float): String = String.format(Locale.US, "%.2f", value)

fun formatSignedDegrees(value: Float): String = String.format(Locale.US, "%+.0f°", value)

fun formatBearing(value: Float): String = String.format(Locale.US, "%.0f°", value)

fun formatGps(latitude: Double, longitude: Double): String =
    String.format(Locale.US, "%.5f, %.5f", latitude, longitude)
