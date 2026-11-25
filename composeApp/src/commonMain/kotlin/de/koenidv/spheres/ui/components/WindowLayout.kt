package de.koenidv.spheres.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class WindowClass {
    COMPACT,
    MEDIUM,
    EXPANDED,
    LARGE;

    val isCompact: Boolean get() = this == COMPACT
    val hasSidebar: Boolean get() = this == EXPANDED || this == LARGE
    val hasFullSidebar: Boolean get() = this == LARGE
}

fun windowClassFor(width: Dp): WindowClass = when {
    width < 600.dp -> WindowClass.COMPACT
    width < 840.dp -> WindowClass.MEDIUM
    width < 1200.dp -> WindowClass.EXPANDED
    else -> WindowClass.LARGE
}

val LocalWindowClass = compositionLocalOf { WindowClass.COMPACT }

// Space screens reserve at the bottom for floating chrome (the bottom nav on compact)
val LocalBottomChromeInset = compositionLocalOf { 96.dp }

val ContentMaxWidth = 720.dp

// 0 outside the detail pane, 1 for the pane's root screen, higher when drilled in
val LocalDetailPaneDepth = compositionLocalOf { 0 }

@Composable
fun backIcon(): IconName = if (LocalDetailPaneDepth.current == 1) IconName.CLOSE else IconName.BACK

// Person currently open in the detail pane, for highlighting in lists
val LocalSelectedPersonId = compositionLocalOf<String?> { null }

// Width the radar should lay out for once the detail pane has finished opening or closing
val LocalRadarTargetWidth = compositionLocalOf<Dp?> { null }

// Sphere or event open next to the spheres list, for highlighting in it
val LocalSelectedRoute = compositionLocalOf<String?> { null }
