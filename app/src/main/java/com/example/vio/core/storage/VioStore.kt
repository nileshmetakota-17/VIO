package com.example.vio.core.storage

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.vio.core.model.DESCRIPTOR_MODEL_VERSION
import com.example.vio.core.model.DESCRIPTOR_TYPE
import com.example.vio.core.model.FeatureSet
import com.example.vio.core.model.IssueSeverity
import com.example.vio.core.model.LocalizationMap
import com.example.vio.core.model.LocalizationReference
import com.example.vio.core.model.LocalizationResult
import com.example.vio.core.model.MAP_SCHEMA_VERSION
import com.example.vio.core.model.MapFormatException
import com.example.vio.core.model.MapIssue
import com.example.vio.core.model.MapManifest
import com.example.vio.core.model.MapSummary
import com.example.vio.core.model.ObservationSummary
import com.example.vio.core.model.ReferenceObservation
import com.example.vio.core.model.Zone
import com.example.vio.core.model.ZoneRelation
import com.example.vio.core.map.DemoMaps
import com.example.vio.core.map.MapPackage
import com.example.vio.core.map.MapValidator
import com.example.vio.core.map.PackageManifest
import com.example.vio.core.map.PackageObservation
import com.example.vio.core.map.StoredMapSnapshot
import com.example.vio.core.map.ZoneTopology
import com.example.vio.core.vision.FeatureCodec
import com.example.vio.core.vision.FeatureExtractor
import com.example.vio.core.vision.ImageBuffer
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

@Entity(tableName = "maps")
data class MapEntity(
    @PrimaryKey val mapId: String,
    val name: String,
    val schemaVersion: Int,
    val createdAtEpochMs: Long,
    val sourceLabel: String,
    val sourceLatitude: Double?,
    val sourceLongitude: Double?,
    val notes: String,
)

