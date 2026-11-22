package de.koenidv.spheres.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.koenidv.spheres.data.SheetState
import de.koenidv.spheres.data.SpheresStore
import de.koenidv.spheres.domain.DAY
import de.koenidv.spheres.domain.DueInfo
import de.koenidv.spheres.domain.DueStatus
import de.koenidv.spheres.domain.INTERVALS
import de.koenidv.spheres.domain.Person
import de.koenidv.spheres.domain.SocialEvent
import de.koenidv.spheres.domain.Sphere
import de.koenidv.spheres.domain.Suggestion
import de.koenidv.spheres.domain.SuggestionReason
import de.koenidv.spheres.domain.contactScore
import de.koenidv.spheres.platform.currentTimeMillis
import de.koenidv.spheres.domain.dueInfo
import de.koenidv.spheres.domain.entriesByPerson
import de.koenidv.spheres.domain.firstName
import de.koenidv.spheres.domain.formatBirthday
import de.koenidv.spheres.domain.formatDay
import de.koenidv.spheres.domain.intervalInfo
import de.koenidv.spheres.domain.keepInTouchSuggestions
import de.koenidv.spheres.domain.nextBirthday
import de.koenidv.spheres.domain.recentBirthday
import de.koenidv.spheres.domain.relativeDays
import de.koenidv.spheres.domain.sphereIdsViaEvents
import de.koenidv.spheres.ui.components.ButtonSize
import de.koenidv.spheres.ui.components.ButtonVariant
import de.koenidv.spheres.ui.components.Chip
import de.koenidv.spheres.ui.components.HealthRing
import de.koenidv.spheres.ui.components.IconName
import de.koenidv.spheres.ui.components.PersonAvatar
import de.koenidv.spheres.ui.components.PixelButton
import de.koenidv.spheres.ui.components.PixelIcon
import de.koenidv.spheres.ui.components.PixelPanel
import de.koenidv.spheres.ui.components.PxClipShape
import de.koenidv.spheres.ui.components.ScreenHeader
import de.koenidv.spheres.ui.components.SwipeAction
import de.koenidv.spheres.ui.components.SwipeRow
import de.koenidv.spheres.ui.components.rememberLiveNow
import de.koenidv.spheres.ui.theme.DangerText
import de.koenidv.spheres.ui.theme.FieldBg
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
import kotlin.math.floor

private const val CHECK_IN_COLOR = "#3fbf9f"
private const val LATER_COLOR = "#9d7ce7"

private data class CheckRow(val person: Person, val due: DueInfo, val priority: Double)
private data class BirthdayRow(val person: Person, val inDays: Int, val turns: Int?)

private fun dueText(due: DueInfo, label: String): String {
    val n = due.daysLeft
    val whenText = if (due.status == DueStatus.OVERDUE) {
        if (n >= 0) "due today" else "${-n} ${if (-n == 1) "day" else "days"} overdue"
    } else {
        if (n == 1) "due tomorrow" else "due in $n days"
    }
    return "$whenText · $label"
}

private fun followUpText(ts: Long, now: Long = currentTimeMillis()): Pair<String, Color?> {
    val tz = TimeZone.currentSystemDefault()
    val todayStart = Instant.fromEpochMilliseconds(now).toLocalDateTime(tz).date.atStartOfDayIn(tz).toEpochMilliseconds()
    val n = floor((ts - todayStart).toDouble() / DAY).toInt()
    return when {
        n < 0 -> Pair("Follow up · ${-n} ${if (-n == 1) "day" else "days"} overdue", DangerText)
        n == 0 -> Pair("Follow up today", DangerText)
        n == 1 -> Pair("Follow up tomorrow", null)
        else -> Pair("Follow up in $n days", null)
    }
}

