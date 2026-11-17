package de.koenidv.spheres.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.koenidv.spheres.data.SheetState
import de.koenidv.spheres.data.SpheresStore
import de.koenidv.spheres.domain.DueStatus
import de.koenidv.spheres.domain.FREE_CONNECTIONS_LIMIT
import de.koenidv.spheres.domain.INTERVALS
import de.koenidv.spheres.domain.Person
import de.koenidv.spheres.domain.SocialEvent
import de.koenidv.spheres.domain.contactScore
import de.koenidv.spheres.domain.dueInfo
import de.koenidv.spheres.domain.entriesByPerson
import de.koenidv.spheres.domain.inSphere
import de.koenidv.spheres.domain.intervalInfo
import de.koenidv.spheres.domain.sphereIdsViaEvents
import de.koenidv.spheres.ui.components.AnchoredPopupMenu
import de.koenidv.spheres.ui.components.ButtonSize
import de.koenidv.spheres.ui.components.ButtonVariant
import de.koenidv.spheres.ui.components.EmptyState
import de.koenidv.spheres.ui.components.HealthRing
import de.koenidv.spheres.ui.components.IconName
import de.koenidv.spheres.ui.components.PersonAvatar
import de.koenidv.spheres.ui.components.PixelButton
import de.koenidv.spheres.ui.components.PixelIcon
import de.koenidv.spheres.ui.components.PixelIconButton
import de.koenidv.spheres.ui.components.PixelTextInput
import de.koenidv.spheres.ui.components.PopupAnchor
import de.koenidv.spheres.ui.components.PopupMenuOption
import de.koenidv.spheres.ui.components.PxClipShape
import de.koenidv.spheres.ui.components.SpherePill
import de.koenidv.spheres.ui.components.healthColor
import de.koenidv.spheres.ui.components.rememberLiveNow
import de.koenidv.spheres.ui.theme.CardBg
import de.koenidv.spheres.ui.theme.FieldBg
import de.koenidv.spheres.ui.theme.Ink
import de.koenidv.spheres.ui.theme.Muted
import de.koenidv.spheres.ui.theme.PixelFontFamily
import de.koenidv.spheres.ui.theme.SansFontFamily
import de.koenidv.spheres.ui.theme.WeeklyPink
import de.koenidv.spheres.ui.theme.parseHexColor
import de.koenidv.spheres.ui.theme.tintColor

private const val FILTER_NONE = "__none__"

private enum class FilterKind(val title: String) {
    SPHERE("Sphere"),
    EVENT("Event"),
    INTERVAL("Interval"),
    SCORE("Score"),
}

private data class FilterSelection(
    val kind: FilterKind,
    val anchor: PopupAnchor,
)

private fun hasAnySphere(person: Person, events: List<SocialEvent>): Boolean =
    person.sphereIds.isNotEmpty() || sphereIdsViaEvents(person, events).isNotEmpty()

