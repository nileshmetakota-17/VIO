package com.example.vio

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import com.example.vio.feature.capture.CaptureScreen
import com.example.vio.feature.diagnostics.DiagnosticsScreen
import com.example.vio.feature.home.HomeScreen
import com.example.vio.feature.localize.LocalizeScreen
import com.example.vio.feature.settings.SettingsScreen

sealed interface Screen {
    data object Home : Screen
    data class Capture(val mapId: String) : Screen
    data class Localize(val mapId: String) : Screen
    data class Diagnostics(val mapId: String) : Screen
    data object Settings : Screen
}

@Composable
fun VioApp() {
    val stack = remember { mutableStateListOf<Screen>(Screen.Home) }
    BackHandler(enabled = stack.size > 1) {
        stack.removeAt(stack.lastIndex)
    }
    when (val screen = stack.last()) {
        Screen.Home -> HomeScreen(
            onCapture = { stack += Screen.Capture(it) },
            onLocalize = { stack += Screen.Localize(it) },
            onDiagnostics = { stack += Screen.Diagnostics(it) },
            onSettings = { stack += Screen.Settings },
        )
        Screen.Settings -> SettingsScreen(onBack = { stack.removeAt(stack.lastIndex) })
        is Screen.Capture -> CaptureScreen(
            mapId = screen.mapId,
            onBack = { stack.removeAt(stack.lastIndex) },
            onSettings = { stack += Screen.Settings },
        )
        is Screen.Localize -> LocalizeScreen(
            mapId = screen.mapId,
            onBack = { stack.removeAt(stack.lastIndex) },
            onDiagnostics = { stack += Screen.Diagnostics(screen.mapId) },
            onSettings = { stack += Screen.Settings },
        )
        is Screen.Diagnostics -> DiagnosticsScreen(
            mapId = screen.mapId,
            onBack = { stack.removeAt(stack.lastIndex) },
            onDeleted = {
                stack.clear()
                stack += Screen.Home
            },
        )
    }
}
