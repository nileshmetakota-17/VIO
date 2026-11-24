package com.example.vio.core.map

import com.example.vio.core.model.DESCRIPTOR_MODEL_VERSION
import com.example.vio.core.model.DESCRIPTOR_TYPE
import com.example.vio.core.model.IssueSeverity
import com.example.vio.core.model.MAP_SCHEMA_VERSION
import com.example.vio.core.model.MapFormatException
import com.example.vio.core.model.MapIssue
import com.example.vio.core.model.MapManifest
import com.example.vio.core.model.ReferenceObservation
import com.example.vio.core.model.Zone
import com.example.vio.core.model.ZoneRelation
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class PackageObservation(
    val observationId: String,
    val zoneId: String,
    val sessionId: String,
    val capturedAtEpochMs: Long,
    val latitude: Double?,
    val longitude: Double?,
    val horizontalAccuracyMeters: Float?,
    val routeDistanceMeters: Double,
    val viewBearingDegrees: Float?,
    val qualityScore: Float,
    val conditionsTag: String,
    val imageFile: String,
    val descriptorFile: String,
)

data class PackageManifest(
    val schemaVersion: Int,
    val descriptorType: String,
    val modelVersion: String,
    val map: MapManifest,
    val zones: List<Zone>,
    val relations: List<ZoneRelation>,
    val observations: List<PackageObservation>,
)

data class UnzippedPackage(
    val manifest: PackageManifest,
    val files: Map<String, ByteArray>,
)

data class StoredMapSnapshot(
    val manifest: MapManifest,
    val zones: List<Zone>,
    val relations: List<ZoneRelation>,
    val observations: List<ReferenceObservation>,
    val descriptorObservationIds: Set<String>,
    val imageFilesPresent: Set<String>,
)

object ZoneTopology {
    fun chainByDistance(zones: List<Zone>): List<ZoneRelation> {
        val sorted = zones.sortedWith(compareBy({ it.routeDistanceMeters }, { it.orderIndex }))
        return sorted.zipWithNext { from, to ->
            ZoneRelation(from.mapId, from.zoneId, to.zoneId)
        }
    }
}

object MapValidator {
    fun validate(snapshot: StoredMapSnapshot): List<MapIssue> {
        val issues = ArrayList<MapIssue>()
        if (snapshot.manifest.schemaVersion != MAP_SCHEMA_VERSION) {
            issues += MapIssue(
                IssueSeverity.ERROR,
                "Map schema ${snapshot.manifest.schemaVersion} is not supported. Expected $MAP_SCHEMA_VERSION.",
            )
        }
        if (snapshot.manifest.name.isBlank()) {
            issues += MapIssue(IssueSeverity.ERROR, "The map name is blank.")
        }
        if (snapshot.zones.isEmpty()) {
            issues += MapIssue(IssueSeverity.ERROR, "The map has no zones.")
        }
        val zoneIds = HashSet<String>()
        for (zone in snapshot.zones) {
            if (!zoneIds.add(zone.zoneId)) {
                issues += MapIssue(IssueSeverity.ERROR, "Duplicate zone id ${zone.zoneId}.")
            }
            if (zone.label.isBlank()) {
                issues += MapIssue(IssueSeverity.ERROR, "A zone is missing its label.")
            }
            if (!zone.routeDistanceMeters.isFinite() || zone.routeDistanceMeters < 0.0) {
                issues += MapIssue(IssueSeverity.ERROR, "Zone ${zone.label} has an invalid source distance.")
            }
            if (!zone.zoneLengthMeters.isFinite() || zone.zoneLengthMeters <= 0.0) {
                issues += MapIssue(IssueSeverity.ERROR, "Zone ${zone.label} needs a length greater than zero.")
            }
        }
        val labels = snapshot.zones.groupingBy { it.label }.eachCount()
        for ((label, count) in labels) {
            if (label.isNotBlank() && count > 1) {
                issues += MapIssue(IssueSeverity.WARNING, "More than one zone is named $label.")
            }
        }
        val knownZones = snapshot.zones.map { it.zoneId }.toSet()
        val observationsByZone = snapshot.observations.groupBy { it.zoneId }
        for (zone in snapshot.zones) {
            val count = observationsByZone[zone.zoneId].orEmpty().size
            if (count == 0) {
                issues += MapIssue(IssueSeverity.WARNING, "Zone ${zone.label} has no reference images.")
            } else if (count == 1) {
                issues += MapIssue(
                    IssueSeverity.WARNING,
                    "Zone ${zone.label} has one reference view. A second viewpoint is needed for a leave-one-out check.",
                )
            }
        }
        for (observation in snapshot.observations) {
            if (observation.zoneId !in knownZones) {
                issues += MapIssue(
                    IssueSeverity.ERROR,
                    "Observation ${observation.observationId} points at an unknown zone.",
                )
            }
            if (observation.imageRelativePath !in snapshot.imageFilesPresent) {
                issues += MapIssue(
                    IssueSeverity.ERROR,
                    "Missing image file for ${observation.observationId}.",
                )
            }
            if (observation.observationId !in snapshot.descriptorObservationIds) {
                issues += MapIssue(
                    IssueSeverity.ERROR,
                    "Missing descriptor for ${observation.observationId}.",
                )
            }
            if (!observation.routeDistanceMeters.isFinite() || observation.routeDistanceMeters < 0.0) {
                issues += MapIssue(
                    IssueSeverity.ERROR,
                    "Observation ${observation.observationId} has an invalid source distance.",
                )
            }
        }
        for (relation in snapshot.relations) {
            if (relation.fromZoneId !in knownZones || relation.toZoneId !in knownZones) {
                issues += MapIssue(IssueSeverity.ERROR, "A zone link points outside this map.")
            }
        }
        return issues
    }
}

