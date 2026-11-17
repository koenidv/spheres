package de.koenidv.spheres.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.koenidv.spheres.data.PaywallReason
import de.koenidv.spheres.data.SheetState
import de.koenidv.spheres.data.SpheresStore
import de.koenidv.spheres.domain.DAY
import de.koenidv.spheres.domain.EventLimitStatus
import de.koenidv.spheres.domain.FREE_CONNECTIONS_LIMIT
import de.koenidv.spheres.domain.Person
import de.koenidv.spheres.domain.SocialEvent
import de.koenidv.spheres.domain.evaluateEventLimitStatus
import de.koenidv.spheres.domain.eventGraceRemainingMs
import de.koenidv.spheres.domain.formatDay
import de.koenidv.spheres.domain.formatGraceRemaining
import de.koenidv.spheres.domain.intervalInfo
import de.koenidv.spheres.domain.parseQuickAdd
import de.koenidv.spheres.domain.wasEventCreatedBeforeLimit
import de.koenidv.spheres.platform.currentTimeMillis
import de.koenidv.spheres.platform.haptic
import de.koenidv.spheres.ui.components.ButtonVariant
import de.koenidv.spheres.ui.components.EmptyState
import de.koenidv.spheres.ui.components.rememberLiveNow
import de.koenidv.spheres.ui.theme.Amber
import de.koenidv.spheres.ui.theme.Coral
import de.koenidv.spheres.ui.theme.FieldBg
import de.koenidv.spheres.ui.components.IconName
import de.koenidv.spheres.ui.components.PersonAvatar
import de.koenidv.spheres.ui.components.PixelButton
import de.koenidv.spheres.ui.components.PixelIcon
import de.koenidv.spheres.ui.components.PixelIconButton
import de.koenidv.spheres.ui.components.PixelPanel
import de.koenidv.spheres.ui.components.PixelTextInput
import de.koenidv.spheres.ui.components.PxClipShape
import de.koenidv.spheres.ui.components.SpherePill
import de.koenidv.spheres.ui.components.autoFocus
import de.koenidv.spheres.ui.theme.DangerText
import de.koenidv.spheres.ui.theme.Ink
import de.koenidv.spheres.ui.theme.Muted
import de.koenidv.spheres.ui.theme.PixelFontFamily
import de.koenidv.spheres.ui.theme.SansFontFamily
import de.koenidv.spheres.ui.theme.parseHexColor
import de.koenidv.spheres.ui.theme.tintColor
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.math.round

private data class RemindSpec(val days: Int, val label: String)

private val REMIND = listOf(
    RemindSpec(1, "1 day"),
    RemindSpec(3, "3 days"),
    RemindSpec(7, "1 week")
)

private fun daysUntil(ts: Long, now: Long = currentTimeMillis()): Int {
    val tz = TimeZone.currentSystemDefault()
    val todayStart = Instant.fromEpochMilliseconds(now).toLocalDateTime(tz).date.atStartOfDayIn(tz).toEpochMilliseconds()
    return round((ts - todayStart - 9 * 3_600_000L).toDouble() / DAY).toInt()
}

