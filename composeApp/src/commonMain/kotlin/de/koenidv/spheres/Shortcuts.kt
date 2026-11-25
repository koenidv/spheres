package de.koenidv.spheres

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow

enum class AppShortcut(val id: String) {
    NEW_CONNECTION("new"),
    LOG_INTERACTION("log"),
    SEARCH("search"),
    RADAR("radar"),
    DUE("due"),
    SPHERES("spheres"),
    MAP("map"),
    SETTINGS("settings"),
    HELP("help")
}

// Entry point for shortcuts that originate outside Compose, e.g. the iPad menu bar
object AppShortcuts {
    private val _events = MutableSharedFlow<AppShortcut>(extraBufferCapacity = 8)
    val events: SharedFlow<AppShortcut> = _events

    fun fire(id: String) {
        AppShortcut.entries.firstOrNull { it.id == id }?.let { _events.tryEmit(it) }
    }
}

// Whether the keyboard shortcut overview is showing
object ShortcutHelp {
    val open = MutableStateFlow(false)
}