@Composable
fun PeopleScreen(
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by SpheresStore.state.collectAsState()
    val people = state.people
    val events = state.events
    val spheres = state.spheres
    val byPerson = remember(state.entries) { entriesByPerson(state.entries) }
    val now = rememberLiveNow()

    var sphereFilter by remember { mutableStateOf<String?>(null) }
    var eventFilter by remember { mutableStateOf<String?>(null) }
    var intervalFilter by remember { mutableStateOf<Int?>(null) }
    var scoreFilter by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var activeMenu by remember { mutableStateOf<FilterSelection?>(null) }

    val filteredPeople = remember(people, events, byPerson, sphereFilter, eventFilter, intervalFilter, scoreFilter, searchQuery, now) {
        val query = searchQuery.trim().lowercase()
        people
            .filter { p ->
                val matchesSphere = when (sphereFilter) {
                    null -> true
                    FILTER_NONE -> !hasAnySphere(p, events)
                    else -> inSphere(p, sphereFilter!!, events)
                }
                val matchesEvent = when (eventFilter) {
                    null -> true
                    FILTER_NONE -> p.eventIds.isEmpty()
                    else -> p.eventIds.contains(eventFilter)
                }
                val matchesInterval = when (intervalFilter) {
                    null -> true
                    -1 -> p.intervalDays == null
                    else -> p.intervalDays == intervalFilter
                }
                val currentScore = contactScore(p, byPerson[p.id], now)?.score
                val matchesScore = when (scoreFilter) {
                    null -> true
                    FILTER_NONE -> currentScore == null
                    "high" -> currentScore != null && currentScore >= 60
                    "medium" -> currentScore != null && currentScore in 30..59
                    "low" -> currentScore != null && currentScore < 30
                    else -> true
                }
                val matchesQuery = query.isEmpty() || p.name.lowercase().contains(query)
                matchesSphere && matchesEvent && matchesInterval && matchesScore && matchesQuery
            }
            .sortedBy { it.name.lowercase() }
    }

    val sphereLabel = when (sphereFilter) {
        null -> "Sphere"
        FILTER_NONE -> "No sphere"
        else -> spheres.firstOrNull { it.id == sphereFilter }?.name ?: "Sphere"
    }
    val sphereColor = sphereFilter?.takeIf { it != FILTER_NONE }?.let { id ->
        spheres.firstOrNull { it.id == id }?.color?.let { parseHexColor(it) }
    }

    val eventLabel = when (eventFilter) {
        null -> "Event"
        FILTER_NONE -> "No event"
        else -> events.firstOrNull { it.id == eventFilter }?.name ?: "Event"
    }
    val eventColor = eventFilter?.takeIf { it != FILTER_NONE }?.let { id ->
        events.firstOrNull { it.id == id }?.color?.let { parseHexColor(it) }
    }

    val intervalLabel = when (intervalFilter) {
        null -> "Interval"
        -1 -> "No interval"
        else -> INTERVALS.firstOrNull { it.days == intervalFilter }?.label ?: "Interval"
    }
    val intervalColor = intervalFilter?.takeIf { it != -1 }?.let { days ->
        INTERVALS.firstOrNull { it.days == days }?.color?.let { parseHexColor(it) }
    }

    val scoreLabel = when (scoreFilter) {
        null -> "Score"
        FILTER_NONE -> "No score"
        "high" -> "Strong"
        "medium" -> "Fading"
        "low" -> "Low"
        else -> "Score"
    }
    val scoreColor = when (scoreFilter) {
        "high" -> healthColor(80)
        "medium" -> healthColor(45)
        "low" -> healthColor(10)
        else -> null
    }

    val hasActiveFilters = sphereFilter != null || eventFilter != null || intervalFilter != null || scoreFilter != null

    val layoutDir = LocalLayoutDirection.current
    val statusInsets = WindowInsets.statusBars.asPaddingValues()
    val navInsets = WindowInsets.navigationBars.asPaddingValues()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = statusInsets.calculateTopPadding() + 12.dp,
                bottom = navInsets.calculateBottomPadding() + 112.dp,
                start = statusInsets.calculateStartPadding(layoutDir) + 16.dp,
                end = statusInsets.calculateEndPadding(layoutDir) + 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    val peopleAtLimit = !state.isUnlimited && people.size >= FREE_CONNECTIONS_LIMIT
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PixelIconButton(
                                icon = IconName.BACK,
                                onClick = { onNavigate("/spheres") },
                                size = 40.dp
                            )
                            Text(
                                text = "Everyone",
                                style = TextStyle(
                                    fontFamily = PixelFontFamily(),
                                    fontSize = 28.sp,
                                    lineHeight = 34.sp,
                                    color = Ink
                                )
                            )
                            if (!state.isUnlimited) {
                                SpherePill(
                                    label = "${people.size}/$FREE_CONNECTIONS_LIMIT",
                                    color = if (peopleAtLimit) tintColor(WeeklyPink, 35) else FieldBg
                                )
                            }
                        }

                        PixelButton(
                            text = "New",
                            icon = if (peopleAtLimit) IconName.SPARKLE else IconName.PLUS,
                            variant = ButtonVariant.SECONDARY,
                            size = ButtonSize.SM,
                            onClick = { SpheresStore.openSheet(SheetState.AddPerson(intervalDays = null)) }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    PixelTextInput(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = "Search everyone"
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterButton(
                            label = sphereLabel,
                            active = sphereFilter != null,
                            accentColor = sphereColor,
                            onOpen = { coords ->
                                val b = coords.boundsInWindow()
                                activeMenu = FilterSelection(FilterKind.SPHERE, PopupAnchor(b.left, b.top, b.width, b.height))
                            }
                        )
                        FilterButton(
                            label = eventLabel,
                            active = eventFilter != null,
                            accentColor = eventColor,
                            onOpen = { coords ->
                                val b = coords.boundsInWindow()
                                activeMenu = FilterSelection(FilterKind.EVENT, PopupAnchor(b.left, b.top, b.width, b.height))
                            }
                        )
                        FilterButton(
                            label = intervalLabel,
                            active = intervalFilter != null,
                            accentColor = intervalColor,
                            onOpen = { coords ->
                                val b = coords.boundsInWindow()
                                activeMenu = FilterSelection(FilterKind.INTERVAL, PopupAnchor(b.left, b.top, b.width, b.height))
                            }
                        )
                        FilterButton(
                            label = scoreLabel,
                            active = scoreFilter != null,
                            accentColor = scoreColor,
                            onOpen = { coords ->
                                val b = coords.boundsInWindow()
                                activeMenu = FilterSelection(FilterKind.SCORE, PopupAnchor(b.left, b.top, b.width, b.height))
                            }
                        )
                        if (hasActiveFilters) {
                            Box(
                                modifier = Modifier
                                    .height(36.dp)
                                    .clip(PxClipShape)
                                    .clickable {
                                        sphereFilter = null
                                        eventFilter = null
                                        intervalFilter = null
                                        scoreFilter = null
                                    }
                                    .padding(horizontal = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Clear",
                                    style = TextStyle(
                                        fontFamily = SansFontFamily(),
                                        fontSize = 13.sp,
                                        color = Muted
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                }
            }

            if (filteredPeople.isEmpty()) {
                item {
                    EmptyState(
                        icon = IconName.USERS,
                        title = if (people.isEmpty()) "No people yet" else "No matches",
                        text = if (people.isEmpty()) {
                            "Add people to keep track of your connections."
                        } else {
                            "Try another filter or search term."
                        }
                    )
                }
            } else {
                items(filteredPeople, key = { it.id }) { p ->
                    val personEntries = byPerson[p.id]
                    val info = intervalInfo(p.intervalDays)
                    val score = contactScore(p, personEntries, now)?.score
                    val sphereNames = remember(p.sphereIds, p.eventIds, spheres, events) {
                        val allIds = (p.sphereIds + sphereIdsViaEvents(p, events)).distinct()
                        allIds.mapNotNull { id -> spheres.firstOrNull { it.id == id }?.name }
                    }
                    val subtitle = remember(p, personEntries, sphereNames, now) {
                        val parts = mutableListOf<String>()
                        val d = dueInfo(p, personEntries, now)
                        if (p.intervalDays != null && d != null) {
                            val status = when (d.status) {
                                DueStatus.OVERDUE -> if (d.daysLeft < 0) "${-d.daysLeft}d overdue" else "Due today"
                                DueStatus.SOON -> "Due in ${d.daysLeft}d"
                                DueStatus.OK -> "${d.daysLeft}d left"
                            }
                            parts.add("$status · ${info.label}")
                        } else {
                            parts.add("Not tracked")
                        }
                        if (sphereNames.isNotEmpty()) {
                            parts.add(sphereNames.joinToString(", "))
                        }
                        if (p.eventIds.isNotEmpty()) {
                            parts.add("${p.eventIds.size} ${if (p.eventIds.size == 1) "event" else "events"}")
                        }
                        if (!hasAnySphere(p, events) && p.eventIds.isEmpty() && p.intervalDays == null) {
                            "No sphere, event, or interval"
                        } else {
                            parts.joinToString(" · ")
                        }
                    }

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
                                text = subtitle,
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
                        PixelIcon(name = IconName.CHEVRON_RIGHT, size = 18.dp, tint = Ink.copy(alpha = 0.25f))
                    }
                }
            }
        }

        val options = when (activeMenu?.kind) {
            FilterKind.SPHERE -> listOf(
                PopupMenuOption(null, "All spheres", selected = sphereFilter == null),
                PopupMenuOption(FILTER_NONE, "No sphere", selected = sphereFilter == FILTER_NONE),
            ) + spheres.map { s ->
                PopupMenuOption(s.id, s.name, color = parseHexColor(s.color), selected = sphereFilter == s.id)
            }
            FilterKind.EVENT -> listOf(
                PopupMenuOption(null, "All events", selected = eventFilter == null),
                PopupMenuOption(FILTER_NONE, "No event", selected = eventFilter == FILTER_NONE),
            ) + events.map { e ->
                PopupMenuOption(e.id, e.name, color = parseHexColor(e.color), selected = eventFilter == e.id)
            }
            FilterKind.INTERVAL -> listOf(
                PopupMenuOption(null, "All intervals", selected = intervalFilter == null),
                PopupMenuOption("-1", "No interval", selected = intervalFilter == -1),
            ) + INTERVALS.map { spec ->
                PopupMenuOption(
                    spec.days.toString(),
                    spec.label,
                    color = parseHexColor(spec.color),
                    selected = intervalFilter == spec.days
                )
            }
            FilterKind.SCORE -> listOf(
                PopupMenuOption(null, "All scores", selected = scoreFilter == null),
                PopupMenuOption("high", "Strong", color = healthColor(80), selected = scoreFilter == "high"),
                PopupMenuOption("medium", "Fading", color = healthColor(45), selected = scoreFilter == "medium"),
                PopupMenuOption("low", "Low", color = healthColor(10), selected = scoreFilter == "low"),
                PopupMenuOption(FILTER_NONE, "No score", selected = scoreFilter == FILTER_NONE),
            )
            null -> emptyList()
        }

        AnchoredPopupMenu(
            anchor = activeMenu?.anchor,
            title = activeMenu?.kind?.title ?: "",
            options = options,
            onSelect = { key ->
                when (activeMenu?.kind) {
                    FilterKind.SPHERE -> sphereFilter = key
                    FilterKind.EVENT -> eventFilter = key
                    FilterKind.INTERVAL -> intervalFilter = key?.toIntOrNull()
                    FilterKind.SCORE -> scoreFilter = key
                    null -> {}
                }
                activeMenu = null
            },
            onClose = { activeMenu = null }
        )
    }
}

@Composable
private fun FilterButton(
    label: String,
    active: Boolean,
    accentColor: Color?,
    onOpen: (LayoutCoordinates) -> Unit,
) {
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val bg = when {
        active && accentColor != null -> accentColor
        active -> Ink
        else -> FieldBg
    }
    val fg = if (active) Color.White else Ink

    Row(
        modifier = Modifier
            .height(36.dp)
            .clip(PxClipShape)
            .background(bg)
            .onGloballyPositioned { coords = it }
            .clickable { coords?.let(onOpen) }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontFamily = PixelFontFamily(),
                fontSize = 14.sp,
                color = fg
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        PixelIcon(
            name = IconName.CHEVRON_RIGHT,
            size = 14.dp,
            tint = fg.copy(alpha = 0.7f),
            modifier = Modifier.rotate(90f)
        )
    }
}
