package com.example.vio.feature.localize

import android.app.Application
import android.graphics.BitmapFactory
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.vio.core.camera.CameraPane
import com.example.vio.core.localization.LocalizationEngine
import com.example.vio.core.model.FeatureSet
import com.example.vio.core.model.LocalizationRequest
import com.example.vio.core.model.LocalizationResult
import com.example.vio.core.model.LocalizationState
import com.example.vio.core.model.QualityReport
import com.example.vio.core.sensors.MappingLocator
import com.example.vio.core.sensors.SensorHub
import com.example.vio.core.sensors.metersBetween
import com.example.vio.core.session.SessionStore
import com.example.vio.core.settings.AppSettings
import com.example.vio.core.storage.VioGraph
import com.example.vio.core.vision.FeatureExtractor
import com.example.vio.core.vision.ImageBuffer
import com.example.vio.core.vision.ImageQuality
import com.example.vio.ui.ConfidenceBar
import com.example.vio.ui.GpsFixLine
import com.example.vio.ui.LiveStatus
import com.example.vio.ui.MetricBlock
import com.example.vio.ui.Notice
import com.example.vio.ui.Panel
import com.example.vio.ui.ScreenHeader
import com.example.vio.ui.SectionTitle
import com.example.vio.ui.SegmentedActions
import com.example.vio.ui.StatePill
import com.example.vio.ui.formatBearing
import com.example.vio.ui.formatMeters
import com.example.vio.ui.formatScore
import com.example.vio.ui.formatSignedDegrees
import com.example.vio.ui.theme.Amber
import com.example.vio.ui.theme.InkHigh
import com.example.vio.ui.theme.Line
import com.example.vio.ui.theme.Mute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

data class LocalizeUi(
    val mapName: String = "",
    val result: LocalizationResult? = null,
    val error: String? = null,
    val zoneLabel: String? = null,
    val gpsFixed: Boolean = false,
    val gpsStatus: String = "No GPS fix",
    val travelledMeters: Double = 0.0,
    val sinceFixMeters: Double = 0.0,
    val uncertaintyMeters: Double? = null,
    val accuracyTargetMeters: Double = 2.0,
    val imuStatus: String = "Waiting for sensors",
    val relocalizeStatus: String = "Idle",
)

class LocalizeViewModel(app: Application, private val mapId: String) : AndroidViewModel(app) {
    private val repository = VioGraph.repository(app)
    private val settingsStore = AppSettings(app)
    private val sessionStore = SessionStore(app)
    private val sensors = SensorHub(app)
    private val locator = MappingLocator(app)
    private val engine = LocalizationEngine()
    private val gate = AtomicBoolean(false)
    private val _ui = MutableStateFlow(LocalizeUi())
    val ui: StateFlow<LocalizeUi> = _ui
    private var map: com.example.vio.core.model.LocalizationMap? = null
    private var yawDegrees = 0.0
    private var lastGyroNs = 0L
    private var lastLogKey: String? = null
    private var heldZone: String? = null
    private var driftReset = false
    private var pathMeters = 0.0
    private var lastTrackLat: Double? = null
    private var lastTrackLon: Double? = null

    init {
        sensors.start()
        val session = sessionStore.read(mapId)
        pathMeters = session.pathMeters
        heldZone = session.zoneLabel
        _ui.update {
            it.copy(
                zoneLabel = session.zoneLabel,
                travelledMeters = pathMeters,
                accuracyTargetMeters = settingsStore.read().accuracyTargetMeters,
            )
        }
        locator.start { fix, status ->
            if (fix != null) {
                val previousLat = lastTrackLat
                val previousLon = lastTrackLon
                if (previousLat != null && previousLon != null) {
                    val step = metersBetween(previousLat, previousLon, fix.latitude, fix.longitude)
                    if (step < maxOf(fix.accuracyMeters.toDouble() * 4.0, 40.0)) pathMeters += step
                }
                lastTrackLat = fix.latitude
                lastTrackLon = fix.longitude
            }
            _ui.update {
                it.copy(
                    gpsFixed = fix?.fromGps == true,
                    gpsStatus = status,
                    travelledMeters = pathMeters,
                    uncertaintyMeters = fix?.accuracyMeters?.toDouble(),
                )
            }
        }
        viewModelScope.launch {
            try {
                val manifest = repository.manifest(mapId)
                map = repository.loadLocalizationMap(mapId)
                _ui.update { it.copy(mapName = manifest?.name ?: "Map") }
            } catch (error: Exception) {
                _ui.update { it.copy(error = error.message ?: "Could not load the map.") }
            }
        }
    }

