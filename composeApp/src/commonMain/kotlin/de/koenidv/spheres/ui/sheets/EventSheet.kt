package de.koenidv.spheres.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.koenidv.spheres.data.SheetState
import de.koenidv.spheres.data.SpheresStore
import de.koenidv.spheres.domain.SPHERE_COLORS
import de.koenidv.spheres.platform.currentTimeMillis
import de.koenidv.spheres.ui.components.ButtonSize
import de.koenidv.spheres.ui.components.ButtonVariant
import de.koenidv.spheres.ui.components.Chip
import de.koenidv.spheres.ui.components.Field
import de.koenidv.spheres.ui.components.IconName
import de.koenidv.spheres.ui.components.NativeDatePickerField
import de.koenidv.spheres.ui.components.PixelButton
import de.koenidv.spheres.ui.components.PixelIcon
import de.koenidv.spheres.ui.components.PixelTextArea
import de.koenidv.spheres.ui.components.PixelTextInput
import de.koenidv.spheres.ui.components.PxClipShape
import de.koenidv.spheres.ui.theme.FieldBg
import de.koenidv.spheres.ui.theme.Ink
import de.koenidv.spheres.ui.theme.SansFontFamily
import de.koenidv.spheres.ui.theme.parseHexColor
import kotlinx.coroutines.delay

@Composable
fun EventSheet(
    sheet: SheetState.EventEdit,
    onNavigate: (String) -> Unit
) {
    val state by SpheresStore.state.collectAsState()
    val existing = state.events.firstOrNull { it.id == sheet.eventId } ?: return
    val spheres = state.spheres
    val people = state.people

    var name by remember { mutableStateOf(existing.name) }
    var dateMillis by remember { mutableStateOf(existing.date) }
    var sphereIds by remember { mutableStateOf(existing.sphereIds) }
    var color by remember { mutableStateOf(existing.color) }
    var note by remember { mutableStateOf(existing.note) }
    var confirm by remember { mutableStateOf(false) }
    var alsoPeople by remember { mutableStateOf(false) }

    val onlyHere = remember(people, existing.id) {
        people.count { p ->
            p.eventIds.contains(existing.id) &&
                p.intervalDays == null &&
                p.eventIds.size == 1 &&
                p.sphereIds.isEmpty()
        }
    }

    LaunchedEffect(confirm) {
        if (confirm) {
            delay(4000)
            confirm = false
        }
    }

    val toggle = { id: String ->
        sphereIds = if (sphereIds.contains(id)) sphereIds - id else sphereIds + id
    }

    val save = {
        val n = name.trim()
        if (n.isNotEmpty()) {
            SpheresStore.updateEvent(
                id = existing.id,
                name = n,
                date = dateMillis,
                sphereIds = sphereIds,
                color = color,
                note = note.trim()
            )
            SpheresStore.showToast("Event saved")
            SpheresStore.closeSheet()
        }
    }

    val remove = {
        if (!confirm) {
            confirm = true
        } else {
            SpheresStore.deleteEvent(existing.id, deleteUntrackedMembers = alsoPeople)
            SpheresStore.closeSheet()
            onNavigate("/spheres")
            SpheresStore.showToast("Event deleted")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        PixelTextInput(
            value = name,
            onValueChange = { name = it },
            placeholder = "Event name"
        )

        Field(label = "Date") {
            NativeDatePickerField(
                dateMillis = dateMillis,
                onDateSelected = { dateMillis = it }
            )
        }

        if (spheres.isNotEmpty()) {
            Field(label = "Spheres") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    spheres.forEach { s ->
                        Chip(
                            label = s.name,
                            color = parseHexColor(s.color),
                            selected = sphereIds.contains(s.id),
                            onClick = { toggle(s.id) }
                        )
                    }
                }
            }
        }

        Field(label = "Color") {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SPHERE_COLORS.forEach { c ->
                    val isSelected = color == c
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(PxClipShape)
                            .background(parseHexColor(c))
                            .clickable { color = c },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(Color.Black)
                            )
                        }
                    }
                }
            }
        }

        Field(label = "Note (optional)") {
            PixelTextArea(
                value = note,
                onValueChange = { note = it }
            )
        }

        Spacer(modifier = Modifier.height(4.dp))
        PixelButton(
            text = "Save",
            size = ButtonSize.LG,
            fullWidth = true,
            enabled = name.isNotBlank(),
            onClick = save
        )

        if (confirm && onlyHere > 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { alsoPeople = !alsoPeople }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(PxClipShape)
                        .background(if (alsoPeople) Ink else FieldBg),
                    contentAlignment = Alignment.Center
                ) {
                    if (alsoPeople) {
                        PixelIcon(
                            name = IconName.CHECK,
                            size = 14.dp,
                            tint = Color.White
                        )
                    }
                }
                Text(
                    text = "Also delete $onlyHere ${if (onlyHere == 1) "person" else "people"} only met here",
                    style = TextStyle(
                        fontFamily = SansFontFamily(),
                        fontSize = 15.sp,
                        color = Ink
                    )
                )
            }
        }

        PixelButton(
            text = if (confirm) "Tap again to delete" else "Delete event",
            variant = ButtonVariant.DANGER,
            size = ButtonSize.MD,
            fullWidth = true,
            icon = IconName.TRASH,
            onClick = remove
        )
    }
}
