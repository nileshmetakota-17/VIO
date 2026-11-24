package com.example.vio

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.vio.ui.theme.VIOTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ink = Color.parseColor("#12160F")
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(ink),
            navigationBarStyle = SystemBarStyle.dark(ink),
        )
        setContent {
            VIOTheme {
                VioApp()
            }
        }
    }
}
