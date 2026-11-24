package com.example.vio.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.vio.core.settings.AppSettings
import com.example.vio.ui.Notice
import com.example.vio.ui.Panel
import com.example.vio.ui.ScreenHeader
import com.example.vio.ui.SectionTitle
import com.example.vio.ui.SettingRow
import com.example.vio.ui.theme.Mute

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { AppSettings(context) }
    var draft by remember { mutableStateOf(store.read()) }
    var customZone by remember { mutableStateOf(trim(draft.zoneDistanceMeters)) }
    var seconds by remember { mutableStateOf(draft.intervalSeconds.toString()) }
    var moved by remember { mutableStateOf(trim(draft.moveMeters)) }
    var turn by remember { mutableStateOf(draft.turnDegrees.toInt().toString()) }
    var drift by remember { mutableStateOf(draft.driftDegrees.toInt().toString()) }
    var accuracy by remember { mutableStateOf(trim(draft.accuracyTargetMeters)) }
    var savedNote by remember { mutableStateOf<String?>(null) }
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ScreenHeader("Settings", "Change the values, then tap Update. Nothing is stored until then.", onBack)
            if (savedNote != null) Notice(savedNote ?: "")
            Panel {
                SettingRow(
                    "Show details on Map",
                    if (draft.showMapDetails) "The zone, distance, and sensor lines are visible."
                    else "Those lines are hidden on the Map screen.",
                    draft.showMapDetails,
                ) { draft = draft.copy(showMapDetails = it) }
            }
            Panel {
                SectionTitle("Zone distance")
                Text("A new zone opens after this many metres from the start.", color = Mute, style = MaterialTheme.typography.bodyMedium)
                ChoiceRow(listOf(5.0, 10.0, 20.0, 30.0), draft.zoneDistanceMeters) { meters ->
                    customZone = trim(meters)
                    draft = draft.copy(zoneDistanceMeters = meters)
                }
                OutlinedTextField(
                    customZone,
                    {
                        customZone = it.filter { char -> char.isDigit() || char == '.' }.take(6)
                        it.toDoubleOrNull()?.takeIf { value -> value >= 1.0 }?.let { value ->
                            draft = draft.copy(zoneDistanceMeters = value)
                        }
                    },
                    label = { Text("Custom metres") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Panel {
                SectionTitle("GPS closeness")
                Text(
                    "Type how tight the GPS fix should be, in metres. 2 means warn unless the phone’s uncertainty is within 2 m. This does not make the GPS sharper.",
                    color = Mute,
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    accuracy,
                    {
                        val typed = it.filter { char -> char.isDigit() || char == '.' }.take(6)
                        accuracy = typed
                        typed.toDoubleOrNull()?.takeIf { value -> value > 0.0 }?.let { value ->
                            draft = draft.copy(accuracyTargetMeters = value.coerceIn(0.1, 100.0))
                        }
                    },
                    label = { Text("Accuracy, m") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Panel {
                SectionTitle("When a photo is saved")
                SettingRow("By time", "Seconds since the last photo.", draft.useTime) { draft = draft.copy(useTime = it) }
                SettingRow("By distance moved", "Metres of GPS movement since the last photo.", draft.useMove) { draft = draft.copy(useMove = it) }
                SettingRow("By a large turn", "Heading change since the last photo.", draft.useTurn) { draft = draft.copy(useTurn = it) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(seconds, { seconds = it.filter { char -> char.isDigit() }.take(3); it.toIntOrNull()?.let { value -> draft = draft.copy(intervalSeconds = value.coerceIn(1, 600)) } }, label = { Text("Seconds") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(moved, { moved = it.filter { char -> char.isDigit() || char == '.' }.take(6); it.toDoubleOrNull()?.let { value -> draft = draft.copy(moveMeters = value.coerceIn(1.0, 500.0)) } }, label = { Text("Metres") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(turn, { turn = it.filter { char -> char.isDigit() }.take(3); it.toFloatOrNull()?.let { value -> draft = draft.copy(turnDegrees = value.coerceIn(5f, 180f)) } }, label = { Text("Turn °") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                SettingRow("Automatic saving", "Save when one of the rules above is reached.", draft.autoSave) { draft = draft.copy(autoSave = it) }
            }
            Panel {
                SettingRow("Automatic zones", "Recognize a zone you enter again, or open the next one.", draft.autoZoneDetection) {
                    draft = draft.copy(autoZoneDetection = it)
                }
                SettingRow("Place names", "Use a place name when the phone has one. Otherwise Zone 1, Zone 2.", draft.placeNames) {
                    draft = draft.copy(placeNames = it)
                }
                SettingRow("Type the zone yourself", "On the Map screen, enter the name and the metres.", draft.manualFields) {
                    draft = draft.copy(manualFields = it)
                }
                SettingRow("Automatic re-localization", "After a large IMU jump, match the saved photos again.", draft.autoRelocalize) {
                    draft = draft.copy(autoRelocalize = it)
                }
                SettingRow("Several photos per zone", "Keep more than one reference in the same zone.", draft.multipleImages) {
                    draft = draft.copy(multipleImages = it)
                }
                OutlinedTextField(
                    drift,
                    {
                        drift = it.filter { char -> char.isDigit() }.take(3)
                        it.toFloatOrNull()?.let { value -> draft = draft.copy(driftDegrees = value.coerceIn(5f, 180f)) }
                    },
                    label = { Text("IMU drift, degrees") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Button(
                onClick = {
                    val accuracyMeters = accuracy.toDoubleOrNull()?.takeIf { it > 0.0 }?.coerceIn(0.1, 100.0) ?: 2.0
                    val next = draft.copy(accuracyTargetMeters = accuracyMeters)
                    draft = next
                    accuracy = trim(accuracyMeters)
                    store.write(next)
                    savedNote = "Updated. Open a map to use these values."
                },
                modifier = Modifier.fillMaxWidth().padding(bottom = 28.dp),
            ) { Text("Update") }
        }
    }
}

@Composable
private fun ChoiceRow(choices: List<Double>, selected: Double, onSelect: (Double) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        choices.forEach { choice ->
            FilterChip(
                selected = kotlin.math.abs(selected - choice) < 0.05,
                onClick = { onSelect(choice) },
                label = { Text(if (choice < 1.0) choice.toString() else trim(choice)) },
            )
        }
    }
}

private fun trim(value: Double): String = if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
