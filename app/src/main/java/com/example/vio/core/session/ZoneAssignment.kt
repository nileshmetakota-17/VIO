package com.example.vio.core.session

import com.example.vio.core.model.Zone
import com.example.vio.core.sensors.zoneStartMeters
import kotlin.math.abs

data class ZoneDecision(
    val zone: Zone?,
    val startMeters: Double,
    val suggestedLabel: String,
)

object ZoneAssignment {
    fun decide(distanceMeters: Double, zoneLengthMeters: Double, zones: List<Zone>): ZoneDecision {
        val length = zoneLengthMeters.coerceAtLeast(1.0)
        val distance = distanceMeters.coerceAtLeast(0.0)
        val containing = zones.filter { zone ->
            val span = zone.zoneLengthMeters.coerceAtLeast(1.0)
            distance + 0.05 >= zone.routeDistanceMeters && distance < zone.routeDistanceMeters + span
        }
        val revisit = containing.minByOrNull { abs(distance - it.routeDistanceMeters) }
            ?: zones.minByOrNull { abs(distance - it.routeDistanceMeters) }
                ?.takeIf { abs(distance - it.routeDistanceMeters) <= length / 2.0 }
        if (revisit != null) {
            return ZoneDecision(revisit, revisit.routeDistanceMeters, revisit.label)
        }
        val start = zoneStartMeters(distance, length)
        val number = (zones.maxOfOrNull { it.orderIndex } ?: -1) + 2
        return ZoneDecision(null, start, "Zone $number")
    }
}

object MotionGate {
    fun rejectAsDrift(
        movedMeters: Double,
        accuracyMeters: Float,
        turnDegrees: Float,
        driftDegrees: Float,
    ): Boolean {
        val accuracy = accuracyMeters.toDouble().coerceAtLeast(1.0)
        val impossibleJump = movedMeters > maxOf(accuracy * 4.0, 40.0)
        val spinWithoutTravel = turnDegrees >= driftDegrees && movedMeters < 1.5
        return impossibleJump || spinWithoutTravel
    }
}
