package com.example.vio.core.sensors

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.Manifest

data class ImuSnapshot(
    val timestampNs: Long,
    val gyroZRadPerSec: Float?,
    val gyroAvailable: Boolean,
    val accelAvailable: Boolean,
    val magneticHeadingDegrees: Float?,
    val turnSinceMarkDegrees: Float,
)

class SensorHub(context: Context) : SensorEventListener {
    private val manager = context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val gyro = manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val accelerometer = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val rotation = manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private var gyroZ: Float? = null
    private var magnetic: Float? = null
    private var timestampNs: Long = 0L
    private var lastGyroNs = 0L
    private var integratedYaw = 0f
    private var markHeading: Float? = null
    private var peakTurnDegrees = 0f

    fun start() {
        gyro?.let { manager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        accelerometer?.let { manager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        rotation?.let { manager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    fun stop() {
        manager.unregisterListener(this)
    }

    fun snapshot(): ImuSnapshot = synchronized(this) {
        ImuSnapshot(
            timestampNs = timestampNs,
            gyroZRadPerSec = gyroZ,
            gyroAvailable = gyro != null,
            accelAvailable = accelerometer != null,
            magneticHeadingDegrees = magnetic,
            turnSinceMarkDegrees = peakTurnDegrees,
        )
    }

    fun markMotion() = synchronized(this) {
        markHeading = magnetic ?: integratedYaw
        peakTurnDegrees = 0f
    }

    override fun onSensorChanged(event: SensorEvent) {
        synchronized(this) {
            timestampNs = event.timestamp
            when (event.sensor.type) {
                Sensor.TYPE_GYROSCOPE -> {
                    gyroZ = event.values[2]
                    if (lastGyroNs != 0L) {
                        val dt = (event.timestamp - lastGyroNs) / 1_000_000_000.0
                        if (dt in 0.0..0.5) integratedYaw += Math.toDegrees(event.values[2] * dt).toFloat()
                    }
                    lastGyroNs = event.timestamp
                    if (rotation == null) noteHeading(wrapDegrees(integratedYaw))
                }
                Sensor.TYPE_ROTATION_VECTOR -> {
                    val matrix = FloatArray(9)
                    val orientation = FloatArray(3)
                    SensorManager.getRotationMatrixFromVector(matrix, event.values)
                    SensorManager.getOrientation(matrix, orientation)
                    var degrees = Math.toDegrees(orientation[0].toDouble()).toFloat()
                    if (degrees < 0f) degrees += 360f
                    magnetic = degrees
                    noteHeading(degrees)
                }
            }
        }
    }

    private fun noteHeading(degrees: Float) {
        val base = markHeading
        if (base == null) {
            markHeading = degrees
            return
        }
        val delta = headingDeltaDegrees(base, degrees)
        if (delta > peakTurnDegrees) peakTurnDegrees = delta
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}

private fun wrapDegrees(degrees: Float): Float {
    var value = degrees % 360f
    if (value < 0f) value += 360f
    return value
}

fun headingDeltaDegrees(from: Float, to: Float): Float {
    var delta = to - from
    while (delta > 180f) delta -= 360f
    while (delta < -180f) delta += 360f
    return kotlin.math.abs(delta)
}

data class GeoFix(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val fromGps: Boolean,
)

class MappingLocator(context: Context) {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val listeners = mutableListOf<LocationListener>()
    private var onUpdate: ((GeoFix?, String) -> Unit)? = null
    private var fixTimeMs = 0L
    private var gpsSeenAtMs = 0L
    @Volatile var fix: GeoFix? = null
        private set
    @Volatile var status: String = "No GPS fix"
        private set

    fun hasPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    fun start(onUpdate: (GeoFix?, String) -> Unit) {
        this.onUpdate = onUpdate
        if (listeners.isNotEmpty()) {
            onUpdate(fix, status)
            return
        }
        if (!hasPermission()) {
            publish(null, "No GPS fix. Location permission is not granted.")
            return
        }
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { provider -> runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false) }
        if (providers.isEmpty()) {
            publish(null, "No GPS fix. Location is turned off.")
            return
        }
        var sawFix = false
        for (provider in providers) {
            val last = runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
            if (last != null) {
                apply(last)
                sawFix = true
            }
            val listener = LocationListener { location -> apply(location) }
            listeners += listener
            val started = runCatching {
                manager.requestLocationUpdates(provider, 1_000L, 0f, listener, Looper.getMainLooper())
            }
            if (started.isFailure) {
                listeners.remove(listener)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                runCatching {
                    manager.getCurrentLocation(
                        provider,
                        null,
                        ContextCompat.getMainExecutor(appContext),
                    ) { location ->
                        if (location != null) apply(location)
                    }
                }
            }
        }
        if (!sawFix && fix == null) {
            publish(null, "No GPS fix. Waiting for satellites.")
        }
    }

    fun stop() {
        listeners.forEach { listener -> runCatching { manager.removeUpdates(listener) } }
        listeners.clear()
        onUpdate = null
        fix = null
        fixTimeMs = 0L
        gpsSeenAtMs = 0L
        status = "No GPS fix. GPS is off."
    }

    private fun apply(location: Location) {
        val satellite = location.provider == LocationManager.GPS_PROVIDER
        val fromGps = satellite && freshSatellite(location)
        val next = GeoFix(location.latitude, location.longitude, location.accuracy, fromGps)
        val current = fix
        if (current != null) {
            val newer = location.time >= fixTimeMs
            val sharper = location.accuracy + 15f < current.accuracyMeters
            val gpsStillFresh = current.fromGps && System.currentTimeMillis() - gpsSeenAtMs < 20_000L
            if (!fromGps && gpsStillFresh) return
            if (!newer && !sharper) return
            if (!fromGps && location.accuracy > current.accuracyMeters + 20f) return
        }
        fix = next
        fixTimeMs = location.time
        if (fromGps) gpsSeenAtMs = System.currentTimeMillis()
        val message = when {
            fromGps -> String.format(
                java.util.Locale.US,
                "GPS fix · %.5f, %.5f ± %d m",
                next.latitude,
                next.longitude,
                next.accuracyMeters.toInt(),
            )
            satellite -> "No GPS fix. Last satellite reading is old."
            else -> "No GPS fix. Network location only."
        }
        publish(next, message)
    }

    private fun freshSatellite(location: Location): Boolean {
        if (location.time <= 0L) return true
        val ageMs = System.currentTimeMillis() - location.time
        return ageMs in -5_000..20_000
    }

    private fun publish(next: GeoFix?, message: String) {
        if (next == null) fix = null
        status = message
        onUpdate?.invoke(fix, status)
    }
}
