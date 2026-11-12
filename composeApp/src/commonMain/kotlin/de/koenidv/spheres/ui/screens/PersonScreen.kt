package de.koenidv.spheres.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.koenidv.spheres.data.LogMode
import de.koenidv.spheres.data.SheetState
import de.koenidv.spheres.data.SpheresStore
import de.koenidv.spheres.domain.Birthday
import de.koenidv.spheres.domain.DAY
import de.koenidv.spheres.domain.DueStatus
import de.koenidv.spheres.domain.Entry
import de.koenidv.spheres.domain.Fact
import de.koenidv.spheres.domain.Gift
import de.koenidv.spheres.domain.INTERVALS
import de.koenidv.spheres.domain.Person
import de.koenidv.spheres.domain.contactScore
import de.koenidv.spheres.platform.currentTimeMillis
import de.koenidv.spheres.domain.dueInfo
import de.koenidv.spheres.domain.firstName
import de.koenidv.spheres.domain.formatBirthday
import de.koenidv.spheres.domain.formatDay
import de.koenidv.spheres.domain.intervalInfo
import de.koenidv.spheres.domain.nextBirthday
import de.koenidv.spheres.domain.recentBirthday
import de.koenidv.spheres.domain.relationshipQuality
import de.koenidv.spheres.domain.relativeDays
import de.koenidv.spheres.domain.sphereIdsViaEvents
import de.koenidv.spheres.platform.openExternalUrl
import de.koenidv.spheres.platform.randomUuid
import de.koenidv.spheres.ui.components.ButtonSize
import de.koenidv.spheres.ui.components.ButtonVariant
import de.koenidv.spheres.ui.components.Card
import de.koenidv.spheres.ui.components.CardLabel
import de.koenidv.spheres.ui.components.Chip
import de.koenidv.spheres.ui.components.EmptyState
import de.koenidv.spheres.ui.theme.FieldBg
import de.koenidv.spheres.ui.components.IconName
import de.koenidv.spheres.ui.theme.Line
import de.koenidv.spheres.ui.components.PixelAvatar
import de.koenidv.spheres.ui.components.PixelButton
import de.koenidv.spheres.ui.components.PixelIcon
import de.koenidv.spheres.ui.components.PixelIconButton
import de.koenidv.spheres.ui.components.PixelTextArea
import de.koenidv.spheres.ui.components.PixelTextInput
import de.koenidv.spheres.ui.components.PxClipShape
import de.koenidv.spheres.ui.components.SpherePill
import kotlin.math.abs
import de.koenidv.spheres.ui.theme.Coral
import de.koenidv.spheres.ui.theme.DangerText
import de.koenidv.spheres.ui.theme.Ink
import de.koenidv.spheres.ui.theme.Muted
import de.koenidv.spheres.ui.theme.PixelFontFamily
import de.koenidv.spheres.ui.theme.SansFontFamily
import de.koenidv.spheres.ui.theme.parseHexColor
import de.koenidv.spheres.ui.theme.tintColor
import kotlinx.coroutines.delay
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.round
import kotlin.math.sqrt

private const val STEP = 4f
private fun snap(n: Float): Float = round(n / STEP) * STEP

private val MONTHS = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December"
)

private val SUGGESTED_FACTS = listOf("Partner", "Kids", "Work", "How we met")

private val KIND_ICON = mapOf(
    de.koenidv.spheres.domain.EntryKind.CHECKIN to IconName.CHECK,
    de.koenidv.spheres.domain.EntryKind.MEET to IconName.COFFEE,
    de.koenidv.spheres.domain.EntryKind.CALL to IconName.PHONE,
    de.koenidv.spheres.domain.EntryKind.MESSAGE to IconName.MESSAGE,
    de.koenidv.spheres.domain.EntryKind.OTHER to IconName.STAR,
    de.koenidv.spheres.domain.EntryKind.NOTE to IconName.NOTE
)

private val KIND_LABEL = mapOf(
    de.koenidv.spheres.domain.EntryKind.CHECKIN to "Check-in",
    de.koenidv.spheres.domain.EntryKind.MEET to "Met",
    de.koenidv.spheres.domain.EntryKind.CALL to "Call",
    de.koenidv.spheres.domain.EntryKind.MESSAGE to "Message",
    de.koenidv.spheres.domain.EntryKind.OTHER to "Interaction",
    de.koenidv.spheres.domain.EntryKind.NOTE to "Note"
)

