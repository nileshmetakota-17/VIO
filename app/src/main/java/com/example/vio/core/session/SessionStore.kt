package com.example.vio.core.session

import android.content.Context

data class MapSession(
    val pathMeters: Double = 0.0,
    val lastConfirmedMeters: Double? = null,
    val lastLatitude: Double? = null,
    val lastLongitude: Double? = null,
    val zoneId: String? = null,
    val zoneLabel: String? = null,
)

class SessionStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("vio-session", Context.MODE_PRIVATE)

    fun read(mapId: String): MapSession = MapSession(
        pathMeters = prefs.getFloat(key(mapId, "path"), 0f).toDouble(),
        lastConfirmedMeters = if (prefs.contains(key(mapId, "confirmed"))) prefs.getFloat(key(mapId, "confirmed"), 0f).toDouble() else null,
        lastLatitude = prefs.getString(key(mapId, "lat"), null)?.toDoubleOrNull(),
        lastLongitude = prefs.getString(key(mapId, "lon"), null)?.toDoubleOrNull(),
        zoneId = prefs.getString(key(mapId, "zone"), null),
        zoneLabel = prefs.getString(key(mapId, "label"), null),
    )

    fun write(mapId: String, session: MapSession) {
        prefs.edit()
            .putFloat(key(mapId, "path"), session.pathMeters.toFloat())
            .putString(key(mapId, "zone"), session.zoneId)
            .putString(key(mapId, "label"), session.zoneLabel)
            .apply {
                if (session.lastConfirmedMeters != null) putFloat(key(mapId, "confirmed"), session.lastConfirmedMeters.toFloat())
                if (session.lastLatitude != null) putString(key(mapId, "lat"), session.lastLatitude.toString())
                if (session.lastLongitude != null) putString(key(mapId, "lon"), session.lastLongitude.toString())
            }
            .apply()
    }

    private fun key(mapId: String, name: String) = "$mapId:$name"
}
