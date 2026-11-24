package com.example.vio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Image
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.res.painterResource
import com.example.vio.R
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.vio.core.model.LocalizationState
import com.example.vio.ui.theme.Amber
import com.example.vio.ui.theme.Danger
import com.example.vio.ui.theme.InkHigh
import com.example.vio.ui.theme.Line
import com.example.vio.ui.theme.MetricStyle
import com.example.vio.ui.theme.Moss
import com.example.vio.ui.theme.Mute

@Composable
fun ScreenHeader(
    title: String,
    subtitle: String?,
    onBack: (() -> Unit)?,
    onSettings: (() -> Unit)? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(top = 4.dp)) {
        if (onBack != null || onSettings != null) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) {
                    TextButton(
                        onClick = onBack,
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
                    ) {
                        Text("Back", color = Amber)
                    }
                }
                Spacer(Modifier.weight(1f))
                if (onSettings != null) {
                    IconButton(onClick = onSettings) {
                        Image(
                            painter = painterResource(R.drawable.ic_settings),
                            contentDescription = "Settings",
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
            }
        }
        Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (!subtitle.isNullOrBlank()) {
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Mute)
        }
    }
}

@Composable
fun GpsFixLine(fixed: Boolean, detail: String) {
    val color = if (fixed) Moss else Danger
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(color.copy(alpha = 0.16f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            Text(
                if (fixed) "GPS fix" else "No GPS fix",
                color = color,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        if (detail.isNotBlank()) {
            Text(detail, color = Mute, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun LiveStatus(rows: List<Pair<String, String>>) {
    Panel {
        SectionTitle("Now")
        rows.forEach { (label, value) ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, color = Mute, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(value, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = Mute,
    )
}

@Composable
fun Panel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
fun StatePill(state: LocalizationState) {
    val color = stateColor(state)
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Text(
            text = state.name,
            color = color,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun MetricBlock(label: String, value: String, detail: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = Mute)
        Text(value, style = MetricStyle, color = MaterialTheme.colorScheme.onSurface)
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = Mute)
    }
}

@Composable
fun ConfidenceBar(confidence: Float) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Score", style = MaterialTheme.typography.labelMedium, color = Mute)
            Text(formatScore(confidence), style = MaterialTheme.typography.labelLarge, color = Amber)
        }
        LinearProgressIndicator(
            progress = { confidence.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
            color = Amber,
            trackColor = Line,
        )
    }
}

@Composable
fun Notice(text: String, alert: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (alert) Danger else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (alert) Danger.copy(alpha = 0.12f) else InkHigh)
            .padding(12.dp),
    )
}

@Composable
fun SettingRow(title: String, detail: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = Mute)
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
fun SelectRow(title: String, detail: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) InkHigh else Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, if (selected) Amber else Line, RoundedCornerShape(12.dp)),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = Mute)
            }
            if (selected) {
                Text("Selected", color = Amber, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
fun SegmentedActions(actions: List<Pair<String, () -> Unit>>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(InkHigh),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        actions.forEachIndexed { index, (label, action) ->
            if (index > 0) {
                Box(Modifier.width(1.dp).fillMaxHeight().background(Line))
            }
            TextButton(onClick = action, modifier = Modifier.weight(1f)) {
                Text(label, color = Amber, maxLines = 1)
            }
        }
    }
}

@Composable
fun StatChip(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .clip(CircleShape)
            .background(InkHigh)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
fun ReferenceRow(title: String, meta: String, onRemove: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(meta, style = MaterialTheme.typography.bodyMedium, color = Mute, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = onRemove) { Text("Remove", color = Danger) }
    }
}

fun stateColor(state: LocalizationState): Color = when (state) {
    LocalizationState.LOCALIZED -> Moss
    LocalizationState.UNCERTAIN -> Amber
    LocalizationState.LOST -> Danger
    LocalizationState.INITIALIZING -> Mute
}
