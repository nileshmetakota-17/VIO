package com.example.vio.core.map

import com.example.vio.core.model.FeatureSet
import com.example.vio.core.model.LocalizationMap
import com.example.vio.core.model.LocalizationReference
import com.example.vio.core.model.MAP_SCHEMA_VERSION
import com.example.vio.core.model.MapManifest
import com.example.vio.core.model.Zone
import com.example.vio.core.model.ZoneRelation
import com.example.vio.core.vision.FeatureExtractor
import com.example.vio.core.vision.ImageBuffer
import com.example.vio.core.vision.SyntheticScenes

data class DemoShot(
    val observationId: String,
    val zoneId: String,
    val sessionId: String,
    val routeDistanceMeters: Double,
    val latitude: Double,
    val longitude: Double,
    val image: ImageBuffer,
    val features: FeatureSet,
)

data class DemoBundle(
    val manifest: MapManifest,
    val zones: List<Zone>,
    val relations: List<ZoneRelation>,
    val shots: List<DemoShot>,
)

object DemoMaps {
    fun build(mapId: String, nowEpochMs: Long): DemoBundle {
        val sessionId = "$mapId-session"
        val straight = "$mapId-a"
        val bend = "$mapId-b"
        val junction = "$mapId-c"
        val zones = listOf(
            Zone(straight, mapId, "A-01 Straight", "Synthetic vertical structure.", 0, 0.0, 80.0, 12f),
            Zone(bend, mapId, "B-07 Northern bend", "Synthetic checker structure.", 1, 180.0, 100.0, 65f),
            Zone(junction, mapId, "C-02 Junction", "Synthetic ring structure.", 2, 420.0, 120.0, 110f),
        )
        val shots = listOf(
            shot(mapId, "a1", straight, sessionId, 20.0, 12.9700, 77.5900, SyntheticScenes.verticalBars(160, 120, 16)),
            shot(
                mapId,
                "a2",
                straight,
                sessionId,
                45.0,
                12.9704,
                77.5904,
                SyntheticScenes.verticalBars(160, 120, 16).translate(6, 2).brighten(16),
            ),
            shot(mapId, "b1", bend, sessionId, 200.0, 12.9720, 77.5940, SyntheticScenes.checker(160, 120, 14)),
            shot(
                mapId,
                "b2",
                bend,
                sessionId,
                235.0,
                12.9724,
                77.5945,
                SyntheticScenes.checker(160, 120, 14).translate(5, 4).brighten(12),
            ),
            shot(mapId, "c1", junction, sessionId, 450.0, 12.9750, 77.5980, SyntheticScenes.rings(160, 120, 9)),
            shot(
                mapId,
                "c2",
                junction,
                sessionId,
                490.0,
                12.9754,
                77.5986,
                SyntheticScenes.rings(160, 120, 9).translate(4, 5).brighten(10),
            ),
        )
        return DemoBundle(
            manifest = MapManifest(
                mapId = mapId,
                name = "Synthetic demo",
                schemaVersion = MAP_SCHEMA_VERSION,
                createdAtEpochMs = nowEpochMs,
                sourceLabel = "Demo source",
                sourceLatitude = 12.9716,
                sourceLongitude = 77.5946,
                notes = "Synthetic patterns for exercising the pipeline. This is not a surveyed place.",
            ),
            zones = zones,
            relations = ZoneTopology.chainByDistance(zones),
            shots = shots,
        )
    }

    fun localizationMap(bundle: DemoBundle): LocalizationMap = LocalizationMap(
        mapId = bundle.manifest.mapId,
        name = bundle.manifest.name,
        schemaVersion = bundle.manifest.schemaVersion,
        sourceLabel = bundle.manifest.sourceLabel,
        zones = bundle.zones,
        relations = bundle.relations,
        observations = bundle.shots.map { shot ->
            LocalizationReference(
                observationId = shot.observationId,
                zoneId = shot.zoneId,
                routeDistanceMeters = shot.routeDistanceMeters,
                viewBearingDegrees = null,
                features = shot.features,
            )
        },
    )

    private fun shot(
        mapId: String,
        suffix: String,
        zoneId: String,
        sessionId: String,
        distance: Double,
        latitude: Double,
        longitude: Double,
        image: ImageBuffer,
    ): DemoShot = DemoShot(
        observationId = "$mapId-$suffix",
        zoneId = zoneId,
        sessionId = sessionId,
        routeDistanceMeters = distance,
        latitude = latitude,
        longitude = longitude,
        image = image,
        features = FeatureExtractor.extract(image),
    )
}