@Entity(
    tableName = "zones",
    foreignKeys = [
        ForeignKey(
            entity = MapEntity::class,
            parentColumns = ["mapId"],
            childColumns = ["mapId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("mapId")],
)
data class ZoneEntity(
    @PrimaryKey val zoneId: String,
    val mapId: String,
    val label: String,
    val description: String,
    val orderIndex: Int,
    val routeDistanceMeters: Double,
    val zoneLengthMeters: Double,
    val routeBearingDegrees: Float?,
)

@Entity(
    tableName = "observations",
    foreignKeys = [
        ForeignKey(
            entity = ZoneEntity::class,
            parentColumns = ["zoneId"],
            childColumns = ["zoneId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("mapId"), Index("zoneId")],
)
data class ObservationEntity(
    @PrimaryKey val observationId: String,
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

@Entity(
    tableName = "descriptors",
    foreignKeys = [
        ForeignKey(
            entity = ObservationEntity::class,
            parentColumns = ["observationId"],
            childColumns = ["observationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class DescriptorEntity(
    @PrimaryKey val observationId: String,
    val descriptorType: String,
    val modelVersion: String,
    val width: Int,
    val height: Int,
    val globalDescriptor: ByteArray,
    val keypointsBlob: ByteArray,
)

@Entity(
    tableName = "relations",
    foreignKeys = [
        ForeignKey(
            entity = MapEntity::class,
            parentColumns = ["mapId"],
            childColumns = ["mapId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("mapId")],
)
data class RelationEntity(
    @PrimaryKey(autoGenerate = true) val relationId: Long = 0,
    val mapId: String,
    val fromZoneId: String,
    val toZoneId: String,
)

@Entity(
    tableName = "localization_logs",
    indices = [Index("mapId")],
)
data class LogEntity(
    @PrimaryKey(autoGenerate = true) val logId: Long = 0,
    val mapId: String,
    val timestampEpochMs: Long,
    val state: String,
    val zoneId: String?,
    val zoneLabel: String?,
    val confidence: Float,
    val summary: String,
)

data class LogLine(
    val timestampEpochMs: Long,
    val state: String,
    val summary: String,
)

@Dao
interface VioDao {
    @Query("SELECT * FROM maps ORDER BY createdAtEpochMs DESC")
    suspend fun listMaps(): List<MapEntity>

    @Query("SELECT * FROM maps WHERE mapId = :mapId")
    suspend fun getMap(mapId: String): MapEntity?

    @Query("UPDATE maps SET sourceLatitude = :latitude, sourceLongitude = :longitude WHERE mapId = :mapId")
    suspend fun updateSource(mapId: String, latitude: Double, longitude: Double)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMap(map: MapEntity)

    @Query("DELETE FROM maps WHERE mapId = :mapId")
    suspend fun deleteMap(mapId: String)

    @Query("SELECT * FROM zones WHERE mapId = :mapId ORDER BY routeDistanceMeters, orderIndex")
    suspend fun zones(mapId: String): List<ZoneEntity>

    @Query("SELECT * FROM zones")
    suspend fun allZones(): List<ZoneEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertZone(zone: ZoneEntity)

    @Query("UPDATE zones SET label = :label WHERE zoneId = :zoneId")
    suspend fun updateZoneLabel(zoneId: String, label: String)

    @Query("DELETE FROM zones WHERE mapId = :mapId")
    suspend fun deleteZones(mapId: String)

    @Query("SELECT * FROM observations WHERE mapId = :mapId ORDER BY capturedAtEpochMs DESC")
    suspend fun observations(mapId: String): List<ObservationEntity>

    @Query("SELECT * FROM observations")
    suspend fun allObservations(): List<ObservationEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertObservation(observation: ObservationEntity)

    @Query("DELETE FROM observations WHERE observationId = :observationId")
    suspend fun deleteObservation(observationId: String)

    @Query("DELETE FROM observations WHERE mapId = :mapId")
    suspend fun deleteObservations(mapId: String)

    @Query("SELECT * FROM descriptors WHERE observationId = :observationId")
    suspend fun descriptor(observationId: String): DescriptorEntity?

    @Query("SELECT * FROM descriptors")
    suspend fun allDescriptors(): List<DescriptorEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDescriptor(descriptor: DescriptorEntity)

    @Query("DELETE FROM descriptors WHERE observationId = :observationId")
    suspend fun deleteDescriptor(observationId: String)

    @Query(
        "DELETE FROM descriptors WHERE observationId IN (SELECT observationId FROM observations WHERE mapId = :mapId)",
    )
    suspend fun deleteDescriptorsForMap(mapId: String)

    @Query("SELECT * FROM relations WHERE mapId = :mapId")
    suspend fun relations(mapId: String): List<RelationEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRelation(relation: RelationEntity)

    @Query("DELETE FROM relations WHERE mapId = :mapId")
    suspend fun deleteRelations(mapId: String)

    @Insert
    suspend fun insertLog(log: LogEntity)

    @Query("SELECT * FROM localization_logs WHERE mapId = :mapId ORDER BY timestampEpochMs DESC LIMIT :limit")
    suspend fun recentLogs(mapId: String, limit: Int): List<LogEntity>

    @Query("DELETE FROM localization_logs WHERE mapId = :mapId")
    suspend fun deleteLogs(mapId: String)

    @Transaction
    suspend fun insertCapture(observation: ObservationEntity, descriptor: DescriptorEntity) {
        insertObservation(observation)
        insertDescriptor(descriptor)
    }
}

@Database(
    entities = [
        MapEntity::class,
        ZoneEntity::class,
        ObservationEntity::class,
        DescriptorEntity::class,
        RelationEntity::class,
        LogEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class VioDatabase : RoomDatabase() {
    abstract fun dao(): VioDao

    companion object {
        fun create(context: Context): VioDatabase = Room.databaseBuilder(
            context.applicationContext,
            VioDatabase::class.java,
            "vio-maps.db",
        ).addCallback(object : RoomDatabase.Callback() {
            override fun onOpen(db: SupportSQLiteDatabase) {
                db.setForeignKeyConstraintsEnabled(true)
            }
        }).build()
    }
}

object VioGraph {
    @Volatile private var repository: MapRepository? = null

    fun repository(context: Context): MapRepository {
        repository?.let { return it }
        return synchronized(this) {
            repository ?: MapRepository(context.applicationContext).also { repository = it }
        }
    }
}

class MapRepository(context: Context) {
    private val files = context.applicationContext.filesDir
    private val database = VioDatabase.create(context)
    private val dao = database.dao()

    suspend fun listMaps(): List<MapSummary> = io {
        val maps = dao.listMaps()
        val zones = dao.allZones()
        val observations = dao.allObservations()
        maps.map { map ->
            MapSummary(
                mapId = map.mapId,
                name = map.name,
                sourceLabel = map.sourceLabel,
                zoneCount = zones.count { it.mapId == map.mapId },
                observationCount = observations.count { it.mapId == map.mapId },
                createdAtEpochMs = map.createdAtEpochMs,
            )
        }
    }

    suspend fun createMap(
        name: String,
        sourceLabel: String,
        sourceLatitude: Double?,
        sourceLongitude: Double?,
        notes: String,
    ): MapManifest = io {
        val manifest = MapManifest(
            mapId = UUID.randomUUID().toString(),
            name = name.trim(),
            schemaVersion = MAP_SCHEMA_VERSION,
            createdAtEpochMs = System.currentTimeMillis(),
            sourceLabel = sourceLabel.trim().ifBlank { "Source" },
            sourceLatitude = sourceLatitude,
            sourceLongitude = sourceLongitude,
            notes = notes.trim(),
        )
        requireSafeId(manifest.mapId, "Map id")
        if (manifest.name.isBlank()) throw MapFormatException("Give the map a name.")
        dao.insertMap(manifest.toEntity())
        manifest
    }

    suspend fun addZone(
        mapId: String,
        label: String,
        description: String,
        routeDistanceMeters: Double,
        zoneLengthMeters: Double,
        routeBearingDegrees: Float?,
    ): Zone = io {
        val existing = dao.zones(mapId)
        val zone = Zone(
            zoneId = UUID.randomUUID().toString(),
            mapId = mapId,
            label = label.trim(),
            description = description.trim(),
            orderIndex = (existing.maxOfOrNull { it.orderIndex } ?: -1) + 1,
            routeDistanceMeters = routeDistanceMeters,
            zoneLengthMeters = zoneLengthMeters,
            routeBearingDegrees = routeBearingDegrees,
        )
        if (zone.label.isBlank()) throw MapFormatException("Give the zone a label.")
        if (!zone.routeDistanceMeters.isFinite() || zone.routeDistanceMeters < 0.0) {
            throw MapFormatException("Source distance must be zero or greater.")
        }
        if (!zone.zoneLengthMeters.isFinite() || zone.zoneLengthMeters <= 0.0) {
            throw MapFormatException("Zone length must be greater than zero.")
        }
        dao.insertZone(zone.toEntity())
        rebuildRelations(mapId)
        zone
    }

    suspend fun renameZone(zoneId: String, label: String) = io {
        val cleaned = label.trim()
        if (cleaned.isBlank()) throw MapFormatException("Give the zone a name.")
        dao.updateZoneLabel(zoneId, cleaned)
    }

    suspend fun zones(mapId: String): List<Zone> = io { dao.zones(mapId).map { it.toModel() } }

    suspend fun observations(mapId: String): List<ObservationSummary> = io {
        val labels = dao.zones(mapId).associate { it.zoneId to it.label }
        dao.observations(mapId).map { observation ->
            ObservationSummary(
                observationId = observation.observationId,
                zoneId = observation.zoneId,
                zoneLabel = labels[observation.zoneId] ?: observation.zoneId,
                routeDistanceMeters = observation.routeDistanceMeters,
                capturedAtEpochMs = observation.capturedAtEpochMs,
                latitude = observation.latitude,
                longitude = observation.longitude,
                hasMappingGps = observation.latitude != null && observation.longitude != null,
                imageRelativePath = observation.imageRelativePath,
            )
        }
    }

    suspend fun addObservation(
        mapId: String,
        zoneId: String,
        sessionId: String,
        image: ImageBuffer,
        features: FeatureSet,
        routeDistanceMeters: Double,
        viewBearingDegrees: Float?,
        latitude: Double?,
        longitude: Double?,
        horizontalAccuracyMeters: Float?,
        qualityScore: Float,
        conditionsTag: String,
    ): ReferenceObservation = io {
        if (!routeDistanceMeters.isFinite() || routeDistanceMeters < 0.0) {
            throw MapFormatException("Source distance must be zero or greater.")
        }
        val observationId = UUID.randomUUID().toString()
        val relative = "maps/$mapId/images/$observationId.jpg"
        val file = resolve(relative)
        file.parentFile?.mkdirs()
        file.writeBytes(image.toJpegBytes())
        val observation = ReferenceObservation(
            observationId = observationId,
            mapId = mapId,
            zoneId = zoneId,
            sessionId = sessionId,
            imageRelativePath = relative,
            capturedAtEpochMs = System.currentTimeMillis(),
            latitude = latitude,
            longitude = longitude,
            horizontalAccuracyMeters = horizontalAccuracyMeters,
            routeDistanceMeters = routeDistanceMeters,
            viewBearingDegrees = viewBearingDegrees,
            qualityScore = qualityScore,
            conditionsTag = conditionsTag,
        )
        val encoded = FeatureCodec.encode(features)
        dao.insertCapture(
            observation.toEntity(),
            DescriptorEntity(
                observationId = observationId,
                descriptorType = DESCRIPTOR_TYPE,
                modelVersion = DESCRIPTOR_MODEL_VERSION,
                width = features.width,
                height = features.height,
                globalDescriptor = features.globalDescriptor,
                keypointsBlob = encoded,
            ),
        )
        observation
    }

    suspend fun deleteObservation(observationId: String) = io {
        val observation = dao.allObservations().firstOrNull { it.observationId == observationId } ?: return@io
        dao.deleteDescriptor(observationId)
        dao.deleteObservation(observationId)
        resolve(observation.imageRelativePath).delete()
    }

    suspend fun deleteAllObservations(mapId: String) = io {
        val stored = dao.observations(mapId)
        database.withTransaction {
            dao.deleteDescriptorsForMap(mapId)
            dao.deleteObservations(mapId)
        }
        stored.forEach { observation -> resolve(observation.imageRelativePath).delete() }
    }

    suspend fun deleteMap(mapId: String) = io {
        requireSafeId(mapId, "Map id")
        removeMap(mapId)
    }

    suspend fun deleteAllMaps() = io {
        dao.listMaps().map { it.mapId }.forEach { mapId -> removeMap(mapId) }
    }

    private suspend fun removeMap(mapId: String) {
        database.withTransaction {
            dao.deleteLogs(mapId)
            dao.deleteRelations(mapId)
            dao.deleteDescriptorsForMap(mapId)
            dao.deleteObservations(mapId)
            dao.deleteZones(mapId)
            dao.deleteMap(mapId)
        }
        File(files, "maps/$mapId").deleteRecursively()
    }

    suspend fun manifest(mapId: String): MapManifest? = io { dao.getMap(mapId)?.toModel() }

    suspend fun setSource(mapId: String, latitude: Double, longitude: Double) = io {
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) {
            throw MapFormatException("Those coordinates are out of range.")
        }
        if (dao.getMap(mapId) == null) throw MapFormatException("That map is no longer on this device.")
        dao.updateSource(mapId, latitude, longitude)
    }

    suspend fun setSourceIfMissing(mapId: String, latitude: Double, longitude: Double) = io {
        val map = dao.getMap(mapId) ?: return@io
        if (map.sourceLatitude != null && map.sourceLongitude != null) return@io
        dao.updateSource(mapId, latitude, longitude)
    }

    fun imageFile(relativePath: String): File = resolve(relativePath)

    suspend fun validate(mapId: String): List<MapIssue> = io { MapValidator.validate(snapshot(mapId)) }

    suspend fun loadLocalizationMap(mapId: String): LocalizationMap = io {
        val map = dao.getMap(mapId) ?: throw MapFormatException("That map is no longer on this device.")
        if (map.schemaVersion != MAP_SCHEMA_VERSION) {
            throw MapFormatException("Map schema ${map.schemaVersion} is not supported.")
        }
        val zones = dao.zones(mapId).map { it.toModel() }
        val relations = dao.relations(mapId).map { ZoneRelation(it.mapId, it.fromZoneId, it.toZoneId) }
        val observations = dao.observations(mapId).map { observation ->
            val descriptor = dao.descriptor(observation.observationId)
                ?: throw MapFormatException("Missing descriptor for ${observation.observationId}.")
            if (descriptor.descriptorType != DESCRIPTOR_TYPE || descriptor.modelVersion != DESCRIPTOR_MODEL_VERSION) {
                throw MapFormatException("Descriptor for ${observation.observationId} is an unsupported version.")
            }
            val features = featuresForCheck(observation.imageRelativePath, descriptor.keypointsBlob)
            LocalizationReference(
                observationId = observation.observationId,
                zoneId = observation.zoneId,
                routeDistanceMeters = observation.routeDistanceMeters,
                viewBearingDegrees = observation.viewBearingDegrees,
                features = features,
            )
        }
        LocalizationMap(
            mapId = map.mapId,
            name = map.name,
            schemaVersion = map.schemaVersion,
            sourceLabel = map.sourceLabel,
            zones = zones,
            relations = relations,
            observations = observations,
        )
    }

    suspend fun installDemoMap(): MapManifest = io {
        val bundle = DemoMaps.build(UUID.randomUUID().toString(), System.currentTimeMillis())
        database.withTransaction {
            dao.insertMap(bundle.manifest.toEntity())
            bundle.zones.forEach { dao.insertZone(it.toEntity()) }
            bundle.relations.forEach {
                dao.insertRelation(RelationEntity(mapId = it.mapId, fromZoneId = it.fromZoneId, toZoneId = it.toZoneId))
            }
            for (shot in bundle.shots) {
                val relative = "maps/${bundle.manifest.mapId}/images/${shot.observationId}.jpg"
                val file = resolve(relative)
                file.parentFile?.mkdirs()
                file.writeBytes(shot.image.toJpegBytes())
                val observation = ReferenceObservation(
                    observationId = shot.observationId,
                    mapId = bundle.manifest.mapId,
                    zoneId = shot.zoneId,
                    sessionId = shot.sessionId,
                    imageRelativePath = relative,
                    capturedAtEpochMs = bundle.manifest.createdAtEpochMs,
                    latitude = shot.latitude,
                    longitude = shot.longitude,
                    horizontalAccuracyMeters = 8f,
                    routeDistanceMeters = shot.routeDistanceMeters,
                    viewBearingDegrees = null,
                    qualityScore = 1f,
                    conditionsTag = "synthetic-demo",
                )
                dao.insertCapture(
                    observation.toEntity(),
                    DescriptorEntity(
                        observationId = shot.observationId,
                        descriptorType = DESCRIPTOR_TYPE,
                        modelVersion = DESCRIPTOR_MODEL_VERSION,
                        width = shot.features.width,
                        height = shot.features.height,
                        globalDescriptor = shot.features.globalDescriptor,
                        keypointsBlob = FeatureCodec.encode(shot.features),
                    ),
                )
            }
        }
        bundle.manifest
    }

    suspend fun export(mapId: String, output: OutputStream) = io {
        val map = dao.getMap(mapId) ?: throw MapFormatException("That map is no longer on this device.")
        val zones = dao.zones(mapId).map { it.toModel() }
        val relations = if (dao.relations(mapId).isEmpty()) {
            ZoneTopology.chainByDistance(zones)
        } else {
            dao.relations(mapId).map { ZoneRelation(it.mapId, it.fromZoneId, it.toZoneId) }
        }
        val observations = dao.observations(mapId)
        val packaged = observations.map { observation ->
            PackageObservation(
                observationId = observation.observationId,
                zoneId = observation.zoneId,
                sessionId = observation.sessionId,
                capturedAtEpochMs = observation.capturedAtEpochMs,
                latitude = observation.latitude,
                longitude = observation.longitude,
                horizontalAccuracyMeters = observation.horizontalAccuracyMeters,
                routeDistanceMeters = observation.routeDistanceMeters,
                viewBearingDegrees = observation.viewBearingDegrees,
                qualityScore = observation.qualityScore,
                conditionsTag = observation.conditionsTag,
                imageFile = "images/${observation.observationId}.jpg",
                descriptorFile = "descriptors/${observation.observationId}.bin",
            )
        }
        val manifest = PackageManifest(
            schemaVersion = MAP_SCHEMA_VERSION,
            descriptorType = DESCRIPTOR_TYPE,
            modelVersion = DESCRIPTOR_MODEL_VERSION,
            map = map.toModel(),
            zones = zones,
            relations = relations,
            observations = packaged,
        )
        val files = LinkedHashMap<String, ByteArray>()
        for (observation in observations) {
            val descriptor = dao.descriptor(observation.observationId)
                ?: throw MapFormatException("Missing descriptor for ${observation.observationId}.")
            files["images/${observation.observationId}.jpg"] = resolve(observation.imageRelativePath).readBytes()
            files["descriptors/${observation.observationId}.bin"] = descriptor.keypointsBlob
        }
        MapPackage.zip(output, MapPackage.encode(manifest), files)
    }

    suspend fun import(input: InputStream): MapManifest = io {
        val unpacked = MapPackage.unzip(input)
        val manifest = unpacked.manifest
        requireSafeId(manifest.map.mapId, "Map id")
        if (dao.getMap(manifest.map.mapId) != null) {
            throw MapFormatException("A map with this id is already stored. Delete it before importing again.")
        }
        for (zone in manifest.zones) requireSafeId(zone.zoneId, "Zone id")
        for (observation in manifest.observations) requireSafeId(observation.observationId, "Observation id")
        val relations = manifest.relations.ifEmpty { ZoneTopology.chainByDistance(manifest.zones) }
        val snapshot = StoredMapSnapshot(
            manifest = manifest.map,
            zones = manifest.zones,
            relations = relations,
            observations = manifest.observations.map { it.toReference(manifest.map.mapId) },
            descriptorObservationIds = manifest.observations.map { it.observationId }.toSet(),
            imageFilesPresent = manifest.observations.map { it.toReference(manifest.map.mapId).imageRelativePath }.toSet(),
        )
        val errors = MapValidator.validate(snapshot).filter { it.severity == IssueSeverity.ERROR }
        if (errors.isNotEmpty()) {
            throw MapFormatException(errors.joinToString(" ") { it.message })
        }
        database.withTransaction {
            dao.insertMap(manifest.map.toEntity())
            manifest.zones.forEach { dao.insertZone(it.toEntity()) }
            relations.forEach {
                dao.insertRelation(RelationEntity(mapId = manifest.map.mapId, fromZoneId = it.fromZoneId, toZoneId = it.toZoneId))
            }
            for (observation in manifest.observations) {
                val imageBytes = unpacked.files.getValue(observation.imageFile)
                val descriptorBytes = unpacked.files.getValue(observation.descriptorFile)
                val features = FeatureCodec.decode(descriptorBytes)
                val relative = "maps/${manifest.map.mapId}/images/${observation.observationId}.jpg"
                val file = resolve(relative)
                file.parentFile?.mkdirs()
                file.writeBytes(imageBytes)
                dao.insertCapture(
                    observation.toReference(manifest.map.mapId).toEntity(),
                    DescriptorEntity(
                        observationId = observation.observationId,
                        descriptorType = DESCRIPTOR_TYPE,
                        modelVersion = DESCRIPTOR_MODEL_VERSION,
                        width = features.width,
                        height = features.height,
                        globalDescriptor = features.globalDescriptor,
                        keypointsBlob = descriptorBytes,
                    ),
                )
            }
        }
        manifest.map
    }

    suspend fun appendLog(mapId: String, result: LocalizationResult) = io {
        val summary = buildString {
            append(result.state.name)
            append(' ')
            append(result.zoneLabel ?: "no zone")
            append(" · score ")
            append(String.format(java.util.Locale.US, "%.2f", result.confidence))
        }.take(300)
        dao.insertLog(
            LogEntity(
                mapId = mapId,
                timestampEpochMs = result.timestampEpochMs,
                state = result.state.name,
                zoneId = result.zoneId,
                zoneLabel = result.zoneLabel,
                confidence = result.confidence,
                summary = summary,
            ),
        )
    }

    suspend fun recentLogs(mapId: String): List<LogLine> = io {
        dao.recentLogs(mapId, 8).map { LogLine(it.timestampEpochMs, it.state, it.summary) }
    }

    suspend fun mappingGpsCount(mapId: String): Int = io {
        dao.observations(mapId).count { it.latitude != null && it.longitude != null }
    }

    private suspend fun snapshot(mapId: String): StoredMapSnapshot {
        val map = dao.getMap(mapId) ?: throw MapFormatException("That map is no longer on this device.")
        val observations = dao.observations(mapId).map { it.toModel() }
        return StoredMapSnapshot(
            manifest = map.toModel(),
            zones = dao.zones(mapId).map { it.toModel() },
            relations = dao.relations(mapId).map { ZoneRelation(it.mapId, it.fromZoneId, it.toZoneId) },
            observations = observations,
            descriptorObservationIds = observations.map { it.observationId }
                .filter { dao.descriptor(it) != null }
                .toSet(),
            imageFilesPresent = observations.filter { resolve(it.imageRelativePath).isFile }.map { it.imageRelativePath }.toSet(),
        )
    }

    private suspend fun rebuildRelations(mapId: String) {
        val zones = dao.zones(mapId).map { it.toModel() }
        dao.deleteRelations(mapId)
        for (relation in ZoneTopology.chainByDistance(zones)) {
            dao.insertRelation(
                RelationEntity(mapId = relation.mapId, fromZoneId = relation.fromZoneId, toZoneId = relation.toZoneId),
            )
        }
    }

    private fun featuresForCheck(relativePath: String, storedBlob: ByteArray): FeatureSet {
        val decoded = decodeGrayJpeg(resolve(relativePath))
        if (decoded != null) return FeatureExtractor.extract(decoded)
        return FeatureCodec.decode(storedBlob)
    }

    private fun decodeGrayJpeg(file: File): ImageBuffer? {
        if (!file.isFile) return null
        val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return null
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) {
            bitmap.recycle()
            return null
        }
        val argb = IntArray(width * height)
        bitmap.getPixels(argb, 0, width, 0, 0, width, height)
        bitmap.recycle()
        val pixels = ByteArray(argb.size)
        for (index in argb.indices) {
            val color = argb[index]
            val red = (color shr 16) and 0xFF
            val green = (color shr 8) and 0xFF
            val blue = color and 0xFF
            pixels[index] = ((red * 30 + green * 59 + blue * 11) / 100).toByte()
        }
        return ImageBuffer(width, height, pixels)
    }

    private fun resolve(relative: String): File {
        val target = File(files, relative).canonicalFile
        val root = files.canonicalFile
        if (target != root && !target.path.startsWith(root.path + File.separator)) {
            throw MapFormatException("Refusing a path outside app storage.")
        }
        return target
    }

    private fun requireSafeId(id: String, label: String) {
        if (!id.matches(Regex("[A-Za-z0-9._-]{1,80}"))) {
            throw MapFormatException("$label contains unsupported characters.")
        }
    }

    private suspend fun <T> io(block: suspend () -> T): T = withContext(Dispatchers.IO) { block() }
}

private fun ImageBuffer.toJpegBytes(quality: Int = 85): ByteArray {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val argb = IntArray(pixels.size)
    for (index in pixels.indices) {
        val gray = pixels[index].toInt() and 0xFF
        argb[index] = (0xFF shl 24) or (gray shl 16) or (gray shl 8) or gray
    }
    bitmap.setPixels(argb, 0, width, 0, 0, width, height)
    val stream = ByteArrayOutputStream()
    check(bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)) { "Could not encode the reference image." }
    bitmap.recycle()
    return stream.toByteArray()
}

private fun MapManifest.toEntity() = MapEntity(
    mapId, name, schemaVersion, createdAtEpochMs, sourceLabel, sourceLatitude, sourceLongitude, notes,
)

private fun MapEntity.toModel() = MapManifest(
    mapId, name, schemaVersion, createdAtEpochMs, sourceLabel, sourceLatitude, sourceLongitude, notes,
)

private fun Zone.toEntity() = ZoneEntity(
    zoneId, mapId, label, description, orderIndex, routeDistanceMeters, zoneLengthMeters, routeBearingDegrees,
)

private fun ZoneEntity.toModel() = Zone(
    zoneId, mapId, label, description, orderIndex, routeDistanceMeters, zoneLengthMeters, routeBearingDegrees,
)

private fun ReferenceObservation.toEntity() = ObservationEntity(
    observationId,
    mapId,
    zoneId,
    sessionId,
    imageRelativePath,
    capturedAtEpochMs,
    latitude,
    longitude,
    horizontalAccuracyMeters,
    routeDistanceMeters,
    viewBearingDegrees,
    qualityScore,
    conditionsTag,
)

private fun ObservationEntity.toModel() = ReferenceObservation(
    observationId,
    mapId,
    zoneId,
    sessionId,
    imageRelativePath,
    capturedAtEpochMs,
    latitude,
    longitude,
    horizontalAccuracyMeters,
    routeDistanceMeters,
    viewBearingDegrees,
    qualityScore,
    conditionsTag,
)

private fun PackageObservation.toReference(mapId: String) = ReferenceObservation(
    observationId = observationId,
    mapId = mapId,
    zoneId = zoneId,
    sessionId = sessionId,
    imageRelativePath = "maps/$mapId/images/$observationId.jpg",
    capturedAtEpochMs = capturedAtEpochMs,
    latitude = latitude,
    longitude = longitude,
    horizontalAccuracyMeters = horizontalAccuracyMeters,
    routeDistanceMeters = routeDistanceMeters,
    viewBearingDegrees = viewBearingDegrees,
    qualityScore = qualityScore,
    conditionsTag = conditionsTag,
)
