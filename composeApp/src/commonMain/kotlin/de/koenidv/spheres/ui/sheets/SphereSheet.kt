package de.koenidv.spheres.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.dp
import de.koenidv.spheres.data.SheetState
import de.koenidv.spheres.data.SpheresStore
import de.koenidv.spheres.domain.SPHERE_COLORS
import de.koenidv.spheres.ui.components.ButtonSize
import de.koenidv.spheres.ui.components.ButtonVariant
import de.koenidv.spheres.ui.components.Field
import de.koenidv.spheres.ui.components.IconName
import de.koenidv.spheres.ui.components.PixelButton
import de.koenidv.spheres.ui.components.PixelTextArea
import de.koenidv.spheres.ui.components.PixelTextInput
import de.koenidv.spheres.ui.components.PxClipShape
import de.koenidv.spheres.ui.theme.parseHexColor
import kotlinx.coroutines.delay

@Composable
fun SphereSheet(
    sheet: SheetState.SphereEdit,
    onNavigate: (String) -> Unit
) {
    val state by SpheresStore.state.collectAsState()
    val existing = state.spheres.firstOrNull { it.id == sheet.sphereId }

    var name by remember { mutableStateOf(existing?.name ?: "") }
    var color by remember { mutableStateOf(existing?.color ?: SPHERE_COLORS[3]) }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var confirm by remember { mutableStateOf(false) }

    LaunchedEffect(confirm) {
        if (confirm) {
            delay(4000)
            confirm = false
        }
    }

    val save = {
        val n = name.trim()
        if (n.isNotEmpty()) {
            if (existing != null) {
                SpheresStore.updateSphere(id = existing.id, name = n, color = color, note = note.trim())
                SpheresStore.showToast("Sphere saved")
                SpheresStore.closeSheet()
            } else {
                SpheresStore.addSphere(name = n, color = color, note = note.trim())
                SpheresStore.closeSheet()
                SpheresStore.showToast("Created $n")
            }
        }
    }

    val remove = {
        if (existing != null) {
            if (!confirm) {
                confirm = true
            } else {
                SpheresStore.deleteSphere(existing.id)
                SpheresStore.closeSheet()
                onNavigate("/spheres")
                SpheresStore.showToast("Sphere deleted")
            }
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
            placeholder = "Sphere name"
        )

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
            text = if (existing != null) "Save" else "Create sphere",
            size = ButtonSize.LG,
            fullWidth = true,
            enabled = name.isNotBlank(),
            onClick = save
        )

        if (existing != null) {
            PixelButton(
                text = if (confirm) "Tap again to delete" else "Delete sphere",
                variant = ButtonVariant.DANGER,
                size = ButtonSize.MD,
                fullWidth = true,
                icon = IconName.TRASH,
                onClick = remove
            )
        }
    }
}