@Composable
fun EventScreen(
    id: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by SpheresStore.state.collectAsState()
    val event = state.events.firstOrNull { it.id == id }
    val dataReady = state.dataReady
    val people = state.people
    val spheres = state.spheres

    if (event == null) {
        if (!dataReady) return
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.White)
                .statusBarsPadding()
                .padding(top = 48.dp)
        ) {
            EmptyState(
                icon = IconName.PARTY,
                title = "Event not found",
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

    val isJustCreated = remember(event.id) {
        currentTimeMillis() - event.createdAt < 5000L && event.name.startsWith("Event · ")
    }
    var isRenaming by remember(event.id) { mutableStateOf(isJustCreated) }
    var renameDraft by remember(event.id, event.name) {
        mutableStateOf(if (isJustCreated) "" else event.name)
    }
    var quickAddInput by remember { mutableStateOf("") }

    val eventColor = parseHexColor(event.color)
    val mineSpheres = remember(spheres, event.sphereIds) {
        spheres.filter { event.sphereIds.contains(it.id) }
    }

    val members = remember(people, event.id) {
        people.filter { it.eventIds.contains(event.id) }.sortedByDescending { it.createdAt }
    }

    val parsed = remember(quickAddInput) { parseQuickAdd(quickAddInput) }
    val now = rememberLiveNow(intervalMs = 30_000L)
    val limitStatus = remember(event, people, state.isUnlimited, now) {
        evaluateEventLimitStatus(event, people, state.isUnlimited, now)
    }
    val graceRemainingMs = remember(event, people, now) {
        eventGraceRemainingMs(event, people, now)
    }
    val createdBeforeLimit = remember(event, people) {
        wasEventCreatedBeforeLimit(event, people)
    }
    val paywallReason = if (createdBeforeLimit) {
        PaywallReason.EventGraceEnded(event.name)
    } else {
        PaywallReason.ConnectionsLimit
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PixelIconButton(
                    icon = IconName.BACK,
                    onClick = { onNavigate("..") }
                )
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(eventColor)
                        )
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
                                        if (v.isNotBlank() && v != event.name) {
                                            SpheresStore.updateEvent(event.id, name = v)
                                        }
                                        isRenaming = false
                                    }
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.Transparent)
                                    .autoFocus(delayMs = if (isJustCreated) 150L else 0L),
                                decorationBox = { innerTextField ->
                                    Box {
                                        if (renameDraft.isEmpty()) {
                                            Text(
                                                text = event.name,
                                                style = TextStyle(
                                                    fontFamily = PixelFontFamily(),
                                                    fontSize = 22.sp,
                                                    lineHeight = 28.sp,
                                                    color = Ink.copy(alpha = 0.35f)
                                                )
                                            )
                                        }
                                        innerTextField()
                                    }
                                }
                            )
                        } else {
                            Text(
                                text = event.name,
                                style = TextStyle(
                                    fontFamily = PixelFontFamily(),
                                    fontSize = 22.sp,
                                    lineHeight = 28.sp,
                                    color = Ink
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.clickable {
                                    renameDraft = event.name
                                    isRenaming = true
                                }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.padding(top = 4.dp, start = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = formatDay(event.date),
                            style = TextStyle(
                                fontFamily = SansFontFamily(),
                                fontSize = 13.sp,
                                color = Muted
                            )
                        )
                        for (s in mineSpheres) {
                            val c = parseHexColor(s.color)
                            SpherePill(label = s.name, color = tintColor(c, 60))
                        }
                    }
                }

                PixelIconButton(
                    icon = IconName.EDIT,
                    onClick = {
                        SpheresStore.openSheet(SheetState.EventEdit(event.id))
                    }
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                val isHardLimit = limitStatus == EventLimitStatus.HARD_LIMIT
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(PxClipShape)
                        .background(FieldBg)
                        .then(
                            if (isHardLimit) {
                                Modifier.clickable {
                                    SpheresStore.openSheet(SheetState.Paywall(reason = paywallReason))
                                }
                            } else Modifier
                        )
                        .padding(start = 16.dp, end = 8.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    BasicTextField(
                        value = quickAddInput,
                        onValueChange = { quickAddInput = it },
                        enabled = !isHardLimit,
                        textStyle = TextStyle(
                            fontFamily = SansFontFamily(),
                            fontSize = 18.sp,
                            color = Ink
                        ),
                        cursorBrush = SolidColor(Ink),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (isHardLimit) {
                                    SpheresStore.openSheet(SheetState.Paywall(reason = paywallReason))
                                } else if (quickAddInput.isNotBlank()) {
                                    val added = SpheresStore.quickAddToEvent(event.id, quickAddInput)
                                    if (added != null) {
                                        haptic()
                                        quickAddInput = ""
                                    }
                                }
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 48.dp),
                        decorationBox = { innerTextField ->
                            if (quickAddInput.isEmpty()) {
                                Text(
                                    text = if (isHardLimit) {
                                        "Unlock Unlimited to add more people…"
                                    } else {
                                        "Name, note… e.g. Anna, works at Spotify"
                                    },
                                    style = TextStyle(
                                        fontFamily = SansFontFamily(),
                                        fontSize = 18.sp,
                                        color = Ink.copy(alpha = 0.35f)
                                    )
                                )
                            }
                            innerTextField()
                        }
                    )

                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .align(Alignment.CenterEnd)
                            .clip(PxClipShape)
                            .background(
                                when {
                                    isHardLimit -> Coral
                                    parsed.name.isNotBlank() -> Ink
                                    else -> Ink.copy(alpha = 0.25f)
                                }
                            )
                            .clickable(enabled = isHardLimit || parsed.name.isNotBlank()) {
                                if (isHardLimit) {
                                    SpheresStore.openSheet(SheetState.Paywall(reason = paywallReason))
                                } else {
                                    val added = SpheresStore.quickAddToEvent(event.id, quickAddInput)
                                    if (added != null) {
                                        haptic()
                                        quickAddInput = ""
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        PixelIcon(
                            name = if (isHardLimit) IconName.LOCK else IconName.PLUS,
                            size = 20.dp,
                            tint = Color.White
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (parsed.name.isNotBlank() && !isHardLimit) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = parsed.name,
                                style = TextStyle(
                                    fontFamily = PixelFontFamily(),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    color = Ink
                                )
                            )
                            if (parsed.note.isNotBlank()) {
                                Text(
                                    text = " · ${parsed.note}",
                                    style = TextStyle(
                                        fontFamily = SansFontFamily(),
                                        fontSize = 14.sp,
                                        color = Muted
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    } else {
                        Text(
                            text = if (isHardLimit) {
                                "Free limit of $FREE_CONNECTIONS_LIMIT connections reached."
                            } else {
                                "Press enter to add. Separate a note with a comma."
                            },
                            style = TextStyle(
                                fontFamily = SansFontFamily(),
                                fontSize = 14.sp,
                                color = Muted
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Text(
                        text = "${members.size} ${if (members.size == 1) "person" else "people"} met",
                        style = TextStyle(
                            fontFamily = SansFontFamily(),
                            fontSize = 14.sp,
                            color = Muted
                        )
                    )
                }

                AnimatedVisibility(
                    visible = limitStatus != EventLimitStatus.OK,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    if (limitStatus == EventLimitStatus.GRACE_WARNING) {
                        val remainingLabel = formatGraceRemaining(graceRemainingMs)
                        PixelPanel(
                            color = Amber,
                            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                            onClick = {
                                SpheresStore.openSheet(SheetState.Paywall(reason = PaywallReason.ConnectionsLimit))
                            }
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    PixelIcon(name = IconName.CLOCK, size = 16.dp, tint = Ink)
                                    Text(
                                        text = "EVENT FLOW PROTECTED · $remainingLabel",
                                        style = TextStyle(
                                            fontFamily = PixelFontFamily(),
                                            fontSize = 13.sp,
                                            color = Ink
                                        ),
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(PxClipShape)
                                            .background(Ink)
                                            .padding(horizontal = 8.dp, vertical = 3.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Unlock · ${state.unlimitedPrice}",
                                            style = TextStyle(
                                                fontFamily = PixelFontFamily(),
                                                fontSize = 12.sp,
                                                color = Color.White
                                            )
                                        )
                                    }
                                }
                                Text(
                                    text = "You've passed $FREE_CONNECTIONS_LIMIT connections — keep adding people freely for ${remainingLabel.removeSuffix(" left")} without interrupting your event.",
                                    style = TextStyle(
                                        fontFamily = SansFontFamily(),
                                        fontSize = 13.sp,
                                        lineHeight = 18.sp,
                                        color = Ink.copy(alpha = 0.78f)
                                    )
                                )
                            }
                        }
                    } else if (limitStatus == EventLimitStatus.HARD_LIMIT) {
                        PixelPanel(
                            color = Coral,
                            raised = true,
                            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                            onClick = {
                                SpheresStore.openSheet(SheetState.Paywall(reason = paywallReason))
                            }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(PxClipShape)
                                        .background(Color.White),
                                    contentAlignment = Alignment.Center
                                ) {
                                    PixelIcon(name = IconName.LOCK, size = 20.dp, tint = Coral)
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (createdBeforeLimit) {
                                            "24-hour event flow ended"
                                        } else {
                                            "Free limit reached (${people.size}/$FREE_CONNECTIONS_LIMIT)"
                                        },
                                        style = TextStyle(
                                            fontFamily = PixelFontFamily(),
                                            fontSize = 16.sp,
                                            color = Ink
                                        )
                                    )
                                    Text(
                                        text = "Unlock Unlimited once (${state.unlimitedPrice}) to keep adding everyone you meet.",
                                        style = TextStyle(
                                            fontFamily = SansFontFamily(),
                                            fontSize = 13.sp,
                                            lineHeight = 18.sp,
                                            color = Ink.copy(alpha = 0.72f)
                                        )
                                    )
                                }
                                Row(
                                    modifier = Modifier
                                        .clip(PxClipShape)
                                        .background(Ink)
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    PixelIcon(name = IconName.SPARKLE, size = 14.dp, tint = Color.White)
                                    Text(
                                        text = "Unlock",
                                        style = TextStyle(
                                            fontFamily = PixelFontFamily(),
                                            fontSize = 13.sp,
                                            color = Color.White
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (members.isEmpty()) {
                EmptyState(
                    icon = IconName.PARTY,
                    title = "Nobody yet",
                    text = "Type a name above and hit enter. You can sort out who to keep in touch with later."
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    for (person in members) {
                        EventRow(
                            person = person,
                            eventColor = eventColor,
                            onOpen = { onNavigate("/people/${person.id}") }
                        )
                    }
                    Spacer(modifier = Modifier.navigationBarsPadding().height(96.dp))
                }
            }
        }
    }
}

@Composable
private fun EventRow(
    person: Person,
    eventColor: Color,
    onOpen: () -> Unit
) {
    var isEditing by remember { mutableStateOf(false) }
    var nameInput by remember(person.name) { mutableStateOf(person.name) }
    var noteInput by remember(person.note) { mutableStateOf(person.note) }

    val info = intervalInfo(person.intervalDays)

    val commit = {
        val n = nameInput.trim()
        if (n.isNotBlank() && n != person.name) {
            SpheresStore.updatePerson(person.id) { it.copy(name = n) }
        }
        if (noteInput != person.note) {
            SpheresStore.updatePerson(person.id) { it.copy(note = noteInput) }
        }
    }

    PixelPanel(color = eventColor) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onOpen),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PersonAvatar(
                        person = person,
                        colorHex = info.color,
                        size = 36.dp
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = person.name,
                            style = TextStyle(
                                fontFamily = PixelFontFamily(),
                                fontSize = 17.sp,
                                lineHeight = 24.sp,
                                color = Ink
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (person.note.isNotBlank()) {
                            Text(
                                text = person.note,
                                style = TextStyle(
                                    fontFamily = SansFontFamily(),
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp,
                                    color = Muted
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                PixelIconButton(
                    icon = IconName.EDIT,
                    size = 36.dp,
                    onClick = {
                        if (isEditing) commit()
                        isEditing = !isEditing
                    }
                )

                PixelIconButton(
                    icon = IconName.TRASH,
                    size = 36.dp,
                    tint = DangerText,
                    onClick = {
                        val snapshot = person
                        SpheresStore.deletePerson(person.id)
                        SpheresStore.showToast("Removed ${person.name}") {
                            SpheresStore.addPerson(
                                name = snapshot.name,
                                intervalDays = snapshot.intervalDays,
                                sphereIds = snapshot.sphereIds,
                                city = snapshot.city,
                                eventIds = snapshot.eventIds,
                                note = snapshot.note,
                                birthday = snapshot.birthday
                            )
                        }
                    }
                )
            }

            AnimatedVisibility(
                visible = isEditing,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PixelTextInput(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        placeholder = "Name"
                    )
                    PixelTextInput(
                        value = noteInput,
                        onValueChange = { noteInput = it },
                        placeholder = "Note"
                    )
                }
            }

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp)
                    .height(1.dp)
            ) {
                drawLine(
                    color = eventColor,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 8f), 0f)
                )
            }

            RemindControl(person = person, accent = eventColor)
        }
    }
}

@Composable
private fun RemindControl(person: Person, accent: Color) {
    val at = if (person.followUpAt != null && person.followUpAt!! > currentTimeMillis() - DAY) {
        person.followUpAt
    } else null
    val days = at?.let { daysUntil(it) }
    val label = when {
        at == null -> "Remind me"
        days != null && days <= 0 -> "Today"
        days == 1 -> "Tomorrow"
        else -> formatDay(at)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            PixelIcon(
                name = IconName.BELL,
                size = 16.dp,
                tint = if (at != null) accent else Muted
            )
            Text(
                text = label,
                style = TextStyle(
                    fontFamily = PixelFontFamily(),
                    fontSize = 14.sp,
                    color = if (at != null) Ink else Muted
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(
            modifier = Modifier
                .clip(PxClipShape)
                .background(accent)
                .padding(1.dp),
            horizontalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            for (r in REMIND) {
                val on = days == r.days
                Box(
                    modifier = Modifier
                        .height(30.dp)
                        .clip(PxClipShape)
                        .background(if (on) accent else tintColor(accent, 14))
                        .clickable {
                            SpheresStore.setFollowUp(person.id, if (on) null else r.days)
                        }
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = r.label,
                        style = TextStyle(
                            fontFamily = PixelFontFamily(),
                            fontSize = 13.sp,
                            color = Ink
                        )
                    )
                }
            }
        }
    }
}
