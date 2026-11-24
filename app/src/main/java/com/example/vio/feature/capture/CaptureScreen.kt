package com.example.vio.feature.capture

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.vio.core.camera.CameraPane
import com.example.vio.core.model.ObservationSummary
import com.example.vio.core.model.QualityReport
import com.example.vio.core.model.Zone
import com.example.vio.core.sensors.MappingLocator
import com.example.vio.core.sensors.PlaceNames
import com.example.vio.core.sensors.SensorHub
import com.example.vio.core.sensors.metersBetween
import com.example.vio.core.sensors.zoneLabelFor
import com.example.vio.core.sensors.zoneStartMeters
import com.example.vio.core.session.MotionGate
import com.example.vio.core.session.SessionStore
import com.example.vio.core.session.ZoneAssignment
import com.example.vio.core.settings.AppSettings
import com.example.vio.core.storage.VioGraph
import com.example.vio.core.vision.DescriptorMath
import com.example.vio.core.vision.FeatureExtractor
import com.example.vio.core.vision.ImageBuffer
import com.example.vio.core.vision.ImageQuality
import com.example.vio.ui.GpsFixLine
import com.example.vio.ui.LiveStatus
import com.example.vio.ui.Notice
import com.example.vio.ui.Panel
import com.example.vio.ui.ScreenHeader
import com.example.vio.ui.SectionTitle
import com.example.vio.ui.SelectRow
import com.example.vio.ui.SettingRow
import com.example.vio.ui.formatGps
import com.example.vio.ui.formatMeters
import com.example.vio.ui.theme.Danger
import com.example.vio.ui.theme.InkHigh
import com.example.vio.ui.theme.Moss
import com.example.vio.ui.theme.Mute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

data class CaptureUi(
    val mapName: String = "",
    val zones: List<Zone> = emptyList(),
    val selectedZoneId: String? = null,
    val observations: List<ObservationSummary> = emptyList(),
    val quality: QualityReport? = null,
    val gpsEnabled: Boolean = false,
    val gpsFixed: Boolean = false,
    val gpsStatus: String = "No GPS fix",
    val autoSave: Boolean = true,
    val manualFields: Boolean = false,
    val useTime: Boolean = true,
    val useMove: Boolean = true,
    val useTurn: Boolean = true,
    val intervalSeconds: String = "5",
    val moveMeters: String = "5",
    val turnDegrees: String = "45",
    val zoneLengthMeters: String = "20",
    val liveDistanceMeters: Double? = null,
    val movedSinceSaveMeters: Double? = null,
    val liveZoneLabel: String? = null,
    val sourceSet: Boolean = false,
    val activeZoneId: String? = null,
    val pathMeters: Double = 0.0,
    val zoneOffsetMeters: Double = 0.0,
    val sinceFixMeters: Double = 0.0,
    val uncertaintyMeters: Double? = null,
    val accuracyTargetMeters: Double = 2.0,
    val imuStatus: String = "Waiting for sensors",
    val zoneStatus: String = "Waiting for GPS",
    val message: String? = null,
    val error: String? = null,
    val busy: Boolean = false,
)

class CaptureViewModel(app: Application, private val mapId: String) : AndroidViewModel(app) {
    private val repository = VioGraph.repository(app)
    private val settingsStore = AppSettings(app)
    private val sessionStore = SessionStore(app)
    private val locator = MappingLocator(app)
    private val sensors = SensorHub(app)
    private val sessionId = UUID.randomUUID().toString()
    private val latest = AtomicReference<ImageBuffer?>(null)
    private val lastAutoSaveMs = AtomicLong(0L)
    private val nextDistanceTryMs = AtomicLong(0L)
    private val saving = AtomicBoolean(false)
    private var frameDistanceMeters = 0.0
    private var lastSavedGlobal: ByteArray? = null
    private var sourceLatitude: Double? = null
    private var sourceLongitude: Double? = null
    private var markLatitude: Double? = null
    private var markLongitude: Double? = null
    private var pathMeters = 0.0
    private var lastTrackLatitude: Double? = null
    private var lastTrackLongitude: Double? = null
    private var activeZoneId: String? = null
    private val sourceDecided = AtomicBoolean(false)
    private var capturedStart = false
    private val _ui = MutableStateFlow(CaptureUi())
    val ui: StateFlow<CaptureUi> = _ui

