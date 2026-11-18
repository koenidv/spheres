package de.koenidv.spheres.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.koenidv.spheres.data.LogMode
import de.koenidv.spheres.data.SheetState
import de.koenidv.spheres.data.SpheresStore
import de.koenidv.spheres.domain.DAY
import de.koenidv.spheres.domain.EntryKind
import de.koenidv.spheres.domain.Gift
import de.koenidv.spheres.domain.InteractionKind
import de.koenidv.spheres.domain.Quality
import de.koenidv.spheres.domain.firstName
import de.koenidv.spheres.domain.intervalInfo
import de.koenidv.spheres.domain.toEntryKind
import de.koenidv.spheres.platform.currentTimeMillis
import de.koenidv.spheres.platform.randomUuid
import de.koenidv.spheres.ui.components.ButtonSize
import de.koenidv.spheres.ui.components.Chip
import de.koenidv.spheres.ui.components.Field
import de.koenidv.spheres.ui.components.IconName
import de.koenidv.spheres.ui.components.DatePickerField
import de.koenidv.spheres.ui.components.PersonAvatar
import de.koenidv.spheres.ui.components.PixelButton
import de.koenidv.spheres.ui.components.PixelTextArea
import de.koenidv.spheres.ui.components.QualityPicker
import de.koenidv.spheres.ui.components.Segmented
import de.koenidv.spheres.ui.components.SegmentedOption
import de.koenidv.spheres.ui.components.autoFocus
import de.koenidv.spheres.ui.theme.Ink
import de.koenidv.spheres.ui.theme.PixelFontFamily

private data class KindOption(val value: InteractionKind, val label: String, val icon: IconName)

private val KINDS = listOf(
    KindOption(InteractionKind.MEET, "Met", IconName.COFFEE),
    KindOption(InteractionKind.CALL, "Call", IconName.PHONE),
    KindOption(InteractionKind.MESSAGE, "Message", IconName.MESSAGE),
    KindOption(InteractionKind.OTHER, "Other", IconName.CHAT),
)

