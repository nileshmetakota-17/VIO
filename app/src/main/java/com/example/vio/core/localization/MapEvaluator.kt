package com.example.vio.core.localization

import com.example.vio.core.model.LocalizationMap
import com.example.vio.core.model.LocalizationRequest
import com.example.vio.core.model.LocalizationState
import com.example.vio.core.model.QualityReport

data class EvaluationReport(
    val evaluated: Int,
    val skippedSingleView: Int,
    val retrievalTop1: Int,
    val retrievalTop3: Int,
    val acceptedCorrect: Int,
    val falseLocalizations: Int,
    val abstained: Int,
    val notes: String,
) {
    fun retrievalTop1Rate(): Float = rate(retrievalTop1)
    fun falseLocalizationRate(): Float = rate(falseLocalizations)

    private fun rate(count: Int): Float = if (evaluated == 0) 0f else count.toFloat() / evaluated

    fun summary(): String = buildString {
        appendLine("Leave-one-out on stored descriptors")
        appendLine("Evaluated views: $evaluated")
        appendLine("Skipped (zone has only one view): $skippedSingleView")
        appendLine("Retrieval top-1: $retrievalTop1 / $evaluated")
        appendLine("Retrieval top-3: $retrievalTop3 / $evaluated")
        appendLine("Accepted and correct: $acceptedCorrect")
        appendLine("False localizations: $falseLocalizations")
        appendLine("Abstained: $abstained")
        append(notes)
    }
}

object MapEvaluator {
    private val usable = QualityReport(
        usable = true,
        brightness = 128f,
        blurVariance = 200f,
        texture = 30f,
        reasons = emptyList(),
    )

    fun leaveOneOut(map: LocalizationMap): EvaluationReport {
        val byZone = map.observations.groupBy { it.zoneId }
        var evaluated = 0
        var skipped = 0
        var top1 = 0
        var top3 = 0
        var acceptedCorrect = 0
        var falseLocalizations = 0
        var abstained = 0
        val retriever = ZoneRetriever()
        for (query in map.observations) {
            val siblings = byZone[query.zoneId].orEmpty().count { it.observationId != query.observationId }
            if (siblings == 0) {
                skipped += 1
                continue
            }
            evaluated += 1
            val reduced = map.copy(
                observations = map.observations.filter { it.observationId != query.observationId },
            )
            val ranked = retriever.rank(reduced, query.features)
            val topIds = ranked.take(3).map { it.zoneId }
            if (ranked.firstOrNull()?.zoneId == query.zoneId) top1 += 1
            if (query.zoneId in topIds) top3 += 1
            val engine = LocalizationEngine()
            val first = request(query.features, 1L)
            engine.localize(reduced, first)
            val result = engine.localize(reduced, first.copy(timestampEpochMs = 2L))
            when {
                result.state == LocalizationState.LOCALIZED && result.zoneId == query.zoneId -> acceptedCorrect += 1
                result.state == LocalizationState.LOCALIZED -> falseLocalizations += 1
                else -> abstained += 1
            }
        }
        return EvaluationReport(
            evaluated = evaluated,
            skippedSingleView = skipped,
            retrievalTop1 = top1,
            retrievalTop3 = top3,
            acceptedCorrect = acceptedCorrect,
            falseLocalizations = falseLocalizations,
            abstained = abstained,
            notes = "This compares stored descriptors after removing the query view. " +
                "It is not a field-accuracy result, and near-duplicate references can make it look better than a new journey.",
        )
    }

    private fun request(features: com.example.vio.core.model.FeatureSet, time: Long) = LocalizationRequest(
        timestampEpochMs = time,
        features = features,
        quality = usable,
        sessionYawDegrees = null,
        magneticHeadingDegrees = null,
        gyroAvailable = false,
        accelAvailable = false,
    )
}