    init {
        sensors.start()
        val saved = settingsStore.read()
        val session = sessionStore.read(mapId)
        pathMeters = session.pathMeters
        activeZoneId = session.zoneId
        _ui.update {
            it.copy(
                zoneLengthMeters = trimMeters(saved.zoneDistanceMeters),
                autoSave = saved.autoSave,
                useTime = saved.useTime,
                useMove = saved.useMove,
                useTurn = saved.useTurn,
                intervalSeconds = saved.intervalSeconds.toString(),
                moveMeters = trimMeters(saved.moveMeters),
                turnDegrees = saved.turnDegrees.toInt().toString(),
                manualFields = saved.manualFields,
                pathMeters = pathMeters,
                accuracyTargetMeters = saved.accuracyTargetMeters,
                activeZoneId = session.zoneId,
                liveZoneLabel = session.zoneLabel,
            )
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            try {
                val manifest = repository.manifest(mapId)
                val zones = repository.zones(mapId)
                val observations = repository.observations(mapId)
                sourceLatitude = manifest?.sourceLatitude
                sourceLongitude = manifest?.sourceLongitude
                if (markLatitude == null) {
                    val latestFix = observations.firstOrNull { it.latitude != null && it.longitude != null }
                    markLatitude = latestFix?.latitude
                    markLongitude = latestFix?.longitude
                }
                val selected = _ui.value.selectedZoneId ?: zones.firstOrNull()?.zoneId
                if (_ui.value.selectedZoneId == null) {
                    frameDistanceMeters = zones.firstOrNull()?.routeDistanceMeters ?: frameDistanceMeters
                }
                val live = livePlacement()
                _ui.update {
                    it.copy(
                        mapName = manifest?.name ?: "Map",
                        zones = zones,
                        observations = observations,
                        selectedZoneId = selected,
                        sourceSet = sourceLatitude != null && sourceLongitude != null,
                        liveDistanceMeters = live?.first,
                        liveZoneLabel = live?.second,
                        movedSinceSaveMeters = movedSinceSave(locator.fix),
                    )
                }
            } catch (error: Exception) {
                _ui.update { it.copy(error = error.message ?: "Could not load this map.") }
            }
        }
    }

    fun selectZone(zoneId: String) {
        val zone = _ui.value.zones.firstOrNull { it.zoneId == zoneId }
        if (zone != null) frameDistanceMeters = zone.routeDistanceMeters
        _ui.update { it.copy(selectedZoneId = zoneId, error = null) }
    }

    fun setFrameDistance(text: String) {
        text.toDoubleOrNull()?.let { frameDistanceMeters = it }
    }

    fun setAutoSave(enabled: Boolean) {
        _ui.update { it.copy(autoSave = enabled) }
        settingsStore.write(settingsStore.read().copy(autoSave = enabled))
    }

    fun renameActiveZone(label: String) {
        val zoneId = _ui.value.activeZoneId ?: return
        viewModelScope.launch {
            try {
                repository.renameZone(zoneId, label)
                _ui.update { it.copy(liveZoneLabel = label.trim(), message = "Zone name saved.", error = null) }
                persistSession()
                refresh()
            } catch (error: Exception) {
                _ui.update { it.copy(error = error.message ?: "Could not rename the zone.") }
            }
        }
    }

    fun setManualFields(enabled: Boolean) {
        _ui.update { it.copy(manualFields = enabled, error = null) }
    }

    fun setUseTime(enabled: Boolean) {
        _ui.update { it.copy(useTime = enabled) }
    }

    fun setUseMove(enabled: Boolean) {
        _ui.update { it.copy(useMove = enabled) }
    }

    fun setUseTurn(enabled: Boolean) {
        _ui.update { it.copy(useTurn = enabled) }
    }

    fun setIntervalSeconds(text: String) {
        _ui.update { it.copy(intervalSeconds = text.filter { char -> char.isDigit() }.take(3)) }
    }

    fun setMoveMeters(text: String) {
        _ui.update { it.copy(moveMeters = text.filter { char -> char.isDigit() || char == '.' }.take(6)) }
    }

    fun setTurnDegrees(text: String) {
        _ui.update { it.copy(turnDegrees = text.filter { char -> char.isDigit() }.take(3)) }
    }

    fun setZoneLength(text: String) {
        val cleaned = text.filter { char -> char.isDigit() || char == '.' }.take(6)
        cleaned.toDoubleOrNull()?.takeIf { it >= 1.0 }?.let { meters ->
            settingsStore.write(settingsStore.read().copy(zoneDistanceMeters = meters))
        }
        val live = livePlacement(cleaned)
        _ui.update {
            it.copy(
                zoneLengthMeters = cleaned,
                liveDistanceMeters = live?.first ?: it.liveDistanceMeters,
                liveZoneLabel = live?.second,
            )
        }
    }

