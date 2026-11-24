package com.example.vio.core.model

const val MAP_SCHEMA_VERSION = 1
const val DESCRIPTOR_TYPE = "grid-hog-brief-v1"
const val DESCRIPTOR_MODEL_VERSION = "1"

enum class LocalizationState {
    INITIALIZING,
    LOCALIZED,
    UNCERTAIN,
    LOST,
}

class MapFormatException(message: String) : IllegalArgumentException(message)

data class MapManifest(
    val mapId: String,
    val name: String,
    val schemaVersion: Int,
    val createdAtEpochMs: Long,
    val sourceLabel: String,
    val sourceLatitude: Double?,
    val sourceLongitude: Double?,
    val notes: String,
)

data class Zone(
    val zoneId: String,
    val mapId: String,
    val label: String,
    val description: String,
    val orderIndex: Int,
    val routeDistanceMeters: Double,
    val zoneLengthMeters: Double,
    val routeBearingDegrees: Float?,
)

data class ZoneRelation(
    val mapId: String,
    val fromZoneId: String,
    val toZoneId: String,
)

/** Mapping-time record. Coordinates here are never a runtime localization input. */
data class ReferenceObservation(
    val observationId: String,
    val mapId: String,
    val zoneId: String,
    val sessionId: String,
    val imageRelativePath: String,
    val capturedAtEpochMs: Long,
    val latitude: Double?,
    val longitude: Double?,
    val horizontalAccuracyMeters: Float?,
    val routeDistanceMeters: Double,
    val viewBearingDegrees: Float?,
    val qualityScore: Float,
    val conditionsTag: String,
)

class Keypoint(
    val x: Float,
    val y: Float,
    val angle: Float,
    val descriptor: ByteArray,
)

class FeatureSet(
    val width: Int,
    val height: Int,
    val globalDescriptor: ByteArray,
    val keypoints: List<Keypoint>,
) {
    companion object {
        fun empty(): FeatureSet = FeatureSet(0, 0, ByteArray(0), emptyList())
    }
}

/** Runtime reference. Mapping coordinates are intentionally absent. */
class LocalizationReference(
    val observationId: String,
    val zoneId: String,
    val routeDistanceMeters: Double,
    val viewBearingDegrees: Float?,
    val features: FeatureSet,
)

data class LocalizationMap(
    val mapId: String,
    val name: String,
    val schemaVersion: Int,
    val sourceLabel: String,
    val zones: List<Zone>,
    val relations: List<ZoneRelation>,
    val observations: List<LocalizationReference>,
)

data class QualityReport(
    val usable: Boolean,
    val brightness: Float,
    val blurVariance: Float,
    val texture: Float,
    val reasons: List<String>,
)

data class ZoneCandidate(
    val zoneId: String,
    val label: String,
    val score: Float,
    val routeDistanceMeters: Double,
)

data class LocalizationDiagnostics(
    val retrievalMs: Long,
    val verifyMs: Long,
    val bestScore: Float,
    val secondScore: Float?,
    val inlierCount: Int,
    val inlierRatio: Float,
    val matchCount: Int,
    val qualityReasons: List<String>,
    val temporalAgreement: Float,
    val frameCount: Int,
    val message: String,
    val magneticHeadingDegrees: Float?,
    val sessionYawDegrees: Float?,
    val gyroAvailable: Boolean,
    val accelAvailable: Boolean,
    val descriptorType: String,
)

data class LocalizationResult(
    val timestampEpochMs: Long,
    val state: LocalizationState,
    val zoneId: String?,
    val zoneLabel: String?,
    val confidence: Float,
    val alternatives: List<ZoneCandidate>,
    val headingDegrees: Float?,
    val headingReference: String,
    val mappedRouteBearingDegrees: Float?,
    val mappedRouteBearingReference: String,
    val distanceFromSourceMeters: Double?,
    val distanceLowMeters: Double?,
    val distanceHighMeters: Double?,
    val distanceReference: String,
    val diagnostics: LocalizationDiagnostics,
    val matchedObservationId: String? = null,
)

data class LocalizationRequest(
    val timestampEpochMs: Long,
    val features: FeatureSet,
    val quality: QualityReport,
    val sessionYawDegrees: Float?,
    val magneticHeadingDegrees: Float?,
    val gyroAvailable: Boolean,
    val accelAvailable: Boolean,
)

data class MapSummary(
    val mapId: String,
    val name: String,
    val sourceLabel: String,
    val zoneCount: Int,
    val observationCount: Int,
    val createdAtEpochMs: Long,
)

data class ObservationSummary(
    val observationId: String,
    val zoneId: String,
    val zoneLabel: String,
    val routeDistanceMeters: Double,
    val capturedAtEpochMs: Long,
    val latitude: Double?,
    val longitude: Double?,
    val hasMappingGps: Boolean,
    val imageRelativePath: String,
)

enum class IssueSeverity { ERROR, WARNING }

data class MapIssue(
    val severity: IssueSeverity,
    val message: String,
)