@Composable
fun LogSheet(
    sheet: SheetState.Log
) {
    val state by SpheresStore.state.collectAsState()
    val targetIds = sheet.personIds.ifEmpty { listOf(sheet.personId) }
    val selectedPeople = targetIds.mapNotNull { id -> state.people.firstOrNull { it.id == id } }
    val person = selectedPeople.firstOrNull()
    val name = firstName(person?.name ?: "them")
    val isGroup = selectedPeople.size > 1
    val isNote = sheet.mode == LogMode.NOTE

    var kind by remember { mutableStateOf(InteractionKind.CHECKIN) }
    var whenChoice by remember { mutableStateOf("today") }
    var dateMillis by remember { mutableStateOf(currentTimeMillis()) }
    var text by remember { mutableStateOf("") }
    var quality by remember { mutableStateOf<Quality?>(null) }
    var noteType by remember { mutableStateOf("note") }

    val save = {
        if (selectedPeople.isNotEmpty()) {
            if (isNote && noteType == "gift" && person != null) {
                val newGift = Gift(
                    id = randomUuid(),
                    text = text.trim(),
                    done = false
                )
                SpheresStore.updatePerson(person.id) { it.copy(gifts = it.gifts + newGift) }
                SpheresStore.showToast("Gift idea saved for $name")
                SpheresStore.closeSheet()
            } else {
                val at = when (whenChoice) {
                    "today" -> currentTimeMillis()
                    "yesterday" -> currentTimeMillis() - DAY
                    else -> dateMillis
                }
                val entryKind = if (isNote) EntryKind.NOTE else kind.toEntryKind()
                val trimmedText = text.trim()
                val entryAt = if (isNote) null else at
                selectedPeople.forEach { target ->
                    SpheresStore.logEntry(
                        personId = target.id,
                        kind = entryKind,
                        text = trimmedText,
                        at = entryAt,
                        quality = quality
                    )
                }
                SpheresStore.showToast(
                    when {
                        isNote -> "Note saved for $name"
                        isGroup && kind == InteractionKind.CHECKIN -> "Checked in with ${selectedPeople.size} people"
                        isGroup -> "Interaction with ${selectedPeople.size} people saved"
                        kind == InteractionKind.CHECKIN -> "Checked in with $name"
                        else -> "Interaction with $name saved"
                    }
                )
                SpheresStore.closeSheet()
            }
        }
    }

    val titleText = when {
        isNote -> "Note about $name"
        selectedPeople.size == 2 -> "Interaction with ${firstName(selectedPeople[0].name)} & ${firstName(selectedPeople[1].name)}"
        selectedPeople.size > 2 -> {
            val others = selectedPeople.size - 2
            "Interaction with ${firstName(selectedPeople[0].name)}, ${firstName(selectedPeople[1].name)} & $others ${if (others == 1) "other" else "others"}"
        }
        else -> "Interaction with $name"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (isGroup) {
                val shown = selectedPeople.take(4)
                val avatarSize = 36.dp
                val step = 24.dp
                val totalWidth = avatarSize + step * (shown.size - 1)
                Box(
                    modifier = Modifier
                        .width(totalWidth)
                        .height(avatarSize)
                ) {
                    shown.forEachIndexed { idx, p ->
                        val info = intervalInfo(p.intervalDays)
                        Box(
                            modifier = Modifier
                                .offset(x = step * idx)
                                .size(avatarSize)
                                .clip(CircleShape)
                                .background(Color.White)
                                .border(2.dp, Color.White, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            PersonAvatar(
                                person = p,
                                colorHex = info.color,
                                size = avatarSize - 4.dp
                            )
                        }
                    }
                }
            }
            Text(
                text = titleText,
                style = TextStyle(
                    fontFamily = PixelFontFamily(),
                    fontSize = 22.sp,
                    lineHeight = 28.sp,
                    color = Ink,
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (!isNote) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                KINDS.forEach { k ->
                    Chip(
                        label = k.label,
                        icon = k.icon,
                        selected = kind == k.value,
                        onClick = { kind = if (kind == k.value) InteractionKind.CHECKIN else k.value }
                    )
                }
            }

            Field(label = "When") {
                Segmented(
                    options = listOf(
                        SegmentedOption("today", "Today"),
                        SegmentedOption("yesterday", "Yesterday"),
                        SegmentedOption("pick", "Pick date")
                    ),
                    value = whenChoice,
                    onChange = { whenChoice = it }
                )
            }

            if (whenChoice == "pick") {
                DatePickerField(
                    dateMillis = dateMillis,
                    onDateSelected = { dateMillis = it }
                )
            }

            Field(label = "How was it?") {
                QualityPicker(
                    value = quality,
                    onChange = { quality = it }
                )
            }
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Chip(
                    label = "Note",
                    icon = IconName.NOTE,
                    selected = noteType == "note",
                    onClick = { noteType = "note" }
                )
                Chip(
                    label = "Gift idea",
                    icon = IconName.GIFT,
                    selected = noteType == "gift",
                    onClick = { noteType = "gift" }
                )
            }
        }

        val placeholder = if (isNote) {
            if (noteType == "note") "What do you want to remember?" else "What could you give them?"
        } else {
            "Anything worth remembering? (optional)"
        }
        val minLines = if (isNote) {
            if (noteType == "note") 5 else 2
        } else {
            3
        }

        PixelTextArea(
            value = text,
            onValueChange = { text = it },
            placeholder = placeholder,
            minLines = minLines,
            modifier = Modifier.autoFocus(enabled = isNote)
        )

        Spacer(modifier = Modifier.height(4.dp))
        PixelButton(
            text = "Save",
            size = ButtonSize.LG,
            fullWidth = true,
            enabled = person != null && (!isNote || text.isNotBlank()),
            onClick = save
        )
    }
}
