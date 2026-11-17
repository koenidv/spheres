package de.koenidv.spheres.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.koenidv.spheres.data.SheetState
import de.koenidv.spheres.data.SpheresStore
import de.koenidv.spheres.domain.DueStatus
import de.koenidv.spheres.domain.Entry
import de.koenidv.spheres.domain.Person
import de.koenidv.spheres.domain.SocialEvent
import de.koenidv.spheres.domain.Sphere
import de.koenidv.spheres.domain.contactScore
import de.koenidv.spheres.domain.dueInfo
import de.koenidv.spheres.domain.entriesByPerson
import de.koenidv.spheres.domain.formatDay
import de.koenidv.spheres.domain.inSphere
import de.koenidv.spheres.domain.intervalInfo
import de.koenidv.spheres.ui.components.ButtonSize
import de.koenidv.spheres.ui.components.ButtonVariant
import de.koenidv.spheres.ui.theme.CardBg
import de.koenidv.spheres.ui.components.Chip
import de.koenidv.spheres.ui.components.EmptyState
import de.koenidv.spheres.ui.components.HealthRing
import de.koenidv.spheres.ui.components.IconName
import de.koenidv.spheres.ui.components.PersonAvatar
import de.koenidv.spheres.ui.components.PixelButton
import de.koenidv.spheres.ui.components.PixelIcon
import de.koenidv.spheres.ui.components.PixelIconButton
import de.koenidv.spheres.ui.components.PixelPanel
import de.koenidv.spheres.ui.components.PixelTextInput
import de.koenidv.spheres.ui.components.PxClipShape
import de.koenidv.spheres.ui.components.rememberLiveNow
import de.koenidv.spheres.ui.theme.DangerText
import de.koenidv.spheres.ui.theme.Ink
import de.koenidv.spheres.ui.theme.Muted
import de.koenidv.spheres.ui.theme.PixelFontFamily
import de.koenidv.spheres.ui.theme.SansFontFamily
import de.koenidv.spheres.ui.theme.parseHexColor
import de.koenidv.spheres.ui.theme.tintColor

private data class MemberRowData(val person: Person, val priority: Double, val entries: List<Entry>?)

private fun statusText(p: Person, entries: List<Entry>?): Pair<String, Color> {
    val due = dueInfo(p, entries) ?: return Pair("Not tracked", Muted)
    return if (due.status == DueStatus.OVERDUE) {
        val n = -due.daysLeft
        Pair(if (n > 0) "$n ${if (n == 1) "day" else "days"} overdue" else "due today", DangerText)
    } else {
        Pair(if (due.daysLeft == 1) "due tomorrow" else "due in ${due.daysLeft} days", Muted)
    }
}