    fun useCurrentFixAsSource() {
        val fix = locator.fix
        if (fix == null) {
            _ui.update { it.copy(error = "No GPS fix yet. Wait until the status shows coordinates.") }
            return
        }
        viewModelScope.launch {
            try {
                repository.setSource(mapId, fix.latitude, fix.longitude)
                sourceLatitude = fix.latitude
                sourceLongitude = fix.longitude
                sourceDecided.set(true)
                markLatitude = null
                markLongitude = null
                sensors.markMotion()
                val live = livePlacement()
                _ui.update {
                    it.copy(
                        sourceSet = true,
                        liveDistanceMeters = live?.first,
                        liveZoneLabel = live?.second,
                        message = "Source reset to here. Distance is 0 m at this spot.",
                        error = null,
                    )
                }
            } catch (error: Exception) {
                _ui.update { it.copy(error = error.message ?: "Could not store the source.") }
            }
        }
    }

    fun imageFile(relativePath: String): File = repository.imageFile(relativePath)

    fun onFrame(image: ImageBuffer) {
        latest.set(image)
        val quality = ImageQuality.assess(image)
        _ui.update { it.copy(quality = quality) }
        val state = _ui.value
        if (!state.autoSave || !quality.usable || state.busy || saving.get()) return
        if (state.manualFields && state.selectedZoneId == null) return
        if (!state.manualFields && locator.fix == null) return
        if (!state.useTime && !state.useMove && !state.useTurn) return
        val now = android.os.SystemClock.elapsedRealtime()
        val intervalMs = (state.intervalSeconds.toLongOrNull()?.coerceIn(1L, 600L) ?: 5L) * 1_000L
        val move = state.moveMeters.toDoubleOrNull()?.takeIf { it >= 1.0 } ?: 5.0
        val turn = state.turnDegrees.toFloatOrNull()?.takeIf { it >= 5f } ?: 45f
        val previous = lastAutoSaveMs.get()
        val timeDue = state.useTime && now - previous >= intervalMs
        val fix = locator.fix
        val moved = movedSinceSave(fix)
        if (moved != state.movedSinceSaveMeters) {
            _ui.update { it.copy(movedSinceSaveMeters = moved) }
        }
        val distanceDue = state.useMove && moved != null && moved >= move && now >= nextDistanceTryMs.get()
        val turnDue = state.useTurn && sensors.snapshot().turnSinceMarkDegrees >= turn
        val startShot = !state.manualFields && state.observations.isEmpty() && sourceLatitude != null && !capturedStart
        if (!startShot && !timeDue && !distanceDue && !turnDue) return
        if (startShot) capturedStart = true
        if (timeDue || startShot) {
            if (!lastAutoSaveMs.compareAndSet(previous, now)) {
                if (!distanceDue && !turnDue) return
            }
        }
        if (turnDue) sensors.markMotion()
        saveFrame(auto = true)
    }

    private fun movedSinceSave(fix: com.example.vio.core.sensors.GeoFix?): Double? {
        val originLat = markLatitude
        val originLon = markLongitude
        if (fix == null || originLat == null || originLon == null) return null
        return metersBetween(originLat, originLon, fix.latitude, fix.longitude)
    }

    fun onCameraError(message: String) {
        _ui.update { it.copy(error = message) }
    }

    fun setGpsEnabled(enabled: Boolean) {
        if (enabled) {
            locator.start { fix, status ->
                if (fix != null) {
                    if (sourceLatitude == null) lockSourceAtStart(fix) else track(fix)
                }
                val live = livePlacement()
                _ui.update {
                    it.copy(
                        gpsEnabled = true,
                        gpsFixed = fix?.fromGps == true,
                        gpsStatus = status,
                        liveDistanceMeters = live?.first,
                        liveZoneLabel = live?.second,
                        movedSinceSaveMeters = movedSinceSave(fix),
                    )
                }
            }
        } else {
            locator.stop()
            _ui.update { it.copy(gpsEnabled = false, gpsFixed = false, gpsStatus = locator.status) }
        }
    }

