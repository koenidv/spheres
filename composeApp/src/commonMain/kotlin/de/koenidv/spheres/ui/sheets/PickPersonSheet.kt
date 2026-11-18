package de.koenidv.spheres.ui.sheets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.koenidv.spheres.data.LogMode
import de.koenidv.spheres.data.SheetState
import de.koenidv.spheres.data.SpheresStore
import de.koenidv.spheres.domain.ContactScore
import de.koenidv.spheres.domain.EntryKind
import de.koenidv.spheres.domain.Person
import de.koenidv.spheres.domain.contactScore
import de.koenidv.spheres.domain.entriesByPerson
import de.koenidv.spheres.domain.firstName
import de.koenidv.spheres.domain.intervalInfo
import de.koenidv.spheres.platform.currentTimeMillis
import de.koenidv.spheres.platform.haptic
import de.koenidv.spheres.ui.components.ButtonSize
import de.koenidv.spheres.ui.components.HealthRing
import de.koenidv.spheres.ui.components.IconName
import de.koenidv.spheres.ui.components.PersonAvatar
import de.koenidv.spheres.ui.components.PixelButton
import de.koenidv.spheres.ui.components.PixelIcon
import de.koenidv.spheres.ui.components.PixelTextInput
import de.koenidv.spheres.ui.components.PxClipShape
import de.koenidv.spheres.ui.components.autoFocus
import de.koenidv.spheres.ui.theme.Ink
import de.koenidv.spheres.ui.theme.Muted
import de.koenidv.spheres.ui.theme.PixelFontFamily
import de.koenidv.spheres.ui.theme.SansFontFamily
import de.koenidv.spheres.ui.theme.parseHexColor
import de.koenidv.spheres.ui.theme.tintColor

@Composable
fun PickPersonSheet(
    sheet: SheetState.PickPerson
) {
    val allowMulti = sheet.then == LogMode.INTERACTION
    PersonPickerList(
        allowMultiSelect = allowMulti,
        onSelect = { person ->
            SpheresStore.openSheet(
                SheetState.Log(
                    personId = person.id,
                    mode = sheet.then
                )
            )
        },
        onSelectMultiple = { selected ->
            if (selected.isNotEmpty()) {
                SpheresStore.openSheet(
                    SheetState.Log(
                        personId = selected.first().id,
                        mode = sheet.then,
                        personIds = selected.map { it.id }
                    )
                )
            }
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

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun PersonPickerList(
    allowMultiSelect: Boolean = false,
    onSelect: (Person) -> Unit,
    onSelectMultiple: ((List<Person>) -> Unit)? = null,
) {
    val state by SpheresStore.state.collectAsState()
    val people = state.people
    val entries = state.entries
    var q by remember { mutableStateOf("") }
    var selectedIds by remember { mutableStateOf<List<String>>(emptyList()) }
    val selectedIdSet = remember(selectedIds) { selectedIds.toSet() }
    val inSelectionMode = allowMultiSelect && selectedIds.isNotEmpty()

    val selectedPeople = remember(selectedIds, people) {
        val byId = people.associateBy { it.id }
        selectedIds.mapNotNull { byId[it] }
    }

    val sortedPeople = remember(people, entries) {
        val by = entriesByPerson(entries)
        val now = currentTimeMillis()
        people
            .map { p -> p to contactScore(p, by[p.id], now) }
            .sortedWith(
                compareByDescending<Pair<Person, ContactScore?>> { it.second?.priority ?: -1.0 }
                    .thenBy { it.first.name }
            )
    }

    val list = remember(sortedPeople, q) {
        val needle = q.trim().lowercase()
        if (needle.isEmpty()) sortedPeople
        else sortedPeople.filter { it.first.name.lowercase().contains(needle) }
    }

    fun togglePerson(id: String) {
        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
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

        if (allowMultiSelect && selectedIds.isEmpty() && people.size > 1) {
            Text(
                text = "Long press to select multiple",
                style = TextStyle(
                    fontFamily = SansFontFamily(),
                    fontSize = 12.sp,
                    color = Muted
                ),
                modifier = Modifier.padding(top = 8.dp, start = 4.dp)
            )
        }

        AnimatedVisibility(
            visible = selectedPeople.isNotEmpty(),
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                selectedPeople.forEach { sp ->
                    val spInfo = intervalInfo(sp.intervalDays)
                    val spColor = parseHexColor(spInfo.color)
                    Row(
                        modifier = Modifier
                            .clip(PxClipShape)
                            .background(tintColor(spColor, 20))
                            .border(1.dp, spColor.copy(alpha = 0.45f), PxClipShape)
                            .clickable {
                                haptic()
                                togglePerson(sp.id)
                            }
                            .padding(start = 6.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PersonAvatar(
                            person = sp,
                            colorHex = spInfo.color,
                            size = 20.dp
                        )
                        Text(
                            text = firstName(sp.name),
                            style = TextStyle(
                                fontFamily = PixelFontFamily(),
                                fontSize = 13.sp,
                                color = Ink
                            ),
                            maxLines = 1
                        )
                        PixelIcon(
                            name = IconName.CLOSE,
                            size = 11.dp,
                            tint = Ink.copy(alpha = 0.55f)
                        )
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(list, key = { it.first.id }) { (person, cs) ->
                val info = intervalInfo(person.intervalDays)
                val accent = parseHexColor(info.color)
                val isSelected = person.id in selectedIdSet
                val rowBg = if (isSelected) tintColor(accent, 18) else Color.Transparent
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(PxClipShape)
                        .background(rowBg)
                        .combinedClickable(
                            onClick = {
                                if (inSelectionMode) {
                                    haptic()
                                    togglePerson(person.id)
                                } else {
                                    onSelect(person)
                                }
                            },
                            onLongClick = if (allowMultiSelect) {
                                {
                                    haptic()
                                    togglePerson(person.id)
                                }
                            } else null
                        )
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
                    if (inSelectionMode) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(PxClipShape)
                                .background(if (isSelected) accent else Ink.copy(alpha = 0.08f))
                                .border(
                                    width = 1.5.dp,
                                    color = if (isSelected) accent else Ink.copy(alpha = 0.22f),
                                    shape = PxClipShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                PixelIcon(
                                    name = IconName.CHECK,
                                    size = 13.dp,
                                    tint = Color.White
                                )
                            }
                        }
                    } else if (cs != null) {
                        HealthRing(score = cs.score)
                    }
                }
            }

            if (list.isEmpty()) {
                item(key = "empty") {
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

        AnimatedVisibility(
            visible = selectedPeople.isNotEmpty(),
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.height(12.dp))
                val label = if (selectedPeople.size == 1) {
                    "Continue with ${firstName(selectedPeople[0].name)}"
                } else {
                    "Continue (${selectedPeople.size} people)"
                }
                PixelButton(
                    text = label,
                    icon = IconName.CHECK,
                    size = ButtonSize.LG,
                    fullWidth = true,
                    onClick = {
                        if (selectedPeople.size == 1) {
                            onSelect(selectedPeople[0])
                        } else {
                            onSelectMultiple?.invoke(selectedPeople)
                        }
                    }
                )
            }
        }
    }
}
