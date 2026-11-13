package com.example.mycompose.shepherdai.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.example.mycompose.shepherdai.ai.ModelManager
import com.example.mycompose.shepherdai.data.AppDatabase

sealed interface Screen {
    data object Home : Screen
    data object Animals : Screen
    data object Add : Screen
    data object Identify : Screen
    data object Settings : Screen
    data object Scan : Screen
    data class Profile(val id: Long) : Screen
}

@Composable
fun App() {
    val ctx = LocalContext.current
    val dao = remember { AppDatabase.get(ctx).dao() }
    val mm = remember { ModelManager(ctx) }
    val prefs = remember { Prefs(ctx) }
    val stack = remember { mutableStateListOf<Screen>(Screen.Home) }
    val back: () -> Unit = { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    val go: (Screen) -> Unit = { stack.add(it) }
    BackHandler(stack.size > 1) { back() }
    when (val s = stack.last()) {
        Screen.Home -> HomeScreen(dao, mm, go)
        Screen.Animals -> AnimalsScreen(dao, go, back)
        Screen.Add -> AddScreen(dao, mm, back)
        Screen.Identify -> IdentifyScreen(dao, mm, prefs, back)
        Screen.Settings -> SettingsScreen(mm, prefs, back)
        Screen.Scan -> ScanScreen(dao, mm, prefs, back)
        is Screen.Profile -> ProfileScreen(dao, s.id, back)
    }
}
