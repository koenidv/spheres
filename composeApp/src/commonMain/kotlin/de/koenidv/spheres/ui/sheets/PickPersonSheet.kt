package de.koenidv.spheres.ui.sheets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.koenidv.spheres.data.SheetState
import de.koenidv.spheres.data.SpheresStore
import de.koenidv.spheres.domain.ContactScore
import de.koenidv.spheres.domain.EntryKind
import de.koenidv.spheres.domain.Person
import de.koenidv.spheres.domain.contactScore
import de.koenidv.spheres.domain.entriesByPerson
import de.koenidv.spheres.domain.intervalInfo
import de.koenidv.spheres.platform.currentTimeMillis
import de.koenidv.spheres.ui.components.HealthRing
import de.koenidv.spheres.ui.components.PersonAvatar
import de.koenidv.spheres.ui.components.PixelTextInput
import de.koenidv.spheres.ui.components.PxClipShape
import de.koenidv.spheres.ui.components.autoFocus
import de.koenidv.spheres.ui.theme.Ink
import de.koenidv.spheres.ui.theme.Muted
import de.koenidv.spheres.ui.theme.PixelFontFamily
import de.koenidv.spheres.ui.theme.SansFontFamily

@Composable
fun PickPersonSheet(
    sheet: SheetState.PickPerson
) {
    PersonPickerList(
        onSelect = { person ->
            SpheresStore.openSheet(
                SheetState.Log(
                    personId = person.id,
                    mode = sheet.then
                )
            )
        }
    )
}

@Composable
fun LinkContactPickPersonSheet(
    sheet: SheetState.LinkContact
) {
    PersonPickerList(
        onSelect = { person ->
            SpheresStore.linkContact(person.id, sheet.contactId, sheet.contactName)
            SpheresStore.logEntry(
                personId = person.id,
                kind = EntryKind.CALL,
                text = "${sheet.durationMinutes} min call",
                at = sheet.callStartedAt,
            )
            SpheresStore.showToast("Linked ${sheet.contactName} to ${person.name}")
            SpheresStore.closeSheet()
        }
    )
}

@Composable
private fun PersonPickerList(
    onSelect: (Person) -> Unit
) {
    val state by SpheresStore.state.collectAsState()
    val people = state.people
    val entries = state.entries
    var q by remember { mutableStateOf("") }

    val list = remember(people, entries, q) {
        val by = entriesByPerson(entries)
        val now = currentTimeMillis()
        val needle = q.trim().lowercase()
        people
            .filter { needle.isEmpty() || it.name.lowercase().contains(needle) }
            .map { p ->
                val cs = contactScore(p, by[p.id], now)
                p to cs
            }
            .sortedWith(
                compareByDescending<Pair<Person, ContactScore?>> { it.second?.priority ?: -1.0 }
                    .thenBy { it.first.name }
            )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
    ) {
        PixelTextInput(
            value = q,
            onValueChange = { q = it },
            placeholder = "Search connections",
            modifier = Modifier.autoFocus()
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
        ) {
            list.forEach { (person, cs) ->
                val info = intervalInfo(person.intervalDays)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(PxClipShape)
                        .clickable { onSelect(person) }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PersonAvatar(
                        person = person,
                        colorHex = info.color,
                        size = 40.dp
                    )
                    Text(
                        text = person.name,
                        style = TextStyle(
                            fontFamily = PixelFontFamily(),
                            fontSize = 18.sp,
                            color = Ink
                        ),
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = info.label,
                        style = TextStyle(
                            fontFamily = PixelFontFamily(),
                            fontSize = 14.sp,
                            color = Muted
                        )
                    )
                    if (cs != null) {
                        HealthRing(score = cs.score)
                    }
                }
            }

            if (list.isEmpty()) {
                Text(
                    text = if (people.isEmpty()) "No connections yet." else "No matches.",
                    style = TextStyle(
                        fontFamily = SansFontFamily(),
                        fontSize = 15.sp,
                        color = Muted,
                        textAlign = TextAlign.Center
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp)
                )
            }
        }
    }
}
