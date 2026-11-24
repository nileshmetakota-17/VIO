package com.example.vio

import com.example.vio.core.model.Zone
import com.example.vio.core.sensors.PlaceNames
import com.example.vio.core.session.MotionGate
import com.example.vio.core.session.ZoneAssignment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoneAssignmentTest {
    @Test
    fun createsTheFirstZoneFromTheConfiguredLength() {
        val decision = ZoneAssignment.decide(3.0, 20.0, emptyList())
        assertNull(decision.zone)
        assertEquals(0.0, decision.startMeters, 0.01)
        assertEquals("Zone 1", decision.suggestedLabel)
    }

    @Test
    fun revisitsTheSameBandInsteadOfCreatingADuplicate() {
        val existing = zone("a", 0.0, 20.0, 0)
        val decision = ZoneAssignment.decide(18.0, 20.0, listOf(existing))
        assertEquals("a", decision.zone?.zoneId)
        assertEquals("Gate", decision.suggestedLabel)
    }

    @Test
    fun opensTheNextBandAtTheConfiguredDistance() {
        val existing = zone("a", 0.0, 30.0, 0)
        val decision = ZoneAssignment.decide(34.0, 30.0, listOf(existing))
        assertNull(decision.zone)
        assertEquals(30.0, decision.startMeters, 0.01)
        assertEquals("Zone 2", decision.suggestedLabel)
    }

    @Test
    fun holdsAPositionJumpThatIsTooLargeForTheFix() {
        assertTrue(MotionGate.rejectAsDrift(80.0, 8f, 0f, 45f))
        assertTrue(!MotionGate.rejectAsDrift(6.0, 8f, 10f, 45f))
    }

    @Test
    fun placeNamePrefersAStreetOverANumericFeature() {
        assertEquals("Station Road", PlaceNames.readableName("Station Road", "12", null, "City"))
        assertNull(PlaceNames.readableName(null, "12", null, null))
    }

    private fun zone(id: String, start: Double, length: Double, order: Int) = Zone(
        zoneId = id,
        mapId = "map",
        label = "Gate",
        description = "",
        orderIndex = order,
        routeDistanceMeters = start,
        zoneLengthMeters = length,
        routeBearingDegrees = null,
    )
}