    fun addZone(label: String, distanceText: String, lengthText: String, bearingText: String) {
        val distance = distanceText.toDoubleOrNull()
        val length = lengthText.toDoubleOrNull() ?: 80.0
        val bearing = bearingText.toFloatOrNull()
        if (distance == null) {
            _ui.update { it.copy(error = "Enter the zone's distance from the source in metres.") }
            return
        }
        viewModelScope.launch {
            try {
                val zone = repository.addZone(mapId, label, "", distance, length, bearing)
                _ui.update { it.copy(selectedZoneId = zone.zoneId, error = null, message = "Zone added.") }
                refresh()
            } catch (error: Exception) {
                _ui.update { it.copy(error = error.message ?: "Could not add the zone.") }
            }
        }
    }

    fun saveFrame(distanceText: String) {
        distanceText.toDoubleOrNull()?.let { frameDistanceMeters = it }
        saveFrame(auto = false)
    }

    private fun saveFrame(auto: Boolean) {
        val manual = _ui.value.manualFields
        val zoneId = _ui.value.selectedZoneId
        if (manual && zoneId == null) {
            if (!auto) _ui.update { it.copy(error = "Add a zone before saving a reference.") }
            return
        }
        val image = latest.get()
        if (image == null) {
            if (!auto) _ui.update { it.copy(error = "No camera frame yet.") }
            return
        }
        val quality = ImageQuality.assess(image)
        if (!quality.usable) {
            if (!auto) _ui.update { it.copy(error = quality.reasons.joinToString(" "), quality = quality) }
            return
        }
        val typedDistance = frameDistanceMeters
        if (manual && (typedDistance < 0.0 || typedDistance.isNaN())) {
            if (!auto) _ui.update { it.copy(error = "Enter the distance from the source for this frame.") }
            return
        }
        val gps = if (_ui.value.gpsEnabled) locator.fix else null
        if (!manual && gps == null) {
            if (!auto) _ui.update { it.copy(error = "Waiting for a GPS fix so the label and distance can be calculated.") }
            return
        }
        if (!saving.compareAndSet(false, true)) return
        val heading = sensors.snapshot().magneticHeadingDegrees
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, error = null) }
            try {
                val features = withContext(Dispatchers.Default) { FeatureExtractor.extract(image) }
                val previous = lastSavedGlobal
                if (previous != null &&
                    DescriptorMath.similarity(previous, features.globalDescriptor) >= 0.92f
                ) {
                    nextDistanceTryMs.set(android.os.SystemClock.elapsedRealtime() + 2_000L)
                    _ui.update { it.copy(busy = false, message = "Same view, so it was not saved again.") }
                    return@launch
                }
                val placed = if (manual) {
                    zoneId!! to typedDistance
                } else {
                    placeFromGps(gps!!)
                }
                if (!settingsStore.read().multipleImages &&
                    repository.observations(mapId).any { it.zoneId == placed.first }
                ) {
                    _ui.update {
                        it.copy(busy = false, message = "This zone already has a reference. Multiple images are off in Settings.")
                    }
                    return@launch
                }
                repository.addObservation(
                    mapId = mapId,
                    zoneId = placed.first,
                    sessionId = sessionId,
                    image = image,
                    features = features,
                    routeDistanceMeters = placed.second,
                    viewBearingDegrees = heading,
                    latitude = gps?.latitude,
                    longitude = gps?.longitude,
                    horizontalAccuracyMeters = gps?.accuracyMeters,
                    qualityScore = quality.texture,
                    conditionsTag = if (auto) "auto" else "field",
                )
                lastSavedGlobal = features.globalDescriptor
                if (gps != null) {
                    markLatitude = gps.latitude
                    markLongitude = gps.longitude
                }
                sensors.markMotion()
                val note = if (!manual) {
                    "${zoneLabelFor(placed.second, zoneLength())} · ${String.format(Locale.US, "%.0f m", placed.second)} from source"
                } else if (gps != null) {
                    "GPS ${String.format(Locale.US, "%.5f, %.5f", gps.latitude, gps.longitude)}"
                } else {
                    "no GPS fix yet"
                }
                _ui.update {
                    it.copy(
                        busy = false,
                        message = "Reference saved. $note",
                        gpsFixed = it.gpsEnabled && locator.fix?.fromGps == true,
                        gpsStatus = if (it.gpsEnabled) locator.status else it.gpsStatus,
                    )
                }
                refresh()
            } catch (error: Exception) {
                if (_ui.value.observations.isEmpty()) capturedStart = false
                _ui.update { it.copy(busy = false, error = error.message ?: "Could not save the frame.") }
            } finally {
                saving.set(false)
            }
        }
    }

    private suspend fun placeFromGps(gps: com.example.vio.core.sensors.GeoFix): Pair<String, Double> {
        if (sourceLatitude == null || sourceLongitude == null) {
            repository.setSourceIfMissing(mapId, gps.latitude, gps.longitude)
            val manifest = repository.manifest(mapId)
            sourceLatitude = manifest?.sourceLatitude
            sourceLongitude = manifest?.sourceLongitude
        }
        val originLat = sourceLatitude
        val originLon = sourceLongitude
        val meters = if (originLat != null && originLon != null) {
            metersBetween(originLat, originLon, gps.latitude, gps.longitude)
        } else {
            0.0
        }
        val settings = settingsStore.read()
        val length = if (settings.autoZoneDetection) settings.zoneDistanceMeters else zoneLength()
        val zones = repository.zones(mapId)
        val decision = ZoneAssignment.decide(meters, length, zones)
        val zone = decision.zone ?: repository.addZone(
            mapId = mapId,
            label = zoneName(gps, decision.suggestedLabel, settings.placeNames),
            description = "From GPS",
            routeDistanceMeters = decision.startMeters,
            zoneLengthMeters = length,
            routeBearingDegrees = null,
        )
        activeZoneId = zone.zoneId
        return zone.zoneId to meters
    }

    private suspend fun zoneName(gps: com.example.vio.core.sensors.GeoFix, fallback: String, usePlaces: Boolean): String {
        if (!usePlaces) return fallback
        return PlaceNames.lookup(getApplication(), gps.latitude, gps.longitude) ?: fallback
    }

    private fun track(fix: com.example.vio.core.sensors.GeoFix) {
        val previousLat = lastTrackLatitude
        val previousLon = lastTrackLongitude
        val moved = if (previousLat != null && previousLon != null) {
            metersBetween(previousLat, previousLon, fix.latitude, fix.longitude)
        } else {
            0.0
        }
        val settings = settingsStore.read()
        val drift = previousLat != null && MotionGate.rejectAsDrift(
            moved,
            fix.accuracyMeters,
            sensors.snapshot().turnSinceMarkDegrees,
            settings.driftDegrees,
        )
        if (!drift && moved > 0.0 && moved < 100.0) pathMeters += moved
        lastTrackLatitude = fix.latitude
        lastTrackLongitude = fix.longitude
        if (drift) {
            _ui.update {
                it.copy(
                    pathMeters = pathMeters,
                    imuStatus = "Drift held. Zone unchanged.",
                    uncertaintyMeters = fix.accuracyMeters.toDouble(),
                )
            }
            return
        }
        if (sourceLatitude == null || !settings.autoZoneDetection) {
            publishMotion(fix, settings, _ui.value.liveZoneLabel, _ui.value.activeZoneId)
            return
        }
        viewModelScope.launch {
            try {
                val originLat = sourceLatitude ?: return@launch
                val originLon = sourceLongitude ?: return@launch
                val along = metersBetween(originLat, originLon, fix.latitude, fix.longitude)
                val zones = repository.zones(mapId)
                val decision = ZoneAssignment.decide(along, settings.zoneDistanceMeters, zones)
                val zone = decision.zone ?: repository.addZone(
                    mapId = mapId,
                    label = zoneName(fix, decision.suggestedLabel, settings.placeNames),
                    description = "From GPS",
                    routeDistanceMeters = decision.startMeters,
                    zoneLengthMeters = settings.zoneDistanceMeters,
                    routeBearingDegrees = null,
                )
                val changed = zone.zoneId != activeZoneId
                activeZoneId = zone.zoneId
                val since = (_ui.value.observations.filter { it.zoneId == zone.zoneId }.maxOfOrNull { it.routeDistanceMeters }
                    ?: zone.routeDistanceMeters).let { along - it }
                publishMotion(fix, settings, zone.label, zone.zoneId, along - zone.routeDistanceMeters, kotlin.math.abs(since))
                if (changed) {
                    _ui.update { it.copy(message = "Current zone: ${zone.label}") }
                    refresh()
                }
                persistSession()
            } catch (error: Exception) {
                _ui.update { it.copy(error = error.message ?: "Could not update the zone.") }
            }
        }
    }

    private fun publishMotion(
        fix: com.example.vio.core.sensors.GeoFix,
        settings: com.example.vio.core.settings.VioSettings,
        label: String?,
        zoneId: String?,
        zoneOffset: Double = _ui.value.zoneOffsetMeters,
        sinceFix: Double = _ui.value.sinceFixMeters,
    ) {
        val uncertainty = fix.accuracyMeters.toDouble()
        val targetNote = if (uncertainty > settings.accuracyTargetMeters) {
            "Estimate ±${uncertainty.toInt()} m. Target ±${trimMeters(settings.accuracyTargetMeters)} m is not met."
        } else {
            "Estimate ±${"%.1f".format(java.util.Locale.US, uncertainty)} m. Target ±${trimMeters(settings.accuracyTargetMeters)} m."
        }
        _ui.update {
            it.copy(
                pathMeters = pathMeters,
                activeZoneId = zoneId,
                liveZoneLabel = label ?: it.liveZoneLabel,
                zoneOffsetMeters = zoneOffset.coerceAtLeast(0.0),
                sinceFixMeters = sinceFix.coerceAtLeast(0.0),
                uncertaintyMeters = uncertainty,
                accuracyTargetMeters = settings.accuracyTargetMeters,
                imuStatus = if (sensors.snapshot().gyroAvailable) "IMU running" else "IMU unavailable",
                zoneStatus = targetNote,
            )
        }
    }

    private fun persistSession() {
        val state = _ui.value
        sessionStore.write(
            mapId,
            com.example.vio.core.session.MapSession(
                pathMeters = pathMeters,
                lastConfirmedMeters = state.liveDistanceMeters,
                lastLatitude = locator.fix?.latitude,
                lastLongitude = locator.fix?.longitude,
                zoneId = activeZoneId,
                zoneLabel = state.liveZoneLabel,
            ),
        )
    }

    private fun lockSourceAtStart(fix: com.example.vio.core.sensors.GeoFix) {
        if (!sourceDecided.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                val stored = repository.observations(mapId)
                val manifest = repository.manifest(mapId)
                if (stored.isNotEmpty() && manifest?.sourceLatitude != null && manifest.sourceLongitude != null) {
                    sourceLatitude = manifest.sourceLatitude
                    sourceLongitude = manifest.sourceLongitude
                } else if (stored.isEmpty()) {
                    repository.setSource(mapId, fix.latitude, fix.longitude)
                    sourceLatitude = fix.latitude
                    sourceLongitude = fix.longitude
                } else {
                    val origin = stored.filter { it.latitude != null && it.longitude != null }
                        .minByOrNull { it.capturedAtEpochMs }
                    if (origin?.latitude != null && origin.longitude != null) {
                        repository.setSource(mapId, origin.latitude, origin.longitude)
                        sourceLatitude = origin.latitude
                        sourceLongitude = origin.longitude
                    } else {
                        repository.setSource(mapId, fix.latitude, fix.longitude)
                        sourceLatitude = fix.latitude
                        sourceLongitude = fix.longitude
                    }
                }
                val live = livePlacement()
                val startedHere = stored.isEmpty()
                track(fix)
                _ui.update {
                    it.copy(
                        sourceSet = sourceLatitude != null,
                        liveDistanceMeters = live?.first,
                        liveZoneLabel = live?.second,
                        message = if (startedHere) "Source is here. Distance starts at 0 m." else it.message,
                    )
                }
            } catch (error: Exception) {
                sourceDecided.set(false)
                _ui.update { it.copy(error = error.message ?: "Could not set the source.") }
            }
        }
    }

    private fun zoneLength(): Double = settingsStore.read().zoneDistanceMeters

    private fun trimMeters(value: Double): String = if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()

    private fun livePlacement(lengthText: String = _ui.value.zoneLengthMeters): Pair<Double, String>? {
        val fix = locator.fix ?: return null
        val originLat = sourceLatitude
        val originLon = sourceLongitude
        val meters = if (originLat != null && originLon != null) {
            metersBetween(originLat, originLon, fix.latitude, fix.longitude)
        } else {
            0.0
        }
        val length = lengthText.toDoubleOrNull()?.takeIf { it >= 1.0 } ?: 20.0
        return meters to zoneLabelFor(meters, length)
    }

    fun deleteObservation(observationId: String) {
        viewModelScope.launch {
            try {
                repository.deleteObservation(observationId)
                refresh()
            } catch (error: Exception) {
                _ui.update { it.copy(error = error.message ?: "Could not delete that reference.") }
            }
        }
    }

    fun deleteAllReferences() {
        viewModelScope.launch {
            try {
                repository.deleteAllObservations(mapId)
                lastSavedGlobal = null
                markLatitude = null
                markLongitude = null
                sensors.markMotion()
                _ui.update { it.copy(message = "All references in this map were deleted.", error = null) }
                refresh()
            } catch (error: Exception) {
                _ui.update { it.copy(error = error.message ?: "Could not delete the references.") }
            }
        }
    }

    override fun onCleared() {
        sensors.stop()
        locator.stop()
        super.onCleared()
    }

    companion object {
        fun factory(mapId: String): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val app = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    ?: error("Application is unavailable")
                @Suppress("UNCHECKED_CAST")
                return CaptureViewModel(app as Application, mapId) as T
            }
        }
    }
}