object MapPackage {
    const val MANIFEST_NAME = "manifest.json"
    private const val MAX_ENTRY_BYTES = 8 * 1024 * 1024
    private const val MAX_PACKAGE_BYTES = 80 * 1024 * 1024

    fun encode(manifest: PackageManifest): String {
        val root = JSONObject()
        root.put("schemaVersion", manifest.schemaVersion)
        root.put("descriptorType", manifest.descriptorType)
        root.put("modelVersion", manifest.modelVersion)
        root.put("map", manifest.map.toJson())
        root.put("zones", JSONArray().apply { manifest.zones.forEach { put(it.toJson()) } })
        root.put("relations", JSONArray().apply { manifest.relations.forEach { put(it.toJson()) } })
        root.put("observations", JSONArray().apply { manifest.observations.forEach { put(it.toJson()) } })
        return root.toString()
    }

    fun decode(json: String): PackageManifest {
        try {
            val root = JSONObject(json)
            val schema = root.getInt("schemaVersion")
            if (schema != MAP_SCHEMA_VERSION) {
                throw MapFormatException("Map schema $schema is not supported. Expected $MAP_SCHEMA_VERSION.")
            }
            val descriptorType = root.reqString("descriptorType")
            if (descriptorType != DESCRIPTOR_TYPE) {
                throw MapFormatException("Descriptor type $descriptorType is not supported. Expected $DESCRIPTOR_TYPE.")
            }
            val modelVersion = root.reqString("modelVersion")
            if (modelVersion != DESCRIPTOR_MODEL_VERSION) {
                throw MapFormatException("Descriptor model $modelVersion is not supported.")
            }
            val map = root.getJSONObject("map").toManifest()
            if (map.schemaVersion != MAP_SCHEMA_VERSION) {
                throw MapFormatException("Map record schema does not match the package.")
            }
            val zones = root.getJSONArray("zones").mapObjects { it.toZone(map.mapId) }
            val relations = root.getJSONArray("relations").mapObjects { it.toRelation(map.mapId) }
            val observations = root.getJSONArray("observations").mapObjects { it.toPackageObservation() }
            return PackageManifest(
                schemaVersion = schema,
                descriptorType = descriptorType,
                modelVersion = modelVersion,
                map = map,
                zones = zones,
                relations = relations,
                observations = observations,
            )
        } catch (error: MapFormatException) {
            throw error
        } catch (error: Exception) {
            throw MapFormatException(error.message ?: "The map package manifest is invalid.")
        }
    }

    fun zip(output: OutputStream, manifestJson: String, files: Map<String, ByteArray>) {
        ZipOutputStream(output).use { zip ->
            putEntry(zip, MANIFEST_NAME, manifestJson.toByteArray(Charsets.UTF_8))
            for ((name, bytes) in files) {
                checkEntryName(name)
                putEntry(zip, name, bytes)
            }
        }
    }

    fun unzip(input: InputStream): UnzippedPackage {
        val files = LinkedHashMap<String, ByteArray>()
        var total = 0
        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val name = checkEntryName(entry.name)
                    val bytes = zip.readLimited(MAX_ENTRY_BYTES)
                    total += bytes.size
                    if (total > MAX_PACKAGE_BYTES) {
                        throw MapFormatException("The map package is larger than the supported limit.")
                    }
                    files[name] = bytes
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        val manifestBytes = files[MANIFEST_NAME]
            ?: throw MapFormatException("The package has no manifest.json.")
        val manifest = decode(manifestBytes.toString(Charsets.UTF_8))
        for (observation in manifest.observations) {
            if (observation.imageFile !in files) {
                throw MapFormatException("Missing ${observation.imageFile}.")
            }
            if (observation.descriptorFile !in files) {
                throw MapFormatException("Missing ${observation.descriptorFile}.")
            }
        }
        return UnzippedPackage(manifest, files)
    }