private val REMIND = listOf(
    Pair(1, "1 day"),
    Pair(3, "3 days"),
    Pair(7, "1 week")
)

private fun followUpIn(ts: Long, now: Long = currentTimeMillis()): String {
    val tz = TimeZone.currentSystemDefault()
    val todayStart = Instant.fromEpochMilliseconds(now).toLocalDateTime(tz).date.atStartOfDayIn(tz).toEpochMilliseconds()
    val n = floor((ts - todayStart).toDouble() / DAY).toInt()
    return when {
        n <= 0 -> "today"
        n == 1 -> "in 1 day"
        else -> "in $n days"
    }
}

@Composable
fun PersonScreen(
    id: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by SpheresStore.state.collectAsState()
    val person = state.people.firstOrNull { it.id == id }
    val dataReady = state.dataReady
    val spheres = state.spheres
    val events = state.events
    val allEntries = state.entries

    if (person == null) {
        if (!dataReady) return
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.White)
                .statusBarsPadding()
                .padding(top = 48.dp)
        ) {
            EmptyState(
                icon = IconName.USER,
                title = "Connection not found",
                text = "They may have been deleted.",
                action = {
                    PixelButton(
                        text = "Back to radar",
                        variant = ButtonVariant.PRIMARY,
                        onClick = { onNavigate("/") }
                    )
                }
            )
        }
        return
    }

    var isRenaming by remember { mutableStateOf(false) }
    var renameDraft by remember(person.name) { mutableStateOf(person.name) }
    var editInterval by remember { mutableStateOf(false) }
    var pickSpheres by remember { mutableStateOf(false) }
    var deleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(deleteConfirm) {
        if (deleteConfirm) {
            delay(3000)
            deleteConfirm = false
        }
    }

    val entries = remember(allEntries, person.id) {
        allEntries.filter { it.personId == person.id }.sortedByDescending { it.at }
    }

    val info = intervalInfo(person.intervalDays)
    val accentColor = parseHexColor(info.color)

    val due = remember(person, entries) { dueInfo(person, entries) }
    val score = remember(person, entries) { contactScore(person, entries) }
    val rq = remember(entries) { relationshipQuality(entries) }
    val viaEvents = remember(person, events) { sphereIdsViaEvents(person, events) }
    val mineSpheres = remember(spheres, person.sphereIds, viaEvents) {
        spheres.filter { person.sphereIds.contains(it.id) || viaEvents.contains(it.id) }
    }
    val metAtEvents = remember(events, person.eventIds) {
        events.filter { person.eventIds.contains(it.id) }
    }

    val tracked = person.intervalDays != null
    val overdue = due?.status == DueStatus.OVERDUE
    val barColor = if (overdue) Coral else accentColor

    val dueText = if (due != null) {
        val n = due.daysLeft
        if (overdue) {
            if (n >= 0) "due today" else "${-n} ${if (-n == 1) "day" else "days"} overdue"
        } else {
            if (n == 1) "due tomorrow" else "due in $n days"
        }
    } else ""

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 96.dp)
        ) {
            ArcHeader(accent = accentColor) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    PixelIconButton(
                        icon = IconName.BACK,
                        onClick = { onNavigate("..") }
                    )
                    PixelIconButton(
                        icon = IconName.EDIT,
                        onClick = {
                            renameDraft = person.name
                            isRenaming = true
                        }
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .offset(y = (-48).dp)
                        .background(Color.White)
                        .border(2.dp, accentColor)
                        .padding(4.dp)
                ) {
                    PixelAvatar(
                        name = person.name,
                        colorHex = info.color,
                        size = 96.dp
                    )
                }

                if (isRenaming) {
                    BasicTextField(
                        value = renameDraft,
                        onValueChange = { renameDraft = it },
                        textStyle = TextStyle(
                            fontFamily = PixelFontFamily(),
                            fontSize = 28.sp,
                            lineHeight = 36.sp,
                            color = Ink,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        ),
                        cursorBrush = SolidColor(Ink),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                val v = renameDraft.trim()
                                if (v.isNotBlank() && v != person.name) {
                                    SpheresStore.updatePerson(person.id) { it.copy(name = v) }
                                }
                                isRenaming = false
                            }
                        ),
                        modifier = Modifier
                            .offset(y = (-36).dp)
                            .fillMaxWidth(0.8f)
                            .background(Color.Transparent)
                    )
                } else {
                    Text(
                        text = person.name,
                        style = TextStyle(
                            fontFamily = PixelFontFamily(),
                            fontSize = 28.sp,
                            lineHeight = 36.sp,
                            color = Ink
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .offset(y = (-36).dp)
                            .clickable {
                                renameDraft = person.name
                                isRenaming = true
                            }
                    )
                }

                Row(
                    modifier = Modifier
                        .offset(y = (-28).dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CityLine(person = person)
                    SpherePill(
                        label = info.label,
                        color = accentColor,
                        onClick = if (tracked) { { editInterval = !editInterval } } else null
                    )
                }

                AnimatedVisibility(
                    visible = editInterval && tracked,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                    modifier = Modifier.offset(y = (-20).dp)
                ) {
                    OptInIntervalChips(
                        selectedDays = person.intervalDays,
                        onChange = { d ->
                            if (d == null) {
                                SpheresStore.updatePerson(person.id) { it.copy(intervalDays = null) }
                            } else {
                                SpheresStore.keepInTouch(person.id, d)
                            }
                            editInterval = false
                        }
                    )
                }

                Row(
                    modifier = Modifier
                        .offset(y = (-16).dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (s in mineSpheres) {
                        val c = parseHexColor(s.color)
                        SpherePill(
                            label = s.name,
                            color = c,
                            onClick = { onNavigate("/spheres/${s.id}") }
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(width = 32.dp, height = 24.dp)
                            .clip(PxClipShape)
                            .background(if (pickSpheres) Ink else FieldBg)
                            .clickable { pickSpheres = !pickSpheres },
                        contentAlignment = Alignment.Center
                    ) {
                        PixelIcon(
                            name = if (pickSpheres) IconName.CLOSE else IconName.PLUS,
                            size = 14.dp,
                            tint = if (pickSpheres) Color.White else Ink.copy(alpha = 0.6f)
                        )
                    }
                }

                AnimatedVisibility(
                    visible = pickSpheres,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                    modifier = Modifier.offset(y = (-10).dp)
                ) {
                    OptInSpheresRow(
                        spheres = spheres,
                        person = person,
                        viaEvents = viaEvents
                    )
                }

                for (e in metAtEvents) {
                    Row(
                        modifier = Modifier.offset(y = (-8).dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Met at ",
                            style = TextStyle(fontFamily = SansFontFamily(), fontSize = 14.sp, color = Muted)
                        )
                        Text(
                            text = "${e.name} · ${formatDay(e.date)}",
                            style = TextStyle(
                                fontFamily = PixelFontFamily(),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Ink,
                                textDecoration = TextDecoration.Underline
                            ),
                            modifier = Modifier.clickable { onNavigate("/events/${e.id}") }
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column {
                    val lastInteractionTs = due?.last ?: entries.firstOrNull { !it.kind.isNote }?.at
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (lastInteractionTs != null) {
                                "Last interaction ${relativeDays(lastInteractionTs)}"
                            } else {
                                "No interaction yet"
                            },
                            style = TextStyle(
                                fontFamily = SansFontFamily(),
                                fontSize = 16.sp,
                                lineHeight = 24.sp,
                                color = Ink
                            )
                        )
                        if (due != null) {
                            Text(
                                text = " · $dueText",
                                style = TextStyle(
                                    fontFamily = SansFontFamily(),
                                    fontSize = 16.sp,
                                    lineHeight = 24.sp,
                                    color = if (overdue) DangerText else Ink
                                )
                            )
                        }
                    }

                    if (score != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        ScoreBar(score = score.score, color = barColor)
                    }

                    if (rq != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Recent catch-ups: ",
                                style = TextStyle(
                                    fontFamily = PixelFontFamily(),
                                    fontSize = 14.sp,
                                    color = Muted
                                )
                            )
                            Text(
                                text = "${rq.quality.label} ${rq.quality.symbol}",
                                style = TextStyle(
                                    fontFamily = PixelFontFamily(),
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 14.sp,
                                    color = Ink
                                )
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PixelButton(
                        text = "Log interaction",
                        icon = IconName.CHAT,
                        variant = ButtonVariant.PRIMARY,
                        size = ButtonSize.LG,
                        fullWidth = true,
                        onClick = {
                            SpheresStore.openSheet(SheetState.Log(person.id, LogMode.INTERACTION))
                        }
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PixelButton(
                            text = "Note",
                            icon = IconName.NOTE,
                            variant = ButtonVariant.SECONDARY,
                            size = ButtonSize.MD,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                SpheresStore.openSheet(SheetState.Log(person.id, LogMode.NOTE))
                            }
                        )
                        if (due != null && due.status != DueStatus.OK) {
                            PixelButton(
                                text = "Snooze 3 days",
                                icon = IconName.SNOOZE,
                                variant = ButtonVariant.SECONDARY,
                                size = ButtonSize.MD,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    SpheresStore.snooze(person.id, 3)
                                }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (person.followUpAt != null) {
                        Text(
                            text = "Follow-up reminder ${followUpIn(person.followUpAt!!)}",
                            style = TextStyle(
                                fontFamily = PixelFontFamily(),
                                fontSize = 15.sp,
                                color = Ink
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        PixelIconButton(
                            icon = IconName.CLOSE,
                            size = 32.dp,
                            onClick = { SpheresStore.setFollowUp(person.id, null) }
                        )
                    } else {
                        Text(
                            text = "REMIND ME",
                            style = TextStyle(
                                fontFamily = PixelFontFamily(),
                                fontSize = 13.sp,
                                letterSpacing = 0.05.sp,
                                color = Muted
                            )
                        )
                        for ((days, label) in REMIND) {
                            Chip(
                                label = label,
                                onClick = { SpheresStore.setFollowUp(person.id, days) }
                            )
                        }
                    }
                }

                if (!tracked) {
                    val event = metAtEvents.firstOrNull()
                    val cardColor = parseHexColor(event?.color ?: "#9d7ce7")
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(PxClipShape)
                            .background(tintColor(cardColor, 22))
                            .border(1.dp, cardColor, PxClipShape)
                            .padding(16.dp)
                    ) {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                PixelIcon(name = IconName.RADAR, size = 22.dp, tint = Ink)
                                Text(
                                    text = "Keep in touch",
                                    style = TextStyle(
                                        fontFamily = PixelFontFamily(),
                                        fontSize = 20.sp,
                                        color = Ink
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "${if (event != null) "You met ${firstName(person.name)} at ${event.name}. " else ""}How often would you like to reach out? ${firstName(person.name)} will appear on your radar.",
                                style = TextStyle(
                                    fontFamily = SansFontFamily(),
                                    fontSize = 15.sp,
                                    lineHeight = 22.sp,
                                    color = Ink.copy(alpha = 0.7f)
                                )
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            OptInIntervalChips(
                                selectedDays = null,
                                onChange = { d ->
                                    if (d != null) SpheresStore.keepInTouch(person.id, d)
                                }
                            )
                        }
                    }
                }

                BirthdayCard(person = person)
                GiftsCard(person = person)
                NotesCard(person = person)
                DetailsCard(person = person)
                TimelineCard(entries = entries)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                ) {
                    PixelButton(
                        text = if (deleteConfirm) "Tap again to delete" else "Delete connection",
                        icon = IconName.TRASH,
                        variant = ButtonVariant.DANGER,
                        size = ButtonSize.LG,
                        fullWidth = true,
                        onClick = {
                            if (!deleteConfirm) {
                                deleteConfirm = true
                            } else {
                                SpheresStore.deletePerson(person.id)
                                onNavigate("/")
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ArcHeader(accent: Color, content: @Composable () -> Unit) {
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val widthDp = maxWidth.value
        val arcH = remember(widthDp) {
            if (widthDp <= 0f) 32f
            else {
                val cx = floor(widthDp / 2f)
                val radius = 1.7f * widthDp
                val b = floor(-cx / STEP)
                val dx = abs(b * STEP + STEP / 2f)
                val dy = radius - sqrt(max(0f, radius * radius - dx * dx))
                snap(dy) + STEP
            }
        }

        val arcPaths = remember(widthDp, arcH, density) {
            if (widthDp <= 0f) null
            else {
                val cx = floor(widthDp / 2f)
                val radius = 1.7f * widthDp
                fun off(x: Float): Float {
                    val b = floor((x - cx) / STEP)
                    val dx = abs(b * STEP + STEP / 2f)
                    val dy = radius - sqrt(max(0f, radius * radius - dx * dx))
                    return snap(dy)
                }
                val strokePath = Path()
                val fillPath = Path()
                var lastY = -1f
                var b = floor(-cx / STEP).toInt()
                val maxB = kotlin.math.ceil((widthDp - cx) / STEP).toInt()
                while (b < maxB) {
                    val x0 = maxOf(0f, cx + b * STEP) * density
                    val x1 = minOf(widthDp, cx + (b + 1) * STEP) * density
                    val y = (off(cx + b * STEP + 1f) + 1f) * density
                    if (lastY < 0f) {
                        strokePath.moveTo(x0, y)
                        fillPath.moveTo(x0, y)
                    } else if (y != lastY) {
                        strokePath.lineTo(x0, y)
                        fillPath.lineTo(x0, y)
                    }
                    strokePath.lineTo(x1, y)
                    fillPath.lineTo(x1, y)
                    lastY = y
                    b++
                }
                fillPath.lineTo(widthDp * density, arcH * density)
                fillPath.lineTo(0f, arcH * density)
                fillPath.close()
                Pair(strokePath, fillPath)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(tintColor(accent, 28))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                content()
            }
            Spacer(modifier = Modifier.height(28.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(arcH.dp)
            ) {
                if (arcPaths != null) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawPath(arcPaths.second, Color.White)
                        drawPath(arcPaths.first, accent, style = Stroke(1.dp.toPx()))
                    }
                }
            }
        }
    }
}

@Composable
private fun CityLine(person: Person) {
    var isEditing by remember { mutableStateOf(false) }
    var cityDraft by remember(person.city) { mutableStateOf(person.city ?: "") }

    if (isEditing) {
        BasicTextField(
            value = cityDraft,
            onValueChange = { cityDraft = it },
            textStyle = TextStyle(
                fontFamily = SansFontFamily(),
                fontSize = 16.sp,
                lineHeight = 24.sp,
                color = Ink,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            ),
            cursorBrush = SolidColor(Ink),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = {
                    val v = cityDraft.trim().ifEmpty { null }
                    if (v != person.city) {
                        SpheresStore.updatePerson(person.id) { it.copy(city = v) }
                    }
                    isEditing = false
                }
            ),
            modifier = Modifier
                .width(140.dp)
                .background(Color.Transparent)
        )
    } else {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.clickable {
                cityDraft = person.city ?: ""
                isEditing = true
            }
        ) {
            PixelIcon(name = IconName.PIN, size = 16.dp, tint = if (person.city != null) Ink else Muted)
            Text(
                text = person.city ?: "Add city",
                style = TextStyle(
                    fontFamily = SansFontFamily(),
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    color = if (person.city != null) Ink.copy(alpha = 0.75f) else Ink.copy(alpha = 0.4f)
                )
            )
        }
    }
}

@Composable
private fun ScoreBar(score: Int, color: Color) {
    val filled = round(score / 10f).toInt().coerceIn(0, 10)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        for (i in 0 until 10) {
            val isFilled = i < filled
            val barAlpha = remember { Animatable(0.4f) }
            LaunchedEffect(Unit) {
                delay((i * 30).toLong())
                barAlpha.animateTo(1f, tween(150))
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(12.dp)
                    .background(if (isFilled) color.copy(alpha = barAlpha.value) else Color(0xFFE8E8E8))
            )
        }
    }
}

@Composable
private fun OptInIntervalChips(selectedDays: Int?, onChange: (Int?) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (i in INTERVALS) {
            val c = parseHexColor(i.color)
            Chip(
                label = i.label,
                selected = selectedDays == i.days,
                color = c,
                onClick = { onChange(i.days) }
            )
        }
        Chip(
            label = "Not tracked",
            selected = selectedDays == null,
            onClick = { onChange(null) }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OptInSpheresRow(
    spheres: List<de.koenidv.spheres.domain.Sphere>,
    person: Person,
    viaEvents: List<String>
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (s in spheres) {
            val isDirect = person.sphereIds.contains(s.id)
            val isVia = viaEvents.contains(s.id)
            val c = parseHexColor(s.color)
            Chip(
                label = s.name,
                selected = isDirect || isVia,
                color = c,
                onClick = {
                    if (!isVia) {
                        SpheresStore.setMembership(person.id, s.id, !isDirect)
                    }
                }
            )
        }
    }
}

@Composable
private fun BirthdayCard(person: Person) {
    val b = person.birthday
    var isEditing by remember { mutableStateOf(false) }
    var day by remember(b) { mutableStateOf(b?.day ?: 1) }
    var month by remember(b) { mutableStateOf(b?.month ?: 1) }
    var yearStr by remember(b) { mutableStateOf(b?.year?.toString() ?: "") }

    val nb = b?.let { nextBirthday(it) }
    val rb = b?.let { recentBirthday(it) }

    val amberColor = Color(0xFFF0A83C)

    Card(color = amberColor) {
        Column(modifier = Modifier.fillMaxWidth()) {
            CardLabel(
                title = "Birthday",
                icon = IconName.CAKE,
                color = amberColor,
                right = if (b != null && !isEditing) {
                    {
                        Box(modifier = Modifier.clickable { isEditing = true }) {
                            PixelIcon(name = IconName.EDIT, size = 18.dp, tint = Ink.copy(alpha = 0.5f))
                        }
                    }
                } else null
            )

            if (isEditing) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PixelTextInput(
                            value = day.toString(),
                            onValueChange = { day = it.filter { ch -> ch.isDigit() }.toIntOrNull()?.coerceIn(1, 31) ?: 1 },
                            placeholder = "Day",
                            modifier = Modifier.width(72.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        PixelTextInput(
                            value = month.toString(),
                            onValueChange = { month = it.filter { ch -> ch.isDigit() }.toIntOrNull()?.coerceIn(1, 12) ?: 1 },
                            placeholder = "Month",
                            modifier = Modifier.width(84.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        PixelTextInput(
                            value = yearStr,
                            onValueChange = { yearStr = it.filter { ch -> ch.isDigit() }.take(4) },
                            placeholder = "Year (opt)",
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PixelButton(
                            text = "Save",
                            variant = ButtonVariant.PRIMARY,
                            size = ButtonSize.SM,
                            onClick = {
                                val y = yearStr.toIntOrNull()
                                SpheresStore.updatePerson(person.id) {
                                    it.copy(birthday = Birthday(month = month, day = day, year = y))
                                }
                                isEditing = false
                            }
                        )
                        PixelButton(
                            text = "Cancel",
                            variant = ButtonVariant.SECONDARY,
                            size = ButtonSize.SM,
                            onClick = { isEditing = false }
                        )
                        if (b != null) {
                            Spacer(modifier = Modifier.weight(1f))
                            PixelButton(
                                text = "Clear",
                                variant = ButtonVariant.DANGER,
                                size = ButtonSize.SM,
                                onClick = {
                                    SpheresStore.updatePerson(person.id) { it.copy(birthday = null) }
                                    isEditing = false
                                }
                            )
                        }
                    }
                }
            } else if (b != null && nb != null) {
                val whenText = if (nb.inDays == 0) "today" else if (nb.inDays == 1) "tomorrow" else "in ${nb.inDays} days"
                val bdayText = formatBirthday(b.copy(year = null))
                val sub = when {
                    rb != null -> " · ${if (rb.turned != null) "turned ${rb.turned} " else "was "}${if (rb.daysAgo == 1) "yesterday" else "${rb.daysAgo} days ago"}"
                    nb.inDays <= 31 -> " · ${if (nb.turns != null) "turns ${nb.turns} " else ""}$whenText"
                    nb.turns != null -> " · ${nb.turns - 1} years old"
                    else -> ""
                }
                Text(
                    text = "$bdayText$sub",
                    style = TextStyle(
                        fontFamily = SansFontFamily(),
                        fontSize = 16.sp,
                        lineHeight = 24.sp,
                        color = Ink
                    )
                )
            } else {
                PixelButton(
                    text = "Add birthday",
                    icon = IconName.PLUS,
                    variant = ButtonVariant.SECONDARY,
                    size = ButtonSize.SM,
                    onClick = { isEditing = true }
                )
            }
        }
    }
}

@Composable
private fun GiftsCard(person: Person) {
    var giftText by remember { mutableStateOf("") }
    val pinkColor = Color(0xFFEB78B0)

    Card(color = pinkColor) {
        Column(modifier = Modifier.fillMaxWidth()) {
            CardLabel(
                title = "Gift ideas",
                icon = IconName.GIFT,
                color = pinkColor
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (g in person.gifts) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(if (g.done) Ink else Color.White)
                                .border(2.dp, Ink)
                                .clickable {
                                    val updated = person.gifts.map { if (it.id == g.id) it.copy(done = !it.done) else it }
                                    SpheresStore.updatePerson(person.id) { it.copy(gifts = updated) }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (g.done) {
                                PixelIcon(name = IconName.CHECK, size = 16.dp, tint = Color.White)
                            }
                        }
                        Text(
                            text = g.text,
                            style = TextStyle(
                                fontFamily = SansFontFamily(),
                                fontSize = 16.sp,
                                lineHeight = 24.sp,
                                color = if (g.done) Ink.copy(alpha = 0.4f) else Ink,
                                textDecoration = if (g.done) TextDecoration.LineThrough else null
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        Box(
                            modifier = Modifier.clickable {
                                val updated = person.gifts.filter { it.id != g.id }
                                SpheresStore.updatePerson(person.id) { it.copy(gifts = updated) }
                            }
                        ) {
                            PixelIcon(name = IconName.CLOSE, size = 16.dp, tint = Ink.copy(alpha = 0.35f))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PixelTextInput(
                    value = giftText,
                    onValueChange = { giftText = it },
                    placeholder = "Add an idea",
                    modifier = Modifier.weight(1f)
                )
                PixelButton(
                    icon = IconName.PLUS,
                    variant = ButtonVariant.PRIMARY,
                    size = ButtonSize.MD,
                    enabled = giftText.isNotBlank(),
                    onClick = {
                        val t = giftText.trim()
                        if (t.isNotBlank()) {
                            val newGift = Gift(id = randomUuid(), text = t, done = false)
                            SpheresStore.updatePerson(person.id) { it.copy(gifts = it.gifts + newGift) }
                            giftText = ""
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun NotesCard(person: Person) {
    var noteValue by remember(person.note) { mutableStateOf(person.note) }
    val purpleColor = Color(0xFF9D7CE7)

    Card(color = purpleColor) {
        Column(modifier = Modifier.fillMaxWidth()) {
            CardLabel(
                title = "Notes",
                icon = IconName.NOTE,
                color = purpleColor
            )
            PixelTextArea(
                value = noteValue,
                onValueChange = {
                    noteValue = it
                    SpheresStore.updatePerson(person.id) { p -> p.copy(note = it) }
                },
                placeholder = "Anything worth remembering"
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailsCard(person: Person) {
    val blueColor = Color(0xFF5B9CF0)
    var isAddingFact by remember { mutableStateOf(false) }
    var factLabel by remember { mutableStateOf("") }
    var factValue by remember { mutableStateOf("") }

    val usedLabels = remember(person.facts) {
        person.facts.map { it.label.lowercase() }.toSet()
    }

    Card(color = blueColor) {
        Column(modifier = Modifier.fillMaxWidth()) {
            CardLabel(
                title = "Details",
                icon = IconName.USER,
                color = blueColor
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (f in person.facts) {
                    val key = f.label.trim().lowercase()
                    val v = f.value.trim()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = f.label,
                            style = TextStyle(
                                fontFamily = PixelFontFamily(),
                                fontSize = 15.sp,
                                lineHeight = 24.sp,
                                color = Muted
                            ),
                            modifier = Modifier.width(96.dp)
                        )
                        Box(modifier = Modifier.weight(1f)) {
                            if (key == "phone") {
                                Text(
                                    text = v,
                                    style = TextStyle(
                                        fontFamily = SansFontFamily(),
                                        fontSize = 16.sp,
                                        lineHeight = 24.sp,
                                        color = Ink,
                                        textDecoration = TextDecoration.Underline
                                    ),
                                    modifier = Modifier.clickable {
                                        openExternalUrl("tel:${v.filter { ch -> ch.isDigit() || ch == '+' }}")
                                    }
                                )
                            } else if (key == "email") {
                                Text(
                                    text = v,
                                    style = TextStyle(
                                        fontFamily = SansFontFamily(),
                                        fontSize = 16.sp,
                                        lineHeight = 24.sp,
                                        color = Ink,
                                        textDecoration = TextDecoration.Underline
                                    ),
                                    modifier = Modifier.clickable {
                                        openExternalUrl("mailto:$v")
                                    }
                                )
                            } else {
                                Text(
                                    text = v,
                                    style = TextStyle(
                                        fontFamily = SansFontFamily(),
                                        fontSize = 16.sp,
                                        lineHeight = 24.sp,
                                        color = Ink
                                    )
                                )
                            }
                        }
                        Box(
                            modifier = Modifier.clickable {
                                val updated = person.facts.filter { it.id != f.id }
                                SpheresStore.updatePerson(person.id) { it.copy(facts = updated) }
                            }
                        ) {
                            PixelIcon(name = IconName.CLOSE, size = 16.dp, tint = Ink.copy(alpha = 0.35f))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (isAddingFact) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PixelTextInput(
                        value = factLabel,
                        onValueChange = { factLabel = it },
                        placeholder = "Label"
                    )
                    PixelTextInput(
                        value = factValue,
                        onValueChange = { factValue = it },
                        placeholder = "Value"
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PixelButton(
                            text = "Save",
                            variant = ButtonVariant.PRIMARY,
                            size = ButtonSize.SM,
                            onClick = {
                                val l = factLabel.trim()
                                val valText = factValue.trim()
                                if (l.isNotBlank() && valText.isNotBlank()) {
                                    val newFact = Fact(id = randomUuid(), label = l, value = valText)
                                    SpheresStore.updatePerson(person.id) { it.copy(facts = it.facts + newFact) }
                                }
                                isAddingFact = false
                                factLabel = ""
                                factValue = ""
                            }
                        )
                        PixelButton(
                            text = "Cancel",
                            variant = ButtonVariant.SECONDARY,
                            size = ButtonSize.SM,
                            onClick = { isAddingFact = false }
                        )
                    }
                }
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (s in SUGGESTED_FACTS.filter { !usedLabels.contains(it.lowercase()) }) {
                        Chip(
                            label = s,
                            icon = IconName.PLUS,
                            onClick = {
                                factLabel = s
                                factValue = ""
                                isAddingFact = true
                            }
                        )
                    }
                    Chip(
                        label = "Other",
                        icon = IconName.PLUS,
                        onClick = {
                            factLabel = ""
                            factValue = ""
                            isAddingFact = true
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelineCard(entries: List<Entry>) {
    Card {
        Column(modifier = Modifier.fillMaxWidth()) {
            CardLabel(
                title = "Timeline",
                icon = IconName.CLOCK
            )

            if (entries.isEmpty()) {
                Text(
                    text = "Nothing logged yet. Interactions and notes will show up here.",
                    style = TextStyle(
                        fontFamily = SansFontFamily(),
                        fontSize = 15.sp,
                        lineHeight = 24.sp,
                        color = Muted
                    )
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (e in entries) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(PxClipShape)
                                    .background(if (e.kind.isNote) FieldBg else Ink),
                                contentAlignment = Alignment.Center
                            ) {
                                val icon = KIND_ICON[e.kind] ?: IconName.STAR
                                PixelIcon(
                                    name = icon,
                                    size = 18.dp,
                                    tint = if (e.kind.isNote) Ink.copy(alpha = 0.6f) else Color.White
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                val label = KIND_LABEL[e.kind] ?: "Interaction"
                                val rel = relativeDays(e.at)
                                val qualityPart = if (e.quality != null) " · ${e.quality.label}" else ""
                                Text(
                                    text = "$label · $rel$qualityPart",
                                    style = TextStyle(
                                        fontFamily = PixelFontFamily(),
                                        fontSize = 14.sp,
                                        lineHeight = 20.sp,
                                        color = Muted
                                    )
                                )
                                if (e.text.isNotBlank()) {
                                    Text(
                                        text = e.text,
                                        style = TextStyle(
                                            fontFamily = SansFontFamily(),
                                            fontSize = 15.sp,
                                            lineHeight = 22.sp,
                                            color = Ink
                                        )
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier.clickable {
                                    SpheresStore.deleteEntry(e.id)
                                    SpheresStore.showToast("Entry deleted") {
                                        SpheresStore.logEntry(
                                            personId = e.personId,
                                            kind = e.kind,
                                            text = e.text,
                                            at = e.at,
                                            quality = e.quality
                                        )
                                    }
                                }
                            ) {
                                PixelIcon(name = IconName.TRASH, size = 16.dp, tint = Ink.copy(alpha = 0.3f))
                            }
                        }
                    }
                }
            }
        }
    }
}