@Composable
fun DueScreen(
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by SpheresStore.state.collectAsState()
    val people = state.people
    val entries = state.entries
    val spheres = state.spheres
    val events = state.events

    var showAllSuggestions by remember { mutableStateOf(false) }

    val byPerson = remember(entries) { entriesByPerson(entries) }
    val now = rememberLiveNow()

    val overdue = remember(people, byPerson, now) {
        val list = mutableListOf<CheckRow>()
        for (p in people) {
            val due = dueInfo(p, byPerson[p.id], now)
            val cs = contactScore(p, byPerson[p.id], now)
            if (due != null && cs != null && due.status == DueStatus.OVERDUE) {
                list.add(CheckRow(p, due, cs.priority))
            }
        }
        list.sortedWith(compareByDescending<CheckRow> { it.priority }.thenBy { it.due.dueAt })
    }

    val soon = remember(people, byPerson, now) {
        val list = mutableListOf<CheckRow>()
        for (p in people) {
            val due = dueInfo(p, byPerson[p.id], now)
            val cs = contactScore(p, byPerson[p.id], now)
            if (due != null && cs != null && (due.status == DueStatus.SOON || due.daysLeft <= 7) && due.status != DueStatus.OVERDUE) {
                list.add(CheckRow(p, due, cs.priority))
            }
        }
        list.sortedWith(compareByDescending<CheckRow> { it.priority }.thenBy { it.due.dueAt })
    }

    val followUps = remember(people, now) {
        people.filter { it.followUpAt != null && it.followUpAt!! <= now + 7 * DAY }
            .sortedBy { it.followUpAt ?: 0L }
    }

    val birthdays = remember(people, byPerson, now) {
        val list = mutableListOf<BirthdayRow>()
        for (p in people) {
            val b = p.birthday ?: continue
            val nb = nextBirthday(b, now)
            if (nb.inDays <= 30) {
                list.add(BirthdayRow(p, nb.inDays, nb.turns))
            }
            val rb = recentBirthday(b, now)
            if (rb != null) {
                val hasLoggedSince = (byPerson[p.id] ?: emptyList()).any { !it.kind.isNote && it.at >= rb.date }
                if (!hasLoggedSince) {
                    list.add(BirthdayRow(p, -rb.daysAgo, rb.turned))
                }
            }
        }
        list.sortedBy { it.inDays }
    }

    val suggestions = remember(people, byPerson, now) {
        keepInTouchSuggestions(people, byPerson, now)
    }

    val scores = remember(people, byPerson, now) {
        people.associate { it.id to contactScore(it, byPerson[it.id], now)?.score }
    }

    val waiting = overdue.size + soon.filter { it.due.status == DueStatus.SOON }.size
    val subtitle = if (waiting == 0) {
        "Nobody is waiting on you right now."
    } else {
        "$waiting ${if (waiting == 1) "person would" else "people would"} love to hear from you."
    }

    val caughtUp = overdue.isEmpty() && soon.isEmpty() && suggestions.isEmpty() && birthdays.isEmpty() && followUps.isEmpty()
    val shownSuggestions = if (showAllSuggestions) suggestions else suggestions.take(4)

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
            ScreenHeader(
                title = "Check-ins",
                subtitle = subtitle
            )

            if (state.unlinkedCallPrompts.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    for (prompt in state.unlinkedCallPrompts) {
                        PixelPanel(color = Color(0xFF9D7CE7)) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    PixelIcon(name = IconName.PHONE, size = 16.dp, tint = Ink)
                                    Text(
                                        text = "Long call with ${prompt.contactName}",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = TextStyle(
                                            fontFamily = PixelFontFamily(),
                                            fontSize = 15.sp,
                                            color = Ink
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clickable { SpheresStore.dismissUnlinkedCallPrompt(prompt.contactId) }
                                            .padding(4.dp)
                                    ) {
                                        PixelIcon(name = IconName.CLOSE, size = 14.dp, tint = Muted)
                                    }
                                }
                                Text(
                                    text = "${prompt.durationMinutes} min call · Link ${prompt.contactName} to a connection to log future calls automatically?",
                                    style = TextStyle(
                                        fontFamily = SansFontFamily(),
                                        fontSize = 14.sp,
                                        lineHeight = 20.sp,
                                        color = Ink.copy(alpha = 0.8f)
                                    )
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    PixelButton(
                                        text = "Link connection",
                                        variant = ButtonVariant.PRIMARY,
                                        size = ButtonSize.SM,
                                        onClick = {
                                            SpheresStore.openSheet(
                                                SheetState.LinkContact(
                                                    contactId = prompt.contactId,
                                                    contactName = prompt.contactName,
                                                    callStartedAt = prompt.callStartedAt,
                                                    durationMinutes = prompt.durationMinutes,
                                                )
                                            )
                                        }
                                    )
                                    PixelButton(
                                        text = "Don't ask again",
                                        variant = ButtonVariant.SECONDARY,
                                        size = ButtonSize.SM,
                                        onClick = { SpheresStore.ignoreUnlinkedContact(prompt.contactId) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (caughtUp) {
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp)) {
                    PixelPanel(
                        color = Color(0xFFF0A83C)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp, horizontal = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            val transition = rememberInfiniteTransition()
                            val bounceY by transition.animateFloat(
                                initialValue = 0f,
                                targetValue = -8f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(600, easing = LinearEasing),
                                    repeatMode = RepeatMode.Reverse
                                )
                            )

                            Box(contentAlignment = Alignment.Center) {
                                PixelIcon(
                                    name = IconName.SPARKLE,
                                    size = 72.dp,
                                    tint = Color(0xFFF0A83C),
                                    modifier = Modifier.offset(y = bounceY.dp)
                                )
                                PixelIcon(
                                    name = IconName.SPARKLE,
                                    size = 24.dp,
                                    tint = Color(0xFFEB78B0),
                                    modifier = Modifier.offset(x = 36.dp, y = (-20).dp)
                                )
                                PixelIcon(
                                    name = IconName.SPARKLE,
                                    size = 20.dp,
                                    tint = Color(0xFF5B9CF0),
                                    modifier = Modifier.offset(x = (-32).dp, y = 20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(20.dp))
                            Text(
                                text = "All caught up",
                                style = TextStyle(
                                    fontFamily = PixelFontFamily(),
                                    fontSize = 24.sp,
                                    color = Ink
                                )
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (people.any { it.intervalDays != null }) {
                                    "Everyone on your radar is in good company. Enjoy the quiet."
                                } else {
                                    "Pick an interval for a connection and gentle nudges will show up here."
                                },
                                style = TextStyle(
                                    fontFamily = SansFontFamily(),
                                    fontSize = 15.sp,
                                    lineHeight = 24.sp,
                                    color = Muted
                                ),
                                modifier = Modifier.width(280.dp)
                            )
                        }
                    }
                }
            }

            if (followUps.isNotEmpty()) {
                SectionHeader("Follow-ups", followUps.size)
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (person in followUps) {
                        val f = followUpText(person.followUpAt ?: 0L, now)
                        DueRow(
                            person = person,
                            score = scores[person.id],
                            sub = f.first,
                            subColor = f.second ?: Muted,
                            onOpen = { onNavigate("/people/${person.id}") },
                            actions = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(PxClipShape)
                                            .clickable { SpheresStore.setFollowUp(person.id, 1) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        PixelIcon(name = IconName.SNOOZE, size = 20.dp, tint = Ink.copy(alpha = 0.45f))
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(PxClipShape)
                                            .background(Ink)
                                            .clickable { SpheresStore.quickCheckIn(person.id) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        PixelIcon(name = IconName.CHECK, size = 22.dp, tint = Color.White)
                                    }
                                }
                            },
                            swipeRight = SwipeAction("Check in", IconName.CHECK, CHECK_IN_COLOR) {
                                SpheresStore.quickCheckIn(person.id)
                            },
                            swipeLeft = SwipeAction("Tomorrow", IconName.SNOOZE, LATER_COLOR) {
                                SpheresStore.setFollowUp(person.id, 1)
                            }
                        )
                    }
                }
            }

            if (overdue.isNotEmpty()) {
                SectionHeader("Overdue", overdue.size)
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for ((person, due) in overdue) {
                        val info = intervalInfo(person.intervalDays)
                        DueRow(
                            person = person,
                            score = scores[person.id],
                            sub = dueText(due, info.label),
                            subColor = DangerText,
                            onOpen = { onNavigate("/people/${person.id}") },
                            actions = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(PxClipShape)
                                            .clickable { SpheresStore.snooze(person.id, 3) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        PixelIcon(name = IconName.SNOOZE, size = 20.dp, tint = Ink.copy(alpha = 0.45f))
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(PxClipShape)
                                            .background(Ink)
                                            .clickable { SpheresStore.quickCheckIn(person.id) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        PixelIcon(name = IconName.CHECK, size = 22.dp, tint = Color.White)
                                    }
                                }
                            },
                            swipeRight = SwipeAction("Check in", IconName.CHECK, CHECK_IN_COLOR) {
                                SpheresStore.quickCheckIn(person.id)
                            },
                            swipeLeft = SwipeAction("3 days", IconName.SNOOZE, LATER_COLOR) {
                                SpheresStore.snooze(person.id, 3)
                            }
                        )
                    }
                }
            }

            if (soon.isNotEmpty()) {
                SectionHeader("Coming up", soon.size)
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for ((person, due) in soon) {
                        val info = intervalInfo(person.intervalDays)
                        DueRow(
                            person = person,
                            score = scores[person.id],
                            sub = dueText(due, info.label),
                            subColor = Muted,
                            onOpen = { onNavigate("/people/${person.id}") },
                            actions = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(PxClipShape)
                                            .clickable { SpheresStore.snooze(person.id, 3) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        PixelIcon(name = IconName.SNOOZE, size = 20.dp, tint = Ink.copy(alpha = 0.45f))
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(PxClipShape)
                                            .background(Ink)
                                            .clickable { SpheresStore.quickCheckIn(person.id) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        PixelIcon(name = IconName.CHECK, size = 22.dp, tint = Color.White)
                                    }
                                }
                            },
                            swipeRight = SwipeAction("Check in", IconName.CHECK, CHECK_IN_COLOR) {
                                SpheresStore.quickCheckIn(person.id)
                            },
                            swipeLeft = SwipeAction("3 days", IconName.SNOOZE, LATER_COLOR) {
                                SpheresStore.snooze(person.id, 3)
                            }
                        )
                    }
                }
            }

            if (birthdays.isNotEmpty()) {
                SectionHeader("Birthdays", birthdays.size)
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for ((person, inDays, turns) in birthdays) {
                        val giftsCount = person.gifts.count { !it.done }
                        val past = inDays < 0
                        val whenText = if (past) {
                            if (-inDays == 1) "yesterday" else "${-inDays} days ago"
                        } else {
                            when (inDays) {
                                0 -> "today"
                                1 -> "tomorrow"
                                else -> "in $inDays days"
                            }
                        }
                        val base = person.birthday?.let { formatBirthday(it.copy(year = null)) } ?: ""
                        val age = if (turns != null) {
                            if (past) "turned $turns " else "turns $turns "
                        } else {
                            if (past) "was " else ""
                        }
                        val giftPart = if (giftsCount > 0 && !past) {
                            " · $giftsCount gift ${if (giftsCount == 1) "idea" else "ideas"}"
                        } else ""
                        val sub = "$base · $age$whenText$giftPart"

                        DueRow(
                            person = person,
                            score = scores[person.id],
                            sub = sub,
                            subColor = Muted,
                            onOpen = { onNavigate("/people/${person.id}") },
                            actions = {
                                if (past || inDays == 0) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(PxClipShape)
                                            .background(Ink)
                                            .clickable { SpheresStore.quickCheckIn(person.id) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        PixelIcon(name = IconName.CHECK, size = 22.dp, tint = Color.White)
                                    }
                                } else {
                                    PixelIcon(
                                        name = if (giftsCount > 0) IconName.GIFT else IconName.CAKE,
                                        size = 24.dp,
                                        tint = Color(0xFFEB78B0),
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                }
                            },
                            swipeRight = if (past || inDays == 0) {
                                SwipeAction("Check in", IconName.CHECK, CHECK_IN_COLOR) {
                                    SpheresStore.quickCheckIn(person.id)
                                }
                            } else null
                        )
                    }
                }
            }

            if (suggestions.isNotEmpty()) {
                SectionHeader("Keep in touch?", suggestions.size)
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (sg in shownSuggestions) {
                        SuggestionRow(
                            sg = sg,
                            spheres = spheres,
                            events = events,
                            onOpen = { onNavigate("/people/${sg.person.id}") }
                        )
                    }
                }

                if (suggestions.size > 4) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .height(40.dp)
                            .clip(PxClipShape)
                            .background(FieldBg)
                            .clickable { showAllSuggestions = !showAllSuggestions },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (showAllSuggestions) "Show fewer" else "Show ${suggestions.size - 4} more",
                            style = TextStyle(
                                fontFamily = PixelFontFamily(),
                                fontSize = 15.sp,
                                color = Ink
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title.uppercase(),
            style = TextStyle(
                fontFamily = PixelFontFamily(),
                fontSize = 14.sp,
                letterSpacing = 0.05.sp,
                color = Muted
            )
        )
        Box(
            modifier = Modifier
                .clip(PxClipShape)
                .background(FieldBg)
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                text = count.toString(),
                style = TextStyle(
                    fontFamily = PixelFontFamily(),
                    fontSize = 12.sp,
                    color = Ink.copy(alpha = 0.6f)
                )
            )
        }
    }
}