    private fun putEntry(zip: ZipOutputStream, name: String, bytes: ByteArray) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun checkEntryName(raw: String): String {
        val name = raw.replace('\\', '/')
        if (
            name != MANIFEST_NAME &&
            !name.matches(Regex("images/[A-Za-z0-9._-]+\\.jpg")) &&
            !name.matches(Regex("descriptors/[A-Za-z0-9._-]+\\.bin"))
        ) {
            throw MapFormatException("Refusing map entry $raw.")
        }
        return name
    }

    private fun ZipInputStream.readLimited(max: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            total += read
            if (total > max) throw MapFormatException("A file in the map package is too large.")
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }
}

private fun MapManifest.toJson(): JSONObject = JSONObject()
    .put("mapId", mapId)
    .put("name", name)
    .put("schemaVersion", schemaVersion)
    .put("createdAtEpochMs", createdAtEpochMs)
    .put("sourceLabel", sourceLabel)
    .putNullable("sourceLatitude", sourceLatitude)
    .putNullable("sourceLongitude", sourceLongitude)
    .put("notes", notes)

private fun Zone.toJson(): JSONObject = JSONObject()
    .put("zoneId", zoneId)
    .put("label", label)
    .put("description", description)
    .put("orderIndex", orderIndex)
    .put("routeDistanceMeters", routeDistanceMeters)
    .put("zoneLengthMeters", zoneLengthMeters)
    .putNullable("routeBearingDegrees", routeBearingDegrees?.toDouble())

private fun ZoneRelation.toJson(): JSONObject = JSONObject()
    .put("fromZoneId", fromZoneId)
    .put("toZoneId", toZoneId)

private fun PackageObservation.toJson(): JSONObject = JSONObject()
    .put("observationId", observationId)
    .put("zoneId", zoneId)
    .put("sessionId", sessionId)
    .put("capturedAtEpochMs", capturedAtEpochMs)
    .putNullable("latitude", latitude)
    .putNullable("longitude", longitude)
    .putNullable("horizontalAccuracyMeters", horizontalAccuracyMeters?.toDouble())
    .put("routeDistanceMeters", routeDistanceMeters)
    .putNullable("viewBearingDegrees", viewBearingDegrees?.toDouble())
    .put("qualityScore", qualityScore.toDouble())
    .put("conditionsTag", conditionsTag)
    .put("imageFile", imageFile)
    .put("descriptorFile", descriptorFile)

private fun JSONObject.toManifest(): MapManifest = MapManifest(
    mapId = reqString("mapId"),
    name = reqString("name"),
    schemaVersion = getInt("schemaVersion"),
    createdAtEpochMs = getLong("createdAtEpochMs"),
    sourceLabel = reqString("sourceLabel"),
    sourceLatitude = nullableDouble("sourceLatitude"),
    sourceLongitude = nullableDouble("sourceLongitude"),
    notes = optString("notes", ""),
)

private fun JSONObject.toZone(mapId: String): Zone = Zone(
    zoneId = reqString("zoneId"),
    mapId = mapId,
    label = reqString("label"),
    description = optString("description", ""),
    orderIndex = getInt("orderIndex"),
    routeDistanceMeters = getDouble("routeDistanceMeters"),
    zoneLengthMeters = getDouble("zoneLengthMeters"),
    routeBearingDegrees = nullableDouble("routeBearingDegrees")?.toFloat(),
)

private fun JSONObject.toRelation(mapId: String): ZoneRelation = ZoneRelation(
    mapId = mapId,
    fromZoneId = reqString("fromZoneId"),
    toZoneId = reqString("toZoneId"),
)

private fun JSONObject.toPackageObservation(): PackageObservation = PackageObservation(
    observationId = reqString("observationId"),
    zoneId = reqString("zoneId"),
    sessionId = reqString("sessionId"),
    capturedAtEpochMs = getLong("capturedAtEpochMs"),
    latitude = nullableDouble("latitude"),
    longitude = nullableDouble("longitude"),
    horizontalAccuracyMeters = nullableDouble("horizontalAccuracyMeters")?.toFloat(),
    routeDistanceMeters = getDouble("routeDistanceMeters"),
    viewBearingDegrees = nullableDouble("viewBearingDegrees")?.toFloat(),
    qualityScore = getDouble("qualityScore").toFloat(),
    conditionsTag = optString("conditionsTag", ""),
    imageFile = reqString("imageFile"),
    descriptorFile = reqString("descriptorFile"),
)

private fun JSONObject.reqString(key: String): String {
    if (!has(key) || isNull(key)) throw MapFormatException("Missing $key.")
    return getString(key)
}

private fun JSONObject.nullableDouble(key: String): Double? {
    if (!has(key) || isNull(key)) return null
    return getDouble(key)
}

private fun JSONObject.putNullable(key: String, value: Double?): JSONObject {
    if (value == null) put(key, JSONObject.NULL) else put(key, value)
    return this
}

private inline fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> {
    val out = ArrayList<T>(length())
    for (index in 0 until length()) out += transform(getJSONObject(index))
    return out
}
