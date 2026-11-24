package com.example.vio.feature.diagnostics

import android.app.Application
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.vio.core.localization.MapEvaluator
import com.example.vio.core.model.IssueSeverity
import com.example.vio.core.model.MapIssue
import com.example.vio.core.storage.LogLine
import com.example.vio.core.storage.VioGraph
import com.example.vio.ui.Notice
import com.example.vio.ui.Panel
import com.example.vio.ui.ScreenHeader
import com.example.vio.ui.SectionTitle
import com.example.vio.ui.StatChip
import com.example.vio.ui.theme.Amber
import com.example.vio.ui.theme.Danger
import com.example.vio.ui.theme.Moss
import com.example.vio.ui.theme.Mute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

data class DiagnosticsUi(
    val mapName: String = "",
    val sourceLine: String = "",
    val gpsTags: Int = 0,
    val issues: List<MapIssue> = emptyList(),
    val report: String? = null,
    val logs: List<LogLine> = emptyList(),
    val busy: Boolean = false,
    val error: String? = null,
)

class DiagnosticsViewModel(app: Application, private val mapId: String) : AndroidViewModel(app) {
    private val repository = VioGraph.repository(app)
    private val _ui = MutableStateFlow(DiagnosticsUi())
    val ui: StateFlow<DiagnosticsUi> = _ui

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, error = null) }
            try {
                val manifest = repository.manifest(mapId)
                val issues = repository.validate(mapId)
                val logs = repository.recentLogs(mapId)
                val gpsTags = repository.mappingGpsCount(mapId)
                val anchor = if (manifest?.sourceLatitude != null && manifest.sourceLongitude != null) {
                    "Source anchor ${manifest.sourceLabel} has mapping coordinates stored. Localization does not read them."
                } else {
                    "Source anchor: ${manifest?.sourceLabel ?: "unknown"}. No mapping coordinates stored."
                }
                _ui.update {
                    it.copy(
                        mapName = manifest?.name ?: "Map",
                        sourceLine = anchor,
                        gpsTags = gpsTags,
                        issues = issues,
                        logs = logs,
                        busy = false,
                    )
                }
            } catch (error: Exception) {
                _ui.update { it.copy(busy = false, error = error.message ?: "Could not read this map.") }
            }
        }
    }

    fun selfCheck() {
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, error = null) }
            try {
                val map = repository.loadLocalizationMap(mapId)
                val report = MapEvaluator.leaveOneOut(map)
                _ui.update { it.copy(busy = false, report = report.summary()) }
            } catch (error: Exception) {
                _ui.update { it.copy(busy = false, error = error.message ?: "The self-check failed.") }
            }
        }
    }

    fun export(uri: Uri) {
        viewModelScope.launch {
            try {
                val stream = getApplication<Application>().contentResolver.openOutputStream(uri)
                    ?: error("Could not open the destination.")
                stream.use { repository.export(mapId, it) }
            } catch (error: Exception) {
                _ui.update { it.copy(error = error.message ?: "Export failed.") }
            }
        }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            try {
                repository.deleteMap(mapId)
                onDeleted()
            } catch (error: Exception) {
                _ui.update { it.copy(error = error.message ?: "Could not delete the map.") }
            }
        }
    }

    companion object {
        fun factory(mapId: String): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val app = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    ?: error("Application is unavailable")
                @Suppress("UNCHECKED_CAST")
                return DiagnosticsViewModel(app as Application, mapId) as T
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiagnosticsScreen(
    mapId: String,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    model: DiagnosticsViewModel = viewModel(
        key = "diagnostics-$mapId",
        factory = DiagnosticsViewModel.factory(mapId),
    ),
) {
    val ui by model.ui.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) model.export(uri)
    }
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ScreenHeader(ui.mapName, "Map check", onBack)
            Panel {
                SectionTitle("Source")
                Text(ui.sourceLine, style = MaterialTheme.typography.bodyLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatChip("${ui.gpsTags} GPS tags")
                }
                Text(
                    "GPS tags stay with the reference photos. They are not used to localize.",
                    color = Mute,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Panel {
                SectionTitle("Validation")
                if (ui.issues.isEmpty()) {
                    Text("No issues.", color = Moss, style = MaterialTheme.typography.bodyLarge)
                } else {
                    ui.issues.forEach { issue ->
                        val error = issue.severity == IssueSeverity.ERROR
                        Text(
                            "${if (error) "Error" else "Warning"}  ·  ${issue.message}",
                            color = if (error) Danger else Amber,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            Button(onClick = model::selfCheck, enabled = !ui.busy, modifier = Modifier.fillMaxWidth()) {
                Text(if (ui.busy) "Checking" else "Run leave-one-out check")
            }
            if (ui.report != null) {
                Panel {
                    Text(ui.report ?: "", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Panel {
                SectionTitle("Recent estimates")
                if (ui.logs.isEmpty()) {
                    Text("No localization log yet.", color = Mute, style = MaterialTheme.typography.bodyMedium)
                } else {
                    ui.logs.forEach { line ->
                        val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(line.timestampEpochMs))
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(time, color = Mute, style = MaterialTheme.typography.labelLarge)
                            Text(line.summary, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            if (ui.error != null) Notice(ui.error ?: "", alert = true)
            OutlinedButton(
                onClick = { exportLauncher.launch("${ui.mapName.ifBlank { "map" }}.vio.zip") },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Export map package") }
            TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Delete map", color = Danger)
            }
            Text(
                "Deleting removes the map, its images, and its descriptors from this device.",
                color = Mute,
                modifier = Modifier.padding(bottom = 24.dp),
            )
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this map?") },
            text = { Text("Images and descriptors stored for this map will be removed from the device.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    model.delete(onDeleted)
                }) { Text("Delete", color = Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
