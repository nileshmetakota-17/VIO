package com.example.vio.core.localization

import com.example.vio.core.model.DESCRIPTOR_TYPE
import com.example.vio.core.model.FeatureSet
import com.example.vio.core.model.LocalizationDiagnostics
import com.example.vio.core.model.LocalizationMap
import com.example.vio.core.model.LocalizationReference
import com.example.vio.core.model.LocalizationRequest
import com.example.vio.core.model.LocalizationResult
import com.example.vio.core.model.LocalizationState
import com.example.vio.core.model.Zone
import com.example.vio.core.model.ZoneCandidate
import com.example.vio.core.model.ZoneRelation
import com.example.vio.core.vision.DescriptorMath
import com.example.vio.core.vision.GeometricResult
import com.example.vio.core.vision.GeometricVerifier
import kotlin.math.max

data class ZoneMatch(
    val zoneId: String,
    val label: String,
    val score: Float,
    val routeDistanceMeters: Double,
    val reference: LocalizationReference,
)

class ZoneRetriever {
    fun rank(map: LocalizationMap, features: FeatureSet): List<ZoneMatch> {
        if (features.globalDescriptor.isEmpty()) return emptyList()
        val labels = map.zones.associate { it.zoneId to it.label }
        return map.observations
            .groupBy { it.zoneId }
            .mapNotNull { (zoneId, observations) ->
                val scored = observations
                    .map { observation ->
                        observation to DescriptorMath.similarity(
                            features.globalDescriptor,
                            observation.features.globalDescriptor,
                        )
                    }
                    .sortedByDescending { it.second }
                val best = scored.firstOrNull() ?: return@mapNotNull null
                val second = scored.getOrNull(1)?.second
                val combined = if (second == null) best.second else 0.8f * best.second + 0.2f * second
                ZoneMatch(
                    zoneId = zoneId,
                    label = labels[zoneId] ?: zoneId,
                    score = combined,
                    routeDistanceMeters = best.first.routeDistanceMeters,
                    reference = best.first,
                )
            }
            .sortedByDescending { it.score }
    }
}

data class TemporalDecision(
    val winner: String?,
    val agreement: Float,
    val stable: Boolean,
    val frames: Int,
    val consecutiveMisses: Int,
)

class TemporalFilter {
    private val votes = ArrayDeque<String?>()
    private var previousZone: String? = null
    var consecutiveMisses: Int = 0
        private set

    fun reset() {
        votes.clear()
        previousZone = null
        consecutiveMisses = 0
    }

    fun observe(zoneId: String?, score: Float, relations: List<ZoneRelation>): TemporalDecision {
        val penalized = penalize(zoneId, score, relations)
        val vote = if (zoneId != null && penalized >= LocalizationPolicy.VOTE_SCORE) zoneId else null
        if (vote == null) consecutiveMisses += 1 else consecutiveMisses = 0
        votes.addLast(vote)
        while (votes.size > 5) votes.removeFirst()
        val counts = votes.filterNotNull().groupingBy { it }.eachCount()
        val winner = counts.maxByOrNull { it.value }?.key
        val agreement = if (votes.isEmpty() || winner == null) 0f else counts.getValue(winner).toFloat() / votes.size
        val stable = winner != null && counts.getValue(winner) >= 2 && agreement >= 0.6f
        if (stable) previousZone = winner
        return TemporalDecision(winner, agreement, stable, votes.size, consecutiveMisses)
    }

    private fun penalize(zoneId: String?, score: Float, relations: List<ZoneRelation>): Float {
        if (zoneId == null) return 0f
        val previous = previousZone ?: return score
        if (zoneId == previous) return score
        val neighbor = relations.any { relation ->
            (relation.fromZoneId == previous && relation.toZoneId == zoneId) ||
                (relation.toZoneId == previous && relation.fromZoneId == zoneId)
        }
        return if (neighbor) score else score * 0.72f
    }
}

object LocalizationPolicy {
    const val VOTE_SCORE = 0.36f
    const val MIN_CONFIDENCE = 0.56f
    const val MIN_BEST_SCORE = 0.52f
    const val APPEARANCE_SCORE = 0.80f
    const val APPEARANCE_MARGIN = 0.12f
    const val UNCERTAIN_SCORE = 0.40f

    fun confidence(
        best: Float,
        margin: Float,
        inlierRatio: Float,
        inlierCount: Int,
        agreement: Float,
        quality: Float,
    ): Float {
        val marginScore = (margin / 0.20f).coerceIn(0f, 1f)
        val geometry = if (inlierCount >= GeometricVerifier.MIN_INLIERS) inlierRatio.coerceIn(0f, 1f) else 0f
        return (
            0.40f * best +
                0.22f * marginScore +
                0.20f * geometry +
                0.12f * agreement +
                0.06f * quality
            ).coerceIn(0f, 1f)
    }

