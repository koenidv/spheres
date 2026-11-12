package de.koenidv.spheres.ui.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import de.koenidv.spheres.domain.toEntryKind
import de.koenidv.spheres.platform.currentTimeMillis
import de.koenidv.spheres.platform.randomUuid
import de.koenidv.spheres.ui.components.ButtonSize
import de.koenidv.spheres.ui.components.Chip
import de.koenidv.spheres.ui.components.Field
import de.koenidv.spheres.ui.components.IconName
import de.koenidv.spheres.ui.components.PixelButton
import de.koenidv.spheres.ui.components.PixelTextArea
import de.koenidv.spheres.ui.components.PixelTextInput
import de.koenidv.spheres.ui.components.QualityPicker
import de.koenidv.spheres.ui.components.Segmented
import de.koenidv.spheres.ui.components.SegmentedOption
import de.koenidv.spheres.ui.theme.Ink
import de.koenidv.spheres.ui.theme.PixelFontFamily
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

private data class KindOption(val value: InteractionKind, val label: String, val icon: IconName)

private val KINDS = listOf(
    KindOption(InteractionKind.MEET, "Met", IconName.COFFEE),
    KindOption(InteractionKind.CALL, "Call", IconName.PHONE),
    KindOption(InteractionKind.MESSAGE, "Message", IconName.MESSAGE),
    KindOption(InteractionKind.OTHER, "Other", IconName.CHAT),
)

private fun todayStr(): String {
    val date = Instant.fromEpochMilliseconds(currentTimeMillis()).toLocalDateTime(TimeZone.currentSystemDefault()).date
    val m = date.monthNumber.toString().padStart(2, '0')
    val d = date.dayOfMonth.toString().padStart(2, '0')
    return "${date.year}-$m-$d"
}

private fun dateToTs(s: String): Long {
    return try {
        val parts = s.split("-").map { it.toInt() }
        val ldt = LocalDateTime(parts[0], parts[1], parts[2], 12, 0, 0, 0)
        ldt.toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
    } catch (_: Exception) {
        currentTimeMillis()
    }
}

@Composable
fun LogSheet(
    sheet: SheetState.Log
) {
    val state by SpheresStore.state.collectAsState()
    val person = state.people.firstOrNull { it.id == sheet.personId }
    val name = firstName(person?.name ?: "them")
    val isNote = sheet.mode == LogMode.NOTE

    var kind by remember { mutableStateOf(InteractionKind.CHECKIN) }
    var whenChoice by remember { mutableStateOf("today") }
    var date by remember { mutableStateOf(todayStr()) }
    var text by remember { mutableStateOf("") }
    var quality by remember { mutableStateOf<Quality?>(null) }
    var noteType by remember { mutableStateOf("note") }

    val save = {
        if (person != null) {
            if (isNote && noteType == "gift") {
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
                    else -> dateToTs(date)
                }
                SpheresStore.logEntry(
                    personId = person.id,
                    kind = if (isNote) EntryKind.NOTE else kind.toEntryKind(),
                    text = text.trim(),
                    at = if (isNote) null else at,
                    quality = quality
                )
                SpheresStore.showToast(
                    if (isNote) "Note saved for $name"
                    else if (kind == InteractionKind.CHECKIN) "Checked in with $name"
                    else "Interaction with $name saved"
                )
                SpheresStore.closeSheet()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            text = if (isNote) "Note about $name" else "Interaction with $name",
            style = TextStyle(
                fontFamily = PixelFontFamily(),
                fontSize = 22.sp,
                lineHeight = 28.sp,
                color = Ink,
                textAlign = TextAlign.Center
            ),
            modifier = Modifier.fillMaxWidth()
        )

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
                PixelTextInput(
                    value = date,
                    onValueChange = { date = it },
                    placeholder = "YYYY-MM-DD"
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
            minLines = minLines
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