@Composable
fun CaptureScreen(
    mapId: String,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    model: CaptureViewModel = viewModel(key = "capture-$mapId", factory = CaptureViewModel.factory(mapId)),
) {
    val ui by model.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val selected = ui.zones.firstOrNull { it.zoneId == ui.selectedZoneId }
    var label by remember { mutableStateOf("") }
    var zoneDistance by remember { mutableStateOf("0") }
    var zoneLength by remember { mutableStateOf("80") }
    var bearing by remember { mutableStateOf("") }
    var frameDistance by remember(selected?.zoneId) {
        mutableStateOf(selected?.routeDistanceMeters?.toInt()?.toString() ?: "0")
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) model.setGpsEnabled(true) else model.onCameraError("Location permission denied. References still save, without a GPS tag.")
    }
    var confirmDeleteAll by remember { mutableStateOf(false) }
    var openPhoto by remember { mutableStateOf<ObservationSummary?>(null) }
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) model.setGpsEnabled(true) else permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    LaunchedEffect(frameDistance) {
        model.setFrameDistance(frameDistance)
    }
    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ScreenHeader(ui.mapName, "References save with the current GPS fix", onBack, onSettings)
            CameraPane(
                modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(16.dp)),
                onFrame = model::onFrame,
                onCameraError = model::onCameraError,
            )
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val quality = ui.quality
                Text(
                    if (quality == null) "Waiting for a frame."
                    else if (quality.usable) "Frame is usable."
                    else quality.reasons.joinToString(" "),
                    color = when {
                        quality == null -> Mute
                        quality.usable -> Moss
                        else -> Danger
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                GpsFixLine(ui.gpsFixed, ui.gpsStatus)
                var zoneName by remember(ui.activeZoneId, ui.liveZoneLabel) { mutableStateOf(ui.liveZoneLabel.orEmpty()) }
                val showDetails = remember { AppSettings(context).read().showMapDetails }
                if (showDetails) {
                    LiveStatus(
                        listOf(
                            "Zone" to (ui.liveZoneLabel ?: "Not set"),
                            "GPS" to if (ui.gpsFixed) "Fix" else "No fix",
                            "Travelled" to formatMeters(ui.pathMeters),
                            "In this zone" to formatMeters(ui.zoneOffsetMeters),
                            "Since last fix" to formatMeters(ui.sinceFixMeters),
                            "Accuracy" to (ui.zoneStatus),
                            "IMU" to ui.imuStatus,
                        ),
                    )
                }
                if (ui.activeZoneId != null) {
                    OutlinedTextField(
                        zoneName,
                        { zoneName = it },
                        label = { Text("Zone name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedButton(
                        onClick = { model.renameActiveZone(zoneName) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Save zone name") }
                }
                Panel {
                    SettingRow(
                        title = "Save GPS",
                        detail = ui.gpsStatus,
                        checked = ui.gpsEnabled,
                        onChecked = { enabled ->
                            val granted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.ACCESS_FINE_LOCATION,
                            ) == PackageManager.PERMISSION_GRANTED
                            if (enabled && !granted) permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                            else model.setGpsEnabled(enabled)
                        },
                    )
                    if (!ui.manualFields) {
                        val liveMeters = ui.liveDistanceMeters
                        val live = if (ui.liveZoneLabel != null && liveMeters != null) {
                            "${ui.liveZoneLabel} · ${formatMeters(liveMeters)} from the start"
                        } else {
                            "Waiting for a GPS fix to set 0 m."
                        }
                        Text(live, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (ui.sourceSet) "0 m is where this map started. Every distance is measured from that point."
                            else "The first GPS fix on an empty map becomes 0 m.",
                            color = Mute,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        OutlinedButton(onClick = model::useCurrentFixAsSource, modifier = Modifier.fillMaxWidth()) {
                            Text("Set source here")
                        }
                    }
                }
                if (ui.manualFields) {
                    SectionTitle("Zones")
                    if (ui.zones.isEmpty()) Text("Add the first section of the route.", color = Mute)
                    ui.zones.forEach { zone ->
                        SelectRow(
                            title = zone.label,
                            detail = formatMeters(zone.routeDistanceMeters),
                            selected = zone.zoneId == ui.selectedZoneId,
                            onClick = { model.selectZone(zone.zoneId) },
                        )
                    }
                    Panel {
                        SectionTitle("New zone")
                        OutlinedTextField(label, { label = it }, label = { Text("Label") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                zoneDistance,
                                { zoneDistance = it },
                                label = { Text("From source, m") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                zoneLength,
                                { zoneLength = it },
                                label = { Text("Length, m") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        OutlinedTextField(bearing, { bearing = it }, label = { Text("Route bearing, optional") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedButton(
                            onClick = { model.addZone(label, zoneDistance, zoneLength, bearing) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Add zone") }
                    }
                    OutlinedTextField(
                        frameDistance,
                        { frameDistance = it },
                        label = { Text("Distance from source, m") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Button(
                    onClick = { model.saveFrame(frameDistance) },
                    enabled = !ui.busy && (ui.manualFields && selected != null || !ui.manualFields),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (ui.busy) "Saving" else "Save reference now") }
                if (ui.message != null) Notice(ui.message ?: "")
                if (ui.error != null) Notice(ui.error ?: "", alert = true)
                SectionTitle("Saved references")
                if (ui.observations.isEmpty()) {
                    Text(
                        if (ui.manualFields) "None yet. Select a zone and hold the camera on the place."
                        else "None yet. Photos appear here after a GPS fix. They stay in this map, not in the gallery.",
                        color = Mute,
                    )
                } else {
                    Text(
                        "Tap a photo to open it. These stay in the app, not in the phone gallery.",
                        color = Mute,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Panel {
                        ui.observations.forEach { observation ->
                            val gps = if (observation.latitude != null && observation.longitude != null) {
                                formatGps(observation.latitude, observation.longitude)
                            } else {
                                "No GPS yet"
                            }
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                SavedThumbnail(
                                    file = model.imageFile(observation.imageRelativePath),
                                    onClick = { openPhoto = observation },
                                )
                                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                    Text(
                                        "${observation.zoneLabel}  ·  ${formatMeters(observation.routeDistanceMeters)}",
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                    Text(gps, color = Mute, style = MaterialTheme.typography.bodyMedium)
                                }
                                TextButton(onClick = { model.deleteObservation(observation.observationId) }) {
                                    Text("Remove", color = Danger)
                                }
                            }
                        }
                    }
                    TextButton(
                        onClick = { confirmDeleteAll = true },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                    ) { Text("Delete all references", color = Danger) }
                }
            }
        }
    }
    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text("Delete all references?") },
            text = { Text("Every saved photo in this map will be removed. The zones stay.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteAll = false
                    model.deleteAllReferences()
                }) { Text("Delete all", color = Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteAll = false }) { Text("Cancel") } },
        )
    }
    val photo = openPhoto
    if (photo != null) {
        AlertDialog(
            onDismissRequest = { openPhoto = null },
            title = { Text(photo.zoneLabel) },
            text = {
                SavedThumbnail(
                    file = model.imageFile(photo.imageRelativePath),
                    onClick = {},
                    maxSide = 1080,
                    modifier = Modifier.fillMaxWidth().height(280.dp),
                )
            },
            confirmButton = { TextButton(onClick = { openPhoto = null }) { Text("Close") } },
        )
    }
}

@Composable
private fun SavedThumbnail(
    file: java.io.File,
    onClick: () -> Unit,
    maxSide: Int = 240,
    modifier: Modifier = Modifier.size(72.dp),
) {
    var bitmap by remember(file.path, maxSide) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(file.path, maxSide) {
        bitmap = withContext(Dispatchers.IO) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            var sample = 1
            while (bounds.outWidth / sample > maxSide || bounds.outHeight / sample > maxSide) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeFile(file.path, options)?.asImageBitmap()
        }
    }
    val image = bitmap
    if (image == null) {
        BoxPlaceholder(modifier)
    } else {
        Image(
            bitmap = image,
            contentDescription = "Saved reference",
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick),
        )
    }
}

@Composable
private fun BoxPlaceholder(modifier: Modifier) {
    androidx.compose.foundation.layout.Box(
        modifier.clip(RoundedCornerShape(10.dp)).background(InkHigh),
    )
}