    fun decide(
        qualityUsable: Boolean,
        frames: Int,
        stable: Boolean,
        bestScore: Float,
        margin: Float,
        inlierCount: Int,
        inlierRatio: Float,
        hasCandidate: Boolean,
        consecutiveMisses: Int,
        confidence: Float,
    ): LocalizationState {
        if (!qualityUsable) {
            return when {
                consecutiveMisses >= 4 -> LocalizationState.LOST
                else -> LocalizationState.UNCERTAIN
            }
        }
        if (!hasCandidate) return LocalizationState.LOST
        if (frames < 2) return LocalizationState.INITIALIZING
        val geometryOk = inlierCount >= GeometricVerifier.MIN_INLIERS && inlierRatio >= GeometricVerifier.MIN_INLIER_RATIO
        val appearanceOk = bestScore >= APPEARANCE_SCORE && margin >= APPEARANCE_MARGIN
        val confident = confidence >= MIN_CONFIDENCE && bestScore >= MIN_BEST_SCORE && (geometryOk || appearanceOk)
        if (stable && confident) return LocalizationState.LOCALIZED
        if (consecutiveMisses >= 4 && bestScore < UNCERTAIN_SCORE) return LocalizationState.LOST
        if (bestScore >= UNCERTAIN_SCORE || hasCandidate) return LocalizationState.UNCERTAIN
        return LocalizationState.LOST
    }
}

data class DistanceEstimate(
    val meters: Double,
    val lowMeters: Double,
    val highMeters: Double,
)

object PoseEstimator {
    const val HEADING_REFERENCE =
        "Degrees clockwise of the matched reference view, from verified feature geometry. This is not a compass bearing."
    const val HEADING_UNAVAILABLE =
        "View-relative heading is unavailable because geometric verification was too weak."
    const val ROUTE_BEARING_REFERENCE =
        "Bearing stored for the mapped route through this zone. It is not a live compass reading."
    const val DISTANCE_REFERENCE =
        "Approximate metres along the mapped route from the source anchor, taken from the matched reference. A single camera frame does not provide metric scale."
    const val DISTANCE_WITHHELD =
        "Distance is withheld until a zone is stable enough to show a range."

    fun distance(zone: Zone, routeMeters: Double, state: LocalizationState): DistanceEstimate? {
        if (state != LocalizationState.LOCALIZED && state != LocalizationState.UNCERTAIN) return null
        val length = max(zone.zoneLengthMeters, 20.0)
        val extra = if (state == LocalizationState.UNCERTAIN) length else 0.0
        val radius = length / 2.0 + extra
        return DistanceEstimate(
            meters = routeMeters,
            lowMeters = (routeMeters - radius).coerceAtLeast(0.0),
            highMeters = routeMeters + radius,
        )
    }
}

