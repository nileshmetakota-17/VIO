package com.example.vio.core.settings

import android.content.Context

data class VioSettings(
    val zoneDistanceMeters: Double = 20.0,
    val accuracyTargetMeters: Double = 2.0,
    val autoZoneDetection: Boolean = true,
    val placeNames: Boolean = true,
    val driftDegrees: Float = 45f,
    val autoRelocalize: Boolean = true,
    val multipleImages: Boolean = true,
    val autoSave: Boolean = true,
    val useTime: Boolean = true,
    val useMove: Boolean = true,
    val useTurn: Boolean = true,
    val intervalSeconds: Int = 5,
    val moveMeters: Double = 5.0,
    val turnDegrees: Float = 45f,
    val manualFields: Boolean = false,
    val showMapDetails: Boolean = true,
)

class AppSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("vio-settings", Context.MODE_PRIVATE)

    fun read(): VioSettings = VioSettings(
        zoneDistanceMeters = prefs.getFloat(KEY_ZONE, 20f).toDouble().coerceIn(1.0, 500.0),
        accuracyTargetMeters = prefs.getFloat(KEY_ACCURACY, 2f).toDouble().coerceIn(0.1, 100.0),
        autoZoneDetection = prefs.getBoolean(KEY_AUTO_ZONE, true),
        placeNames = prefs.getBoolean(KEY_NAMES, true),
        driftDegrees = prefs.getFloat(KEY_DRIFT, 45f).coerceIn(5f, 180f),
        autoRelocalize = prefs.getBoolean(KEY_RELOCALIZE, true),
        multipleImages = prefs.getBoolean(KEY_MULTI, true),
        autoSave = prefs.getBoolean(KEY_SAVE, true),
        useTime = prefs.getBoolean(KEY_USE_TIME, true),
        useMove = prefs.getBoolean(KEY_USE_MOVE, true),
        useTurn = prefs.getBoolean(KEY_USE_TURN, true),
        intervalSeconds = prefs.getInt(KEY_INTERVAL, 5).coerceIn(1, 600),
        moveMeters = prefs.getFloat(KEY_MOVE, 5f).toDouble().coerceIn(1.0, 500.0),
        turnDegrees = prefs.getFloat(KEY_TURN, 45f).coerceIn(5f, 180f),
        manualFields = prefs.getBoolean(KEY_MANUAL, false),
        showMapDetails = prefs.getBoolean(KEY_DETAILS, true),
    )

    fun write(settings: VioSettings) {
        prefs.edit()
            .putFloat(KEY_ZONE, settings.zoneDistanceMeters.toFloat())
            .putFloat(KEY_ACCURACY, settings.accuracyTargetMeters.toFloat())
            .putBoolean(KEY_AUTO_ZONE, settings.autoZoneDetection)
            .putBoolean(KEY_NAMES, settings.placeNames)
            .putFloat(KEY_DRIFT, settings.driftDegrees)
            .putBoolean(KEY_RELOCALIZE, settings.autoRelocalize)
            .putBoolean(KEY_MULTI, settings.multipleImages)
            .putBoolean(KEY_SAVE, settings.autoSave)
            .putBoolean(KEY_USE_TIME, settings.useTime)
            .putBoolean(KEY_USE_MOVE, settings.useMove)
            .putBoolean(KEY_USE_TURN, settings.useTurn)
            .putInt(KEY_INTERVAL, settings.intervalSeconds)
            .putFloat(KEY_MOVE, settings.moveMeters.toFloat())
            .putFloat(KEY_TURN, settings.turnDegrees)
            .putBoolean(KEY_MANUAL, settings.manualFields)
            .putBoolean(KEY_DETAILS, settings.showMapDetails)
            .apply()
    }

    private companion object {
        const val KEY_ZONE = "zone_distance_m"
        const val KEY_ACCURACY = "accuracy_target_m"
        const val KEY_AUTO_ZONE = "auto_zone"
        const val KEY_NAMES = "place_names"
        const val KEY_DRIFT = "drift_degrees"
        const val KEY_RELOCALIZE = "auto_relocalize"
        const val KEY_MULTI = "multiple_images"
        const val KEY_SAVE = "auto_save"
        const val KEY_USE_TIME = "use_time"
        const val KEY_USE_MOVE = "use_move"
        const val KEY_USE_TURN = "use_turn"
        const val KEY_INTERVAL = "interval_s"
        const val KEY_MOVE = "move_m"
        const val KEY_TURN = "turn_deg"
        const val KEY_MANUAL = "manual_fields"
        const val KEY_DETAILS = "show_map_details"
    }
}
