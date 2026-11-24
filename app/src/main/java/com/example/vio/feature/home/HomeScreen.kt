package com.example.vio.feature.home

import android.app.Application
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.vio.R
import com.example.vio.core.model.MapSummary
import com.example.vio.core.storage.VioGraph
import com.example.vio.ui.Notice
import com.example.vio.ui.SectionTitle
import com.example.vio.ui.SegmentedActions
import com.example.vio.ui.StatChip
import com.example.vio.ui.theme.Danger
import com.example.vio.ui.theme.Mute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

data class HomeUi(
    val maps: List<MapSummary> = emptyList(),
    val busy: Boolean = false,
    val error: String? = null,
)

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = VioGraph.repository(app)
    private val _ui = MutableStateFlow(HomeUi())
    val ui: StateFlow<HomeUi> = _ui

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, error = null) }
            try {
                val maps = repository.listMaps()
                _ui.update { it.copy(maps = maps, busy = false) }
            } catch (error: Exception) {
                _ui.update { it.copy(busy = false, error = error.message ?: "Could not read maps.") }
            }
        }
    }

    fun createMap(name: String, sourceLabel: String, latitude: String, longitude: String, notes: String) {
        val lat = parseCoordinate(latitude)
        val lon = parseCoordinate(longitude)
        if (latitude.isNotBlank() && lat == null || longitude.isNotBlank() && lon == null) {
            _ui.update { it.copy(error = "Latitude and longitude must be numbers, or left blank.") }
            return
        }
        if ((lat == null) != (lon == null)) {
            _ui.update { it.copy(error = "Enter both latitude and longitude, or leave both blank.") }
            return
        }
        if (lat != null && (lat !in -90.0..90.0 || lon!! !in -180.0..180.0)) {
            _ui.update { it.copy(error = "Those coordinates are out of range.") }
            return
        }
        viewModelScope.launch {
            try {
                repository.createMap(name, sourceLabel, lat, lon, notes)
                refresh()
            } catch (error: Exception) {
                _ui.update { it.copy(error = error.message ?: "Could not create the map.") }
            }
        }
    }

    fun installDemo() {
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, error = null) }
            try {
                repository.installDemoMap()
                refresh()
            } catch (error: Exception) {
                _ui.update { it.copy(busy = false, error = error.message ?: "Could not install the demo map.") }
            }
        }
    }

    fun import(uri: Uri) {
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, error = null) }
            try {
                val stream = getApplication<Application>().contentResolver.openInputStream(uri)
                    ?: error("Could not open that file.")
                stream.use { repository.import(it) }
                refresh()
            } catch (error: Exception) {
                _ui.update { it.copy(busy = false, error = error.message ?: "Import failed.") }
            }
        }
    }

    fun deleteAll() {
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, error = null) }
            try {
                repository.deleteAllMaps()
                refresh()
            } catch (error: Exception) {
                _ui.update { it.copy(busy = false, error = error.message ?: "Could not delete the maps.") }
            }
        }
    }

    fun clearError() {
        _ui.update { it.copy(error = null) }
    }

    private fun parseCoordinate(value: String): Double? {
        if (value.isBlank()) return null
        return value.trim().toDoubleOrNull()
    }
}

@Composable
fun HomeScreen(
    onCapture: (String) -> Unit,
    onLocalize: (String) -> Unit,
    onDiagnostics: (String) -> Unit,
    onSettings: () -> Unit = {},
    model: HomeViewModel = viewModel(),
) {
    val ui by model.ui.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    var showCreate by remember { mutableStateOf(false) }
    var confirmDeleteAll by remember { mutableStateOf(false) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) model.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) model.import(uri)
    }
    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Column(Modifier.padding(top = 20.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionTitle("Offline visual localization")
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Image(
                            painter = painterResource(R.drawable.ic_vio_mark),
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                        )
                        Text(
                            "VIO",
                            style = MaterialTheme.typography.headlineLarge,
                            modifier = Modifier.padding(start = 10.dp).weight(1f),
                        )
                        IconButton(onClick = onSettings) {
                            Image(
                                painter = painterResource(R.drawable.ic_settings),
                                contentDescription = "Settings",
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }
                    Text(
                        "Maps stay on this phone. A GPS tag can be stored with a reference. Localization does not read it.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Mute,
                    )
                }
            }
            if (ui.error != null) {
                item {
                    Notice(ui.error ?: "", alert = true)
                    TextButton(onClick = model::clearError) { Text("Dismiss") }
                }
            }
            if (ui.maps.isEmpty()) {
                item {
                    Notice("No map yet. Create one on the route, or install the synthetic demo.")
                }
            }
            items(ui.maps, key = { it.mapId }) { map ->
                MapCard(map, onCapture, onLocalize, onDiagnostics)
            }
            item {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 4.dp, bottom = 28.dp),
                ) {
                    Button(
                        onClick = { showCreate = true },
                        enabled = !ui.busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("New map") }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = model::installDemo,
                            enabled = !ui.busy,
                            modifier = Modifier.weight(1f),
                        ) { Text("Demo map") }
                        OutlinedButton(
                            onClick = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
                            enabled = !ui.busy,
                            modifier = Modifier.weight(1f),
                        ) { Text("Import") }
                    }
                    if (ui.maps.isNotEmpty()) {
                        TextButton(
                            onClick = { confirmDeleteAll = true },
                            enabled = !ui.busy,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        ) { Text("Delete all maps", color = Danger) }
                    }
                }
            }
        }
    }
    if (showCreate) {
        CreateMapDialog(
            onDismiss = { showCreate = false },
            onCreate = { name, source, lat, lon, notes ->
                model.createMap(name, source, lat, lon, notes)
                showCreate = false
            },
        )
    }
    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text("Delete all maps?") },
            text = { Text("Every map, zone, reference photo, and GPS tag on this phone will be removed.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteAll = false
                    model.deleteAll()
                }) { Text("Delete all", color = Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteAll = false }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MapCard(
    map: MapSummary,
    onCapture: (String) -> Unit,
    onLocalize: (String) -> Unit,
    onDiagnostics: (String) -> Unit,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(map.name, style = MaterialTheme.typography.titleLarge)
                Text(map.sourceLabel, color = Mute, style = MaterialTheme.typography.bodyMedium)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                StatChip("${map.zoneCount} zones")
                StatChip("${map.observationCount} references")
            }
            Text(
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(map.createdAtEpochMs)),
                color = Mute,
                style = MaterialTheme.typography.bodyMedium,
            )
            SegmentedActions(
                listOf(
                    "Map" to { onCapture(map.mapId) },
                    "Localize" to { onLocalize(map.mapId) },
                    "Check" to { onDiagnostics(map.mapId) },
                ),
            )
        }
    }
}

@Composable
private fun CreateMapDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String, String, String, String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var source by remember { mutableStateOf("Source") }
    var latitude by remember { mutableStateOf("") }
    var longitude by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New map") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(source, { source = it }, label = { Text("Source label") }, singleLine = true)
                OutlinedTextField(latitude, { latitude = it }, label = { Text("Source latitude, optional") }, singleLine = true)
                OutlinedTextField(longitude, { longitude = it }, label = { Text("Source longitude, optional") }, singleLine = true)
                OutlinedTextField(notes, { notes = it }, label = { Text("Notes") })
                Text(
                    "Coordinates are mapping metadata. They are not used when localizing.",
                    color = Mute,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name, source, latitude, longitude, notes) }, enabled = name.isNotBlank()) {
                Text("Create")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
