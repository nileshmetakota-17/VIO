package com.example.vio.core.sensors

import android.content.Context
import android.location.Address
import android.location.Geocoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

object PlaceNames {
    suspend fun lookup(context: Context, latitude: Double, longitude: Double): String? = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext null
        val address = runCatching {
            @Suppress("DEPRECATION")
            Geocoder(context, Locale.getDefault()).getFromLocation(latitude, longitude, 1)?.firstOrNull()
        }.getOrNull() ?: return@withContext null
        readableName(address.thoroughfare, address.featureName, address.subLocality, address.locality)
    }

    fun readableName(thoroughfare: String?, featureName: String?, subLocality: String?, locality: String?): String? {
        val named = listOf(thoroughfare, featureName, subLocality, locality)
            .firstOrNull { value ->
                val text = value?.trim().orEmpty()
                text.any { it.isLetter() } && text.any { !it.isDigit() }
            }
        return named?.trim()?.take(40)
    }
}