@Composable
private fun DueRow(
    person: Person,
    score: Int?,
    sub: String,
    subColor: Color,
    onOpen: () -> Unit,
    actions: @Composable () -> Unit,
    swipeRight: SwipeAction? = null,
    swipeLeft: SwipeAction? = null
) {
    val info = intervalInfo(person.intervalDays)

    SwipeRow(
        left = swipeLeft,
        right = swipeRight
    ) {
        PixelPanel {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
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
                        size = 44.dp
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = person.name,
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
                            text = sub,
                            style = TextStyle(
                                fontFamily = SansFontFamily(),
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                color = subColor
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                actions()
            }
        }
    }
}

@Composable
private fun SuggestionRow(
    sg: Suggestion,
    spheres: List<Sphere>,
    events: List<SocialEvent>,
    onOpen: () -> Unit
) {
    val person = sg.person
    val mine = spheres.filter { person.sphereIds.contains(it.id) || sphereIdsViaEvents(person, events).contains(it.id) }
    val met = events.filter { person.eventIds.contains(it.id) }

    val reasonText = if (sg.reason == SuggestionReason.NEW) {
        "Added ${relativeDays(person.createdAt)} · never contacted"
    } else {
        "Last contact ${if (sg.last != null) relativeDays(sg.last) else "a while ago"}"
    }

    SwipeRow(
        left = SwipeAction("Dismiss", IconName.CLOSE, "#e36f73") {
            SpheresStore.dismissSuggestion(person.id)
        }
    ) {
        PixelPanel(color = Color(0xFF9D7CE7)) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(onClick = onOpen),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        PersonAvatar(
                            person = person,
                            colorHex = intervalInfo(null).color,
                            size = 44.dp
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = person.name,
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
                                text = reasonText,
                                style = TextStyle(
                                    fontFamily = SansFontFamily(),
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp,
                                    color = Muted
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
                                        color = Ink.copy(alpha = 0.7f)
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(PxClipShape)
                            .clickable { SpheresStore.dismissSuggestion(person.id) },
                        contentAlignment = Alignment.Center
                    ) {
                        PixelIcon(name = IconName.CLOSE, size = 18.dp, tint = Ink.copy(alpha = 0.4f))
                    }
                }

                if (met.isNotEmpty() || mine.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for (x in met) {
                            val c = parseHexColor(x.color)
                            Box(
                                modifier = Modifier
                                    .height(20.dp)
                                    .clip(PxClipShape)
                                    .background(tintColor(c, 60))
                                    .padding(horizontal = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${x.name} · ${formatDay(x.date)}",
                                    style = TextStyle(
                                        fontFamily = PixelFontFamily(),
                                        fontSize = 12.sp,
                                        color = Ink
                                    )
                                )
                            }
                        }
                        for (x in mine) {
                            val c = parseHexColor(x.color)
                            Box(
                                modifier = Modifier
                                    .height(20.dp)
                                    .clip(PxClipShape)
                                    .background(tintColor(c, 60))
                                    .padding(horizontal = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = x.name,
                                    style = TextStyle(
                                        fontFamily = PixelFontFamily(),
                                        fontSize = 12.sp,
                                        color = Ink
                                    )
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (i in INTERVALS) {
                        Chip(
                            label = i.label,
                            color = parseHexColor(i.color),
                            onClick = {
                                SpheresStore.keepInTouch(person.id, i.days)
                                SpheresStore.showToast("${firstName(person.name)} is now on your radar")
                            }
                        )
                    }
                }
            }
        }
    }
}
