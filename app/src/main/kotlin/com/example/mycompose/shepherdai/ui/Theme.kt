package com.example.mycompose.shepherdai.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

private val Green = Color(0xFF2E7D32)
private val Light = lightColorScheme(primary = Green, secondary = Color(0xFF6D4C41))
private val Dark = darkColorScheme(primary = Color(0xFF81C784), secondary = Color(0xFFBCAAA4))

@Composable
fun ShepherdTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
    }
}
