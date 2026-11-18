package de.koenidv.spheres.ui.sheets

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import de.koenidv.spheres.data.LogMode
import de.koenidv.spheres.data.SheetState
import de.koenidv.spheres.data.SpheresStore
import de.koenidv.spheres.ui.components.PixelSheet

@Composable
fun SheetHost(
    onNavigate: (String) -> Unit
) {
    val state by SpheresStore.state.collectAsState()
    val sheet = state.sheet

    var lastSheet by remember { mutableStateOf<SheetState?>(sheet) }
    if (sheet != null) {
        lastSheet = sheet
    }
    val activeSheet = sheet ?: lastSheet

    val title = when (activeSheet) {
        is SheetState.AddPerson -> "New connection"
        is SheetState.SphereEdit -> if (activeSheet.sphereId != null) "Edit sphere" else "New sphere"
        is SheetState.EventEdit -> "Edit event"
        is SheetState.AvatarEdit -> "Avatar"
        is SheetState.LocationEdit -> "Location"
        is SheetState.PickPerson -> if (activeSheet.then == LogMode.NOTE) "Note about…" else "Interaction with…"
        is SheetState.LinkContact -> "Link ${activeSheet.contactName}"
        else -> null
    }

    PixelSheet(
        open = sheet != null,
        onClose = { SpheresStore.closeSheet() },
        title = title,
        scrollable = activeSheet !is SheetState.PickPerson && activeSheet !is SheetState.LinkContact
    ) {
        AnimatedContent(
            targetState = activeSheet,
            contentKey = { it?.let { s -> s::class } },
            transitionSpec = {
                (fadeIn(tween(140)) togetherWith fadeOut(tween(110))).using(
                    SizeTransform(clip = false)
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(spring(stiffness = 500f, dampingRatio = 0.92f))
        ) { current ->
            when (current) {
                is SheetState.Action -> ActionSheet(onNavigate = onNavigate)
                is SheetState.AddPerson -> AddPersonSheet(sheet = current)
                is SheetState.SphereEdit -> SphereSheet(sheet = current, onNavigate = onNavigate)
                is SheetState.EventEdit -> EventSheet(sheet = current, onNavigate = onNavigate)
                is SheetState.AvatarEdit -> AvatarSheet(sheet = current)
                is SheetState.LocationEdit -> LocationSheet(sheet = current)
                is SheetState.PickPerson -> PickPersonSheet(sheet = current)
                is SheetState.LinkContact -> LinkContactPickPersonSheet(sheet = current)
                is SheetState.Log -> LogSheet(sheet = current)
                is SheetState.Paywall -> UnlockSheet(sheet = current)
                null -> Unit
            }
        }
    }
}