    fun onFrame(image: ImageBuffer) {
        val loaded = map ?: return
        if (!gate.compareAndSet(false, true)) return
        val imu = sensors.snapshot()
        if (imu.gyroZRadPerSec != null && lastGyroNs != 0L && imu.timestampNs != 0L) {
            val dt = (imu.timestampNs - lastGyroNs) / 1_000_000_000.0
            if (dt in 0.0..0.5) yawDegrees += Math.toDegrees(imu.gyroZRadPerSec * dt)
        }
        if (imu.timestampNs != 0L) lastGyroNs = imu.timestampNs
        val sessionYaw = if (imu.gyroAvailable) yawDegrees.toFloat() else null
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val quality = ImageQuality.assess(image)
                val features = if (quality.usable) FeatureExtractor.extract(image) else FeatureSet.empty()
                val request = LocalizationRequest(
                    timestampEpochMs = System.currentTimeMillis(),
                    features = features,
                    quality = quality,
                    sessionYawDegrees = sessionYaw,
                    magneticHeadingDegrees = imu.magneticHeadingDegrees,
                    gyroAvailable = imu.gyroAvailable,
                    accelAvailable = imu.accelAvailable,
                )
                val result = engine.localize(loaded, request)
                val settings = settingsStore.read()
                val turn = imu.turnSinceMarkDegrees
                val drifting = settings.autoRelocalize && heldZone != null && turn >= settings.driftDegrees &&
                    result.state != LocalizationState.LOCALIZED
                if (drifting && !driftReset) {
                    engine.reset()
                    driftReset = true
                }
                if (!drifting && (result.state == LocalizationState.LOCALIZED || result.state == LocalizationState.UNCERTAIN)) {
                    driftReset = false
                    sensors.markMotion()
                    heldZone = result.zoneLabel
                    val confirmed = result.distanceFromSourceMeters
                    val since = if (confirmed != null && sessionStore.read(mapId).lastConfirmedMeters != null) {
                        kotlin.math.abs(confirmed - sessionStore.read(mapId).lastConfirmedMeters!!)
                    } else {
                        0.0
                    }
                    sessionStore.write(
                        mapId,
                        com.example.vio.core.session.MapSession(
                            pathMeters = pathMeters,
                            lastConfirmedMeters = confirmed,
                            lastLatitude = locator.fix?.latitude,
                            lastLongitude = locator.fix?.longitude,
                            zoneId = result.zoneId,
                            zoneLabel = result.zoneLabel,
                        ),
                    )
                    val key = "${result.state}:${result.zoneId}"
                    if (key != lastLogKey && result.state != LocalizationState.INITIALIZING) {
                        lastLogKey = key
                        repository.appendLog(loaded.mapId, result)
                    }
                    _ui.update {
                        it.copy(
                            result = result,
                            zoneLabel = result.zoneLabel,
                            sinceFixMeters = since,
                            relocalizeStatus = "Confirmed from saved images",
                            imuStatus = if (imu.gyroAvailable) "IMU steady" else "IMU unavailable",
                        )
                    }
                } else {
                    if (!drifting && result.state != LocalizationState.INITIALIZING) {
                        val key = "${result.state}:${result.zoneId}"
                        if (key != lastLogKey) {
                            lastLogKey = key
                            repository.appendLog(loaded.mapId, result)
                        }
                    }
                    _ui.update {
                        it.copy(
                            result = if (drifting) result.copy(zoneLabel = heldZone) else result,
                            zoneLabel = if (drifting) heldZone else result.zoneLabel ?: heldZone,
                            relocalizeStatus = if (drifting) "IMU drift. Re-localizing from saved images." else "Searching",
                            imuStatus = if (drifting) "Drift held" else if (imu.gyroAvailable) "IMU running" else "IMU unavailable",
                        )
                    }
                }
            } catch (error: Exception) {
                _ui.update { it.copy(error = error.message ?: "Localization failed on this frame.") }
            } finally {
                gate.set(false)
            }
        }
    }

    fun onCameraError(message: String) {
        _ui.update { it.copy(error = message) }
    }

    fun referenceImage(observationId: String): java.io.File =
        repository.imageFile("maps/$mapId/images/$observationId.jpg")

    fun reset() {
        engine.reset()
        yawDegrees = 0.0
        lastGyroNs = 0L
        lastLogKey = null
        _ui.update { it.copy(result = null, error = null) }
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
                return LocalizeViewModel(app as Application, mapId) as T
            }
        }
    }
}

