package com.example.vio

import com.example.vio.core.localization.LocalizationEngine
import com.example.vio.core.localization.LocalizationPolicy
import com.example.vio.core.localization.MapEvaluator
import com.example.vio.core.localization.TemporalFilter
import com.example.vio.core.map.DemoMaps
import com.example.vio.core.map.MapPackage
import com.example.vio.core.map.MapValidator
import com.example.vio.core.map.PackageManifest
import com.example.vio.core.map.PackageObservation
import com.example.vio.core.map.StoredMapSnapshot
import com.example.vio.core.map.ZoneTopology
import com.example.vio.core.model.DESCRIPTOR_MODEL_VERSION
import com.example.vio.core.model.DESCRIPTOR_TYPE
import com.example.vio.core.model.FeatureSet
import com.example.vio.core.model.IssueSeverity
import com.example.vio.core.model.Keypoint
import com.example.vio.core.model.LocalizationRequest
import com.example.vio.core.model.MapFormatException
import com.example.vio.core.model.LocalizationState
import com.example.vio.core.model.MAP_SCHEMA_VERSION
import com.example.vio.core.model.MapManifest
import com.example.vio.core.model.QualityReport
import com.example.vio.core.model.Zone
import com.example.vio.core.model.ZoneRelation
import com.example.vio.core.vision.DescriptorLayout
import com.example.vio.core.vision.DescriptorMath
import com.example.vio.core.vision.FeatureCodec
import com.example.vio.core.vision.FeatureExtractor
import com.example.vio.core.vision.ImageBuffer
import com.example.vio.core.vision.ImageQuality
import com.example.vio.core.vision.SyntheticScenes
import com.example.vio.core.vision.applyHomography
import com.example.vio.core.vision.estimateHomography
import com.example.vio.core.vision.solveLinear
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class LocalizationPipelineTest {
    @Test
    fun flatAndExtremeFramesAreRejected() {
        val flat = ImageQuality.assess(SyntheticScenes.flat(160, 120, 128))
        val dark = ImageQuality.assess(SyntheticScenes.flat(160, 120, 0))
        val bright = ImageQuality.assess(SyntheticScenes.flat(160, 120, 255))
        val bars = ImageQuality.assess(SyntheticScenes.verticalBars(160, 120, 16))
        assertFalse(flat.usable)
        assertFalse(dark.usable)
        assertFalse(bright.usable)
        assertTrue("bars variance=${bars.blurVariance} texture=${bars.texture}", bars.usable)
    }

    @Test
    fun shiftedPatternStaysCloserThanADifferentPattern() {
        val bars = FeatureExtractor.extract(SyntheticScenes.verticalBars(160, 120, 16))
        val shifted = FeatureExtractor.extract(SyntheticScenes.verticalBars(160, 120, 16).translate(6, 2).brighten(16))
        val checker = FeatureExtractor.extract(SyntheticScenes.checker(160, 120, 14))
        val same = DescriptorMath.similarity(bars.globalDescriptor, bars.globalDescriptor)
        val near = DescriptorMath.similarity(bars.globalDescriptor, shifted.globalDescriptor)
        val far = DescriptorMath.similarity(bars.globalDescriptor, checker.globalDescriptor)
        assertEquals(1f, same, 0.001f)
        assertTrue("near=$near far=$far", near > far)
    }

    @Test
    fun darkenedCopyStaysCloserThanADifferentPattern() {
        val bars = SyntheticScenes.verticalBars(160, 120, 16)
        val dark = ImageBuffer(
            bars.width,
            bars.height,
            ByteArray(bars.pixels.size) { index -> ((bars.pixels[index].toInt() and 0xFF) / 3).toByte() },
        )
        val same = FeatureExtractor.extract(bars)
        val dim = FeatureExtractor.extract(dark)
        val other = FeatureExtractor.extract(SyntheticScenes.checker(160, 120, 14))
        val near = DescriptorMath.similarity(same.globalDescriptor, dim.globalDescriptor)
        val far = DescriptorMath.similarity(same.globalDescriptor, other.globalDescriptor)
        assertTrue("near=$near far=$far", near > far)
    }

    @Test
    fun linearSolverAndHomographyHandleTranslation() {
        val solved = solveLinear(
            arrayOf(doubleArrayOf(2.0, 1.0), doubleArrayOf(1.0, -1.0)),
            doubleArrayOf(5.0, 1.0),
        )
        assertEquals(2.0, solved!![0], 1e-6)
        assertEquals(1.0, solved[1], 1e-6)
        val src = listOf(10.0 to 10.0, 110.0 to 10.0, 110.0 to 110.0, 10.0 to 110.0)
        val dst = src.map { (it.first + 10.0) to (it.second + 15.0) }
        val model = estimateHomography(src, dst)
        val projected = applyHomography(model!!, 50.0, 40.0)
        assertEquals(60.0, projected!!.first, 0.2)
        assertEquals(55.0, projected.second, 0.2)
    }

    @Test
    fun descriptorCodecRoundTrips() {
        val original = FeatureSet(
            16,
            12,
            ByteArray(DescriptorLayout.GLOBAL_BYTES) { (it % 16).toByte() },
            listOf(Keypoint(3f, 4f, 0.25f, ByteArray(DescriptorLayout.BRIEF_BYTES) { 7 })),
        )
        val decoded = FeatureCodec.decode(FeatureCodec.encode(original))
        assertEquals(16, decoded.width)
        assertEquals(3f, decoded.keypoints.single().x, 0.001f)
        assertTrue(original.globalDescriptor.contentEquals(decoded.globalDescriptor))
        assertTrue(original.keypoints.single().descriptor.contentEquals(decoded.keypoints.single().descriptor))
    }

    @Test
    fun temporalFilterKeepsASingleOutlierFromTakingOver() {
        val filter = TemporalFilter()
        val relations = listOf(ZoneRelation("m", "a", "b"))
        filter.observe("a", 0.9f, relations)
        filter.observe("a", 0.9f, relations)
        filter.observe("a", 0.9f, relations)
        val outlier = filter.observe("c", 0.9f, relations)
        val back = filter.observe("a", 0.9f, relations)
        assertEquals("a", outlier.winner)
        assertEquals("a", back.winner)
        assertTrue(back.stable)
    }

    @Test
    fun policyDoesNotAcceptTheFirstFrame() {
        val state = LocalizationPolicy.decide(
            qualityUsable = true,
            frames = 1,
            stable = false,
            bestScore = 0.95f,
            margin = 0.4f,
            inlierCount = 20,
            inlierRatio = 0.9f,
            hasCandidate = true,
            consecutiveMisses = 0,
            confidence = 0.9f,
        )
        assertEquals(LocalizationState.INITIALIZING, state)
    }

    @Test
    fun repeatedMatchingViewBecomesLocalizedOnTheCorrectZone() {
        val bundle = DemoMaps.build("demo", 1L)
        val map = DemoMaps.localizationMap(bundle)
        val query = bundle.shots.first { it.observationId == "demo-a2" }
        val engine = LocalizationEngine()
        val first = request(query.features, 10L, magnetic = 10f)
        val second = request(query.features, 20L, magnetic = 200f)
        engine.localize(map, first)
        val result = engine.localize(map, second)
        assertEquals(LocalizationState.LOCALIZED, result.state)
        assertEquals("demo-a", result.zoneId)
        assertEquals(45.0, result.distanceFromSourceMeters!!, 0.1)
        val again = engine.localize(map, second.copy(magneticHeadingDegrees = 5f))
        assertEquals(result.zoneId, again.zoneId)
        assertEquals(result.distanceFromSourceMeters, again.distanceFromSourceMeters)
        assertEquals(result.confidence, again.confidence, 0.0001f)
    }

    @Test
    fun unrelatedAndEmptyInputsAreNotForced() {
        val bundle = DemoMaps.build("demo", 1L)
        val map = DemoMaps.localizationMap(bundle)
        val diagonal = FeatureExtractor.extract(SyntheticScenes.diagonal(160, 120, 11))
        val engine = LocalizationEngine()
        var last = engine.localize(map, request(diagonal, 1L, magnetic = null))
        repeat(4) { index ->
            last = engine.localize(map, request(diagonal, (index + 2).toLong(), magnetic = null))
        }
        assertNotEquals(LocalizationState.LOCALIZED, last.state)

        val flat = ImageQuality.assess(SyntheticScenes.flat(80, 60, 20))
        val lostEngine = LocalizationEngine()
        val poor = lostEngine.localize(
            map,
            LocalizationRequest(1L, FeatureSet.empty(), flat, null, null, false, false),
        )
        assertNotEquals(LocalizationState.LOCALIZED, poor.state)

        val empty = map.copy(observations = emptyList())
        val emptyResult = LocalizationEngine().localize(empty, request(diagonal, 1L, null))
        assertEquals(LocalizationState.LOST, emptyResult.state)
        assertNull(emptyResult.zoneId)
    }

    @Test
    fun leaveOneOutDoesNotFalselyLocalizeTheDemo() {
        val report = MapEvaluator.leaveOneOut(DemoMaps.localizationMap(DemoMaps.build("demo", 1L)))
        assertEquals(6, report.evaluated)
        assertEquals(report.evaluated, report.retrievalTop1)
        assertEquals(0, report.falseLocalizations)
        assertTrue(report.notes.contains("not a field-accuracy"))
    }

    @Test
    fun runtimeTypesDoNotCarryMappingCoordinates() {
        val forbidden = listOf("lat", "lon", "gps", "gnss")
        val banned = Regex("(^|_)(latitude|longitude|gps|gnss)($|_)")
        for (type in listOf(
            com.example.vio.core.model.LocalizationMap::class.java,
            com.example.vio.core.model.LocalizationReference::class.java,
            LocalizationRequest::class.java,
        )) {
            val names = type.declaredFields.map { it.name.lowercase() }
            val hits = names.filter { banned.containsMatchIn(it) || forbidden.any { token -> it == token } }
            assertTrue("${type.simpleName} fields $names", hits.isEmpty())
        }
    }

    @Test
    fun zoneChainFollowsSourceDistance() {
        val zones = listOf(
            zone("b", 50.0),
            zone("a", 0.0),
            zone("c", 90.0),
        )
        val links = ZoneTopology.chainByDistance(zones)
        assertEquals(listOf("a" to "b", "b" to "c"), links.map { it.fromZoneId to it.toZoneId })
    }

    @Test
    fun validatorReportsMissingEvidence() {
        val manifest = MapManifest("map", "Route", MAP_SCHEMA_VERSION, 0L, "Source", null, null, "")
        val issues = MapValidator.validate(
            StoredMapSnapshot(
                manifest = manifest,
                zones = listOf(zone("a", 0.0)),
                relations = listOf(ZoneRelation("map", "a", "missing")),
                observations = emptyList(),
                descriptorObservationIds = emptySet(),
                imageFilesPresent = emptySet(),
            ),
        )
        assertTrue(issues.any { it.severity == IssueSeverity.ERROR && "outside" in it.message })
        assertTrue(issues.any { it.severity == IssueSeverity.WARNING })
    }

    @Test
    fun packageRoundTripAndUnsafeEntries() {
        val manifest = MapManifest("map-1", "Route", MAP_SCHEMA_VERSION, 5L, "Gate", 1.5, 2.5, "note")
        val zone = Zone("zone-1", "map-1", "A-01", "", 0, 0.0, 40.0, 15f)
        val observation = PackageObservation(
            observationId = "obs-1",
            zoneId = "zone-1",
            sessionId = "session",
            capturedAtEpochMs = 9L,
            latitude = 3.0,
            longitude = 4.0,
            horizontalAccuracyMeters = 6f,
            routeDistanceMeters = 12.0,
            viewBearingDegrees = null,
            qualityScore = 1f,
            conditionsTag = "field",
            imageFile = "images/obs-1.jpg",
            descriptorFile = "descriptors/obs-1.bin",
        )
        val encoded = MapPackage.encode(
            PackageManifest(MAP_SCHEMA_VERSION, DESCRIPTOR_TYPE, DESCRIPTOR_MODEL_VERSION, manifest, listOf(zone), emptyList(), listOf(observation)),
        )
        val bytes = ByteArrayOutputStream()
        MapPackage.zip(bytes, encoded, mapOf("images/obs-1.jpg" to byteArrayOf(1, 2), "descriptors/obs-1.bin" to byteArrayOf(3)))
        val unpacked = MapPackage.unzip(ByteArrayInputStream(bytes.toByteArray()))
        assertEquals("Route", unpacked.manifest.map.name)
        assertEquals(12.0, unpacked.manifest.observations.single().routeDistanceMeters, 0.001)
        assertTrue(unpacked.files.getValue("images/obs-1.jpg").contentEquals(byteArrayOf(1, 2)))

        val badSchema = encoded.replace("\"schemaVersion\":1", "\"schemaVersion\":9")
        assertThrows(MapFormatException::class.java) { MapPackage.decode(badSchema) }

        val hostile = ByteArrayOutputStream()
        ZipOutputStream(hostile).use { zip ->
            zip.putNextEntry(ZipEntry("../secret.txt"))
            zip.write(byteArrayOf(1))
            zip.closeEntry()
        }
        assertThrows(MapFormatException::class.java) { MapPackage.unzip(ByteArrayInputStream(hostile.toByteArray())) }
    }

    private fun request(features: FeatureSet, time: Long, magnetic: Float?) = LocalizationRequest(
        timestampEpochMs = time,
        features = features,
        quality = QualityReport(true, 128f, 400f, 40f, emptyList()),
        sessionYawDegrees = null,
        magneticHeadingDegrees = magnetic,
        gyroAvailable = false,
        accelAvailable = false,
    )

    private fun zone(id: String, distance: Double) = Zone(
        zoneId = id,
        mapId = "map",
        label = id,
        description = "",
        orderIndex = 0,
        routeDistanceMeters = distance,
        zoneLengthMeters = 30.0,
        routeBearingDegrees = null,
    )
}