class LocalizationEngine(
    private val retriever: ZoneRetriever = ZoneRetriever(),
    private val verifier: GeometricVerifier = GeometricVerifier(),
    private val temporal: TemporalFilter = TemporalFilter(),
) {
    fun reset() {
        synchronized(this) { temporal.reset() }
    }

    fun localize(map: LocalizationMap, request: LocalizationRequest): LocalizationResult {
        synchronized(this) {
            val timestamp = request.timestampEpochMs
            if (map.observations.isEmpty() || map.zones.isEmpty()) {
                temporal.reset()
                return result(
                    request = request,
                    state = LocalizationState.LOST,
                    zone = null,
                    confidence = 0f,
                    alternatives = emptyList(),
                    heading = null,
                    distance = null,
                    retrievalMs = 0,
                    verifyMs = 0,
                    bestScore = 0f,
                    secondScore = null,
                    geometric = GeometricResult.NONE,
                    agreement = 0f,
                    frames = 0,
                    message = "This map has no reference images.",
                )
            }
            if (!request.quality.usable) {
                val decision = temporal.observe(null, 0f, map.relations)
                val state = LocalizationPolicy.decide(
                    qualityUsable = false,
                    frames = decision.frames,
                    stable = false,
                    bestScore = 0f,
                    margin = 0f,
                    inlierCount = 0,
                    inlierRatio = 0f,
                    hasCandidate = false,
                    consecutiveMisses = decision.consecutiveMisses,
                    confidence = 0f,
                )
                return result(
                    request = request,
                    state = state,
                    zone = null,
                    confidence = 0f,
                    alternatives = emptyList(),
                    heading = null,
                    distance = null,
                    retrievalMs = 0,
                    verifyMs = 0,
                    bestScore = 0f,
                    secondScore = null,
                    geometric = GeometricResult.NONE,
                    agreement = decision.agreement,
                    frames = decision.frames,
                    message = request.quality.reasons.joinToString(" ").ifBlank { "The frame is not usable." },
                )
            }

            val started = System.nanoTime()
            val ranked = retriever.rank(map, request.features)
            val retrievalMs = (System.nanoTime() - started) / 1_000_000
            if (ranked.isEmpty()) {
                val decision = temporal.observe(null, 0f, map.relations)
                return result(
                    request = request,
                    state = LocalizationState.LOST,
                    zone = null,
                    confidence = 0f,
                    alternatives = emptyList(),
                    heading = null,
                    distance = null,
                    retrievalMs = retrievalMs,
                    verifyMs = 0,
                    bestScore = 0f,
                    secondScore = null,
                    geometric = GeometricResult.NONE,
                    agreement = decision.agreement,
                    frames = decision.frames,
                    message = "No reference descriptor could be compared.",
                )
            }

            val best = ranked.first()
            val second = ranked.getOrNull(1)
            val margin = if (second == null) (best.score - 0.55f).coerceAtLeast(0f) else best.score - second.score
            val decision = temporal.observe(best.zoneId, best.score, map.relations)
            val focus = ranked.firstOrNull { it.zoneId == decision.winner } ?: best
            val verifyStarted = System.nanoTime()
            val geometric = verifier.verify(request.features, focus.reference.features)
            val verifyMs = (System.nanoTime() - verifyStarted) / 1_000_000
            val confidence = LocalizationPolicy.confidence(
                best = best.score,
                margin = margin,
                inlierRatio = geometric.inlierRatio,
                inlierCount = geometric.inlierCount,
                agreement = decision.agreement,
                quality = 1f,
            )
            val state = LocalizationPolicy.decide(
                qualityUsable = true,
                frames = decision.frames,
                stable = decision.stable,
                bestScore = best.score,
                margin = margin,
                inlierCount = geometric.inlierCount,
                inlierRatio = geometric.inlierRatio,
                hasCandidate = decision.winner != null,
                consecutiveMisses = decision.consecutiveMisses,
                confidence = confidence,
            )
            val zone = if (state == LocalizationState.LOST) {
                null
            } else {
                map.zones.firstOrNull { it.zoneId == focus.zoneId }
            }
            val distance = zone?.let { PoseEstimator.distance(it, focus.routeDistanceMeters, state) }
            val heading = if (zone != null && (state == LocalizationState.LOCALIZED || state == LocalizationState.UNCERTAIN)) {
                geometric.rotationDegrees
            } else {
                null
            }
            val message = when (state) {
                LocalizationState.LOCALIZED -> "Localized to ${zone?.label ?: focus.label}."
                LocalizationState.INITIALIZING -> "Leading candidate is ${focus.label}. Waiting for another agreeing frame."
                LocalizationState.UNCERTAIN -> "Evidence points toward ${zone?.label ?: focus.label}, but it is not strong enough to accept."
                LocalizationState.LOST -> "Closest stored zone was ${focus.label}, and the evidence was not strong enough to accept it."
            }
            return result(
                request = request,
                state = state,
                zone = zone,
                confidence = confidence,
                alternatives = ranked.take(3).map {
                    ZoneCandidate(it.zoneId, it.label, it.score, it.routeDistanceMeters)
                },
                heading = heading,
                distance = distance,
                retrievalMs = retrievalMs,
                verifyMs = verifyMs,
                bestScore = best.score,
                secondScore = second?.score,
                geometric = geometric,
                agreement = decision.agreement,
                frames = decision.frames,
                message = message,
                matchedObservationId = focus.reference.observationId,
            )
        }
    }

    private fun result(
        request: LocalizationRequest,
        state: LocalizationState,
        zone: Zone?,
        confidence: Float,
        alternatives: List<ZoneCandidate>,
        heading: Float?,
        distance: DistanceEstimate?,
        retrievalMs: Long,
        verifyMs: Long,
        bestScore: Float,
        secondScore: Float?,
        geometric: GeometricResult,
        agreement: Float,
        frames: Int,
        message: String,
        matchedObservationId: String? = null,
    ): LocalizationResult {
        val showRouteBearing = zone != null && state != LocalizationState.LOST
        return LocalizationResult(
            timestampEpochMs = request.timestampEpochMs,
            state = state,
            zoneId = zone?.zoneId,
            zoneLabel = zone?.label,
            confidence = confidence,
            alternatives = alternatives,
            headingDegrees = heading,
            headingReference = if (heading != null) PoseEstimator.HEADING_REFERENCE else PoseEstimator.HEADING_UNAVAILABLE,
            mappedRouteBearingDegrees = if (showRouteBearing) zone?.routeBearingDegrees else null,
            mappedRouteBearingReference = PoseEstimator.ROUTE_BEARING_REFERENCE,
            distanceFromSourceMeters = distance?.meters,
            distanceLowMeters = distance?.lowMeters,
            distanceHighMeters = distance?.highMeters,
            distanceReference = if (distance != null) PoseEstimator.DISTANCE_REFERENCE else PoseEstimator.DISTANCE_WITHHELD,
            diagnostics = LocalizationDiagnostics(
                retrievalMs = retrievalMs,
                verifyMs = verifyMs,
                bestScore = bestScore,
                secondScore = secondScore,
                inlierCount = geometric.inlierCount,
                inlierRatio = geometric.inlierRatio,
                matchCount = geometric.matchCount,
                qualityReasons = request.quality.reasons,
                temporalAgreement = agreement,
                frameCount = frames,
                message = message,
                magneticHeadingDegrees = request.magneticHeadingDegrees,
                sessionYawDegrees = request.sessionYawDegrees,
                gyroAvailable = request.gyroAvailable,
                accelAvailable = request.accelAvailable,
                descriptorType = DESCRIPTOR_TYPE,
            ),
            matchedObservationId = matchedObservationId,
        )
    }
}