@Composable
fun LocalizeScreen(
    mapId: String,
    onBack: () -> Unit,
    onDiagnostics: () -> Unit,
    onSettings: () -> Unit,
    model: LocalizeViewModel = viewModel(key = "localize-$mapId", factory = LocalizeViewModel.factory(mapId)),
) {
    val ui by model.ui.collectAsStateWithLifecycle()
    val result = ui.result
    var showReference by remember { mutableStateOf(false) }
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ScreenHeader(ui.mapName.ifBlank { "Localization" }, "Camera and the on-device map", onBack, onSettings)
            SegmentedActions(
                listOf(
                    "Reset" to model::reset,
                    "Check map" to onDiagnostics,
                ),
            )
            Notice("The zone comes from saved images. GPS is shown as a tag and is not the position fix.")
            GpsFixLine(ui.gpsFixed, ui.gpsStatus)
            LiveStatus(
                listOf(
                    "Zone" to (ui.zoneLabel ?: result?.zoneLabel ?: "Searching"),
                    "GPS" to if (ui.gpsFixed) "Fix" else "No fix",
                    "Travelled" to formatMeters(ui.travelledMeters),
                    "Since last localization" to formatMeters(ui.sinceFixMeters),
                    "Accuracy" to accuracyLine(ui.uncertaintyMeters, ui.accuracyTargetMeters),
                    "IMU" to ui.imuStatus,
                    "Re-localization" to ui.relocalizeStatus,
                ),
            )
            if (ui.error != null) Notice(ui.error ?: "", alert = true)
            if (result == null) {
                Notice("Point the camera at the mapped place.")
            } else {
                Panel {
                    StatePill(result.state)
                    Text(
                        when (result.state) {
                            LocalizationState.LOST -> "No accepted zone"
                            else -> result.zoneLabel ?: "Waiting for a view"
                        },
                        style = MaterialTheme.typography.headlineLarge,
                    )
                    val matchedId = result.matchedObservationId
                    if (matchedId != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            ReferenceThumb(
                                file = model.referenceImage(matchedId),
                                onClick = { showReference = true },
                            )
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                SectionTitle("Matched reference")
                                Text(
                                    "The saved photo this frame was compared with.",
                                    color = Mute,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                    Text(result.diagnostics.message, color = Mute, style = MaterialTheme.typography.bodyMedium)
                    ConfidenceBar(result.confidence)
                }
            }
            CameraPane(
                modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(16.dp)),
                onFrame = model::onFrame,
                onCameraError = model::onCameraError,
            )
            if (result != null) {
                MetricBlock(
                    label = "Heading",
                    value = result.headingDegrees?.let(::formatSignedDegrees) ?: "Unavailable",
                    detail = result.headingReference,
                )
                if (result.mappedRouteBearingDegrees != null) {
                    Text(
                        "Mapped route bearing ${formatBearing(result.mappedRouteBearingDegrees)}. ${result.mappedRouteBearingReference}",
                        color = Mute,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                MetricBlock(
                    label = "Distance from source",
                    value = result.distanceFromSourceMeters?.let(::formatMeters) ?: "Withheld",
                    detail = if (result.distanceLowMeters != null && result.distanceHighMeters != null) {
                        "About ${formatMeters(result.distanceLowMeters)} to ${formatMeters(result.distanceHighMeters)}. ${result.distanceReference}"
                    } else {
                        result.distanceReference
                    },
                )
                if (result.diagnostics.magneticHeadingDegrees != null || result.diagnostics.sessionYawDegrees != null) {
                    Panel {
                        SectionTitle("Sensors")
                        if (result.diagnostics.magneticHeadingDegrees != null) {
                            Text(
                                "Magnetic heading ${formatBearing(result.diagnostics.magneticHeadingDegrees)} is an uncalibrated reading, not the localization heading.",
                                color = Mute,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        if (result.diagnostics.sessionYawDegrees != null) {
                            Text(
                                "Gyroscope yaw since this screen opened is ${formatSignedDegrees(result.diagnostics.sessionYawDegrees)}. It drifts and is not a position.",
                                color = Mute,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
                Panel(modifier = Modifier.padding(bottom = 24.dp)) {
                    SectionTitle("Candidates")
                    Text(
                        if (result.state == LocalizationState.LOCALIZED) "Similarity scores, not probabilities."
                        else "Not an accepted location. Scores are not probabilities.",
                        color = Mute,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    result.alternatives.forEach { candidate ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(candidate.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            Text(formatScore(candidate.score), fontFamily = FontFamily.Monospace, color = Amber)
                            Text(
                                formatMeters(candidate.routeDistanceMeters),
                                fontFamily = FontFamily.Monospace,
                                color = Mute,
                                modifier = Modifier.padding(start = 16.dp),
                            )
                        }
                    }
                    HorizontalDivider(color = Line)
                    Text(
                        "Retrieval ${result.diagnostics.retrievalMs} ms  ·  Verify ${result.diagnostics.verifyMs} ms  ·  Inliers ${result.diagnostics.inlierCount}/${result.diagnostics.matchCount}",
                        color = Mute,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
    val matchedId = result?.matchedObservationId
    if (showReference && result != null && matchedId != null) {
        AlertDialog(
            onDismissRequest = { showReference = false },
            title = { Text(result.zoneLabel ?: "Matched reference") },
            text = {
                ReferenceThumb(
                    file = model.referenceImage(matchedId),
                    onClick = {},
                    maxSide = 1080,
                    modifier = Modifier.fillMaxWidth().height(240.dp),
                )
            },
            confirmButton = { TextButton(onClick = { showReference = false }) { Text("Close") } },
        )
    }
}

private fun accuracyLine(estimate: Double?, target: Double): String {
    val targetText = if (target % 1.0 == 0.0) target.toInt().toString() else target.toString()
    if (estimate == null) return "No fix. Target ±$targetText m"
    val shown = String.format(java.util.Locale.US, "%.1f", estimate)
    return if (estimate > target) {
        "±$shown m estimate. Target ±$targetText m is not met."
    } else {
        "±$shown m estimate. Within the ±$targetText m target."
    }
}

@Composable
private fun ReferenceThumb(
    file: java.io.File,
    onClick: () -> Unit,
    maxSide: Int = 240,
    modifier: Modifier = Modifier.size(84.dp),
) {
    var bitmap by remember(file.path, maxSide) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(file.path, maxSide) {
        bitmap = withContext(Dispatchers.IO) {
            if (!file.isFile) return@withContext null
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
        androidx.compose.foundation.layout.Box(
            modifier.clip(RoundedCornerShape(12.dp)).background(InkHigh),
        )
    } else {
        Image(
            bitmap = image,
            contentDescription = "Matched reference",
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick),
        )
    }
}