@Composable
fun SphereScreen(
    id: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by SpheresStore.state.collectAsState()
    val sphere = state.spheres.firstOrNull { it.id == id }
    val dataReady = state.dataReady
    val people = state.people
    val entries = state.entries
    val events = state.events

    if (sphere == null) {
        if (!dataReady) return
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.White)
                .statusBarsPadding()
                .padding(top = 48.dp)
        ) {
            EmptyState(
                icon = IconName.USERS,
                title = "Sphere not found",
                text = "It may have been deleted.",
                action = {
                    PixelButton(
                        text = "Back to spheres",
                        variant = ButtonVariant.PRIMARY,
                        onClick = { onNavigate("/spheres") }
                    )
                }
            )
        }
        return
    }

    var isAddingMembers by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isRenaming by remember { mutableStateOf(false) }
    var renameDraft by remember(sphere.name) { mutableStateOf(sphere.name) }

    val byPerson = remember(entries) { entriesByPerson(entries) }
    val now = rememberLiveNow()

    val memberRows = remember(people, entries, sphere.id, events, now) {
        people
            .filter { inSphere(it, sphere.id, events) }
            .map { p ->
                val pEntries = byPerson[p.id]
                val priority = contactScore(p, pEntries, now)?.priority ?: -1.0
                MemberRowData(p, priority, pEntries)
            }
            .sortedWith(compareByDescending<MemberRowData> { it.priority }.thenBy { it.person.name })
    }

    val sphereEvents = remember(events, sphere.id) {
        events.filter { it.sphereIds.contains(sphere.id) }
    }

    val candidates = remember(people, searchQuery) {
        val q = searchQuery.trim().lowercase()
        people
            .filter { q.isEmpty() || it.name.lowercase().contains(q) }
            .sortedBy { it.name.lowercase() }
    }

    val sphereColor = parseHexColor(sphere.color)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(bottom = 96.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PixelIconButton(
                    icon = IconName.BACK,
                    onClick = { onNavigate("..") }
                )
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(sphereColor)
                )
                Box(modifier = Modifier.weight(1f)) {
                    if (isRenaming) {
                        BasicTextField(
                            value = renameDraft,
                            onValueChange = { renameDraft = it },
                            textStyle = TextStyle(
                                fontFamily = PixelFontFamily(),
                                fontSize = 22.sp,
                                lineHeight = 28.sp,
                                color = Ink
                            ),
                            cursorBrush = SolidColor(Ink),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    val v = renameDraft.trim()
                                    if (v.isNotBlank() && v != sphere.name) {
                                        SpheresStore.updateSphere(sphere.id, name = v)
                                    }
                                    isRenaming = false
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Transparent)
                        )
                    } else {
                        Text(
                            text = sphere.name,
                            style = TextStyle(
                                fontFamily = PixelFontFamily(),
                                fontSize = 22.sp,
                                lineHeight = 28.sp,
                                color = Ink
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable {
                                renameDraft = sphere.name
                                isRenaming = true
                            }
                        )
                    }
                }
                PixelIconButton(
                    icon = IconName.EDIT,
                    onClick = {
                        SpheresStore.openSheet(SheetState.SphereEdit(sphere.id))
                    }
                )
            }

            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = "${memberRows.size} ${if (memberRows.size == 1) "connection" else "connections"} in this sphere.",
                    style = TextStyle(
                        fontFamily = SansFontFamily(),
                        fontSize = 16.sp,
                        lineHeight = 24.sp,
                        color = Ink
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PixelButton(
                        text = "Add members",
                        icon = IconName.USERS,
                        variant = if (isAddingMembers) ButtonVariant.PRIMARY else ButtonVariant.SECONDARY,
                        size = ButtonSize.SM,
                        onClick = { isAddingMembers = !isAddingMembers }
                    )
                    PixelButton(
                        text = "New connection",
                        icon = IconName.PLUS,
                        variant = ButtonVariant.SECONDARY,
                        size = ButtonSize.SM,
                        onClick = {
                            SpheresStore.openSheet(SheetState.AddPerson(sphereId = sphere.id))
                        }
                    )
                }

                AnimatedVisibility(
                    visible = isAddingMembers,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .clip(PxClipShape)
                            .background(CardBg)
                            .padding(12.dp)
                    ) {
                        PixelTextInput(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = "Search connections"
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        OptInFlowRow(
                            candidates = candidates,
                            sphere = sphere,
                            events = events
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (memberRows.isEmpty()) {
                    EmptyState(
                        icon = IconName.USERS,
                        title = "No members yet",
                        text = "Add existing connections or create a new one for this sphere."
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (row in memberRows) {
                            val p = row.person
                            val info = intervalInfo(p.intervalDays)
                            val st = statusText(p, row.entries)
                            val score = contactScore(p, row.entries)?.score

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(PxClipShape)
                                    .background(CardBg)
                                    .clickable { onNavigate("/people/${p.id}") }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                PersonAvatar(
                                    person = p,
                                    colorHex = info.color,
                                    size = 40.dp
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = p.name,
                                            style = TextStyle(
                                                fontFamily = PixelFontFamily(),
                                                fontSize = 18.sp,
                                                lineHeight = 24.sp,
                                                color = Ink
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (score != null) {
                                            HealthRing(score = score)
                                        }
                                    }
                                    Text(
                                        text = if (p.intervalDays != null) "${st.first} · ${info.label}" else st.first,
                                        style = TextStyle(
                                            fontFamily = SansFontFamily(),
                                            fontSize = 14.sp,
                                            lineHeight = 20.sp,
                                            color = st.second
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                PixelIcon(name = IconName.CHEVRON_RIGHT, size = 20.dp, tint = Ink.copy(alpha = 0.3f))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "EVENTS",
                        style = TextStyle(
                            fontFamily = PixelFontFamily(),
                            fontSize = 14.sp,
                            letterSpacing = 0.05.sp,
                            color = Muted
                        )
                    )
                    PixelButton(
                        text = "New event",
                        icon = IconName.PLUS,
                        variant = ButtonVariant.SECONDARY,
                        size = ButtonSize.SM,
                        onClick = {
                            val newEventId = SpheresStore.addEvent(sphereIds = listOf(sphere.id))
                            onNavigate("/events/$newEventId")
                        }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (sphereEvents.isEmpty()) {
                    PixelPanel {
                        Text(
                            text = "No events in this sphere yet.",
                            style = TextStyle(
                                fontFamily = SansFontFamily(),
                                fontSize = 15.sp,
                                lineHeight = 24.sp,
                                color = Muted
                            )
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (e in sphereEvents) {
                            val met = people.count { it.eventIds.contains(e.id) }
                            val eventColor = parseHexColor(e.color)
                            PixelPanel(
                                color = eventColor,
                                raised = true,
                                onClick = { onNavigate("/events/${e.id}") }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(PxClipShape)
                                            .background(Color.White),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        PixelIcon(name = IconName.PARTY, size = 26.dp, tint = eventColor)
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = e.name,
                                            style = TextStyle(
                                                fontFamily = PixelFontFamily(),
                                                fontSize = 18.sp,
                                                lineHeight = 24.sp,
                                                color = Ink
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${formatDay(e.date)} · $met met",
                                            style = TextStyle(
                                                fontFamily = SansFontFamily(),
                                                fontSize = 13.sp,
                                                color = Ink.copy(alpha = 0.6f)
                                            )
                                        )
                                    }
                                    PixelIcon(name = IconName.CHEVRON_RIGHT, size = 20.dp, tint = Ink.copy(alpha = 0.4f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OptInFlowRow(
    candidates: List<Person>,
    sphere: Sphere,
    events: List<SocialEvent>
) {
    if (candidates.isEmpty()) {
        Text(
            text = "No connections found.",
            style = TextStyle(
                fontFamily = SansFontFamily(),
                fontSize = 14.sp,
                color = Muted
            )
        )
        return
    }

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val sphereColor = parseHexColor(sphere.color)
        for (p in candidates) {
            val isDirectMember = p.sphereIds.contains(sphere.id)
            val isViaEvent = !isDirectMember && inSphere(p, sphere.id, events)

            Chip(
                label = p.name,
                selected = isDirectMember || isViaEvent,
                color = sphereColor,
                onClick = {
                    if (!isViaEvent) {
                        SpheresStore.setMembership(p.id, sphere.id, !isDirectMember)
                    }
                }
            )
        }
    }
}
