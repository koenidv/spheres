package de.koenidv.spheres.ui.sheets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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

    val title = when (sheet) {
        is SheetState.AddPerson -> "New connection"
        is SheetState.SphereEdit -> if (sheet.sphereId != null) "Edit sphere" else "New sphere"
        is SheetState.EventEdit -> "Edit event"
        is SheetState.PickPerson -> if (sheet.then == LogMode.NOTE) "Note about…" else "Interaction with…"
        else -> null
    }

    val sheetKey = when (sheet) {
        is SheetState.Action -> "action"
        is SheetState.AddPerson -> "addPerson-${sheet.name.orEmpty()}-${sheet.sphereId.orEmpty()}"
        is SheetState.SphereEdit -> "sphere-${sheet.sphereId.orEmpty()}"
        is SheetState.EventEdit -> "event-${sheet.eventId}"
        is SheetState.PickPerson -> "pickPerson-${sheet.then}"
        is SheetState.Log -> "log-${sheet.personId}-${sheet.mode}"
        null -> "none"
    }

    PixelSheet(
        open = sheet != null,
        onClose = { SpheresStore.closeSheet() },
        title = title
    ) {
        key(sheetKey) {
            when (val current = sheet) {
                is SheetState.Action -> ActionSheet(onNavigate = onNavigate)
                is SheetState.AddPerson -> AddPersonSheet(sheet = current)
                is SheetState.SphereEdit -> SphereSheet(sheet = current, onNavigate = onNavigate)
                is SheetState.EventEdit -> EventSheet(sheet = current, onNavigate = onNavigate)
                is SheetState.PickPerson -> PickPersonSheet(sheet = current)
                is SheetState.Log -> LogSheet(sheet = current)
                null -> Unit
            }
        }
    }
}
