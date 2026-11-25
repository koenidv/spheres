package de.koenidv.spheres.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.unit.Dp
import de.koenidv.spheres.ui.components.LocalSelectedRoute
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import de.koenidv.spheres.ui.components.LocalBottomChromeInset
import de.koenidv.spheres.data.SheetState
import de.koenidv.spheres.data.SpheresStore
import de.koenidv.spheres.domain.FREE_CONNECTIONS_LIMIT
import de.koenidv.spheres.domain.FREE_SPHERES_LIMIT
import de.koenidv.spheres.domain.Person
import de.koenidv.spheres.domain.SocialEvent
import de.koenidv.spheres.domain.Sphere
import de.koenidv.spheres.platform.currentTimeMillis
import de.koenidv.spheres.domain.formatDay
import de.koenidv.spheres.domain.inSphere
import de.koenidv.spheres.domain.intervalInfo
import de.koenidv.spheres.domain.sameDay
import de.koenidv.spheres.ui.components.ButtonSize
import de.koenidv.spheres.ui.components.ButtonVariant
import de.koenidv.spheres.ui.components.IconName
import de.koenidv.spheres.ui.components.PersonAvatar
import de.koenidv.spheres.ui.components.PixelAvatar
import de.koenidv.spheres.ui.components.PixelButton
import de.koenidv.spheres.ui.components.PixelIcon
import de.koenidv.spheres.ui.components.PixelPanel
import de.koenidv.spheres.ui.components.PxClipShape
import de.koenidv.spheres.ui.components.ScreenHeader
import de.koenidv.spheres.ui.theme.Ink
import de.koenidv.spheres.ui.theme.Muted
import de.koenidv.spheres.ui.theme.PixelFontFamily
import de.koenidv.spheres.ui.theme.SansFontFamily
import de.koenidv.spheres.ui.theme.parseHexColor
import de.koenidv.spheres.ui.theme.tintColor
import kotlin.math.abs
import kotlin.math.hypot

@Composable
fun SpheresScreen(
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by SpheresStore.state.collectAsState()
    val spheres = state.spheres
    val events = state.events
    val people = state.people

    val sortedSpheres = remember(spheres) {
        spheres.sortedBy { it.name.lowercase() }
    }
    val todayEvents = remember(events) {
        val now = currentTimeMillis()
        events.filter { sameDay(it.date, now) }
    }

    val accentColorHex = "#9d7ce7"
    val accentColor = parseHexColor(accentColorHex)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        val narrow = maxWidth < 420.dp
        val gridWidth = maxWidth - 32.dp
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(bottom = LocalBottomChromeInset.current)
        ) {
            ScreenHeader(
                title = "Spheres",
                subtitle = "Groups and events — your people, in context."
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                PixelPanel(
                    color = accentColor,
                    raised = true,
                    onClick = {
                        val newEventId = SpheresStore.addEvent()
                        onNavigate("/events/$newEventId")
                    }
                ) {
                    if (narrow) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            PixelIcon(name = IconName.PARTY, size = 24.dp, tint = Ink)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Start event",
                                    style = TextStyle(
                                        fontFamily = PixelFontFamily(),
                                        fontSize = 20.sp,
                                        lineHeight = 26.sp,
                                        color = Ink
                                    )
                                )
                                Text(
                                    text = "Jot down names as you meet people.",
                                    style = TextStyle(
                                        fontFamily = SansFontFamily(),
                                        fontSize = 13.sp,
                                        lineHeight = 18.sp,
                                        color = Ink.copy(alpha = 0.7f)
                                    )
                                )
                            }
                            PixelIcon(name = IconName.CHEVRON_RIGHT, size = 20.dp, tint = Ink)
                        }
                    } else Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(132.dp)
                    ) {
                        ArcArt(
                            color = accentColor,
                            people = people,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .offset(x = 4.dp, y = 4.dp)
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth(0.62f)
                                .align(Alignment.TopStart)
                        ) {
                            Text(
                                text = "Start event",
                                style = TextStyle(
                                    fontFamily = PixelFontFamily(),
                                    fontSize = 26.sp,
                                    lineHeight = 32.sp,
                                    color = Ink
                                )
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "At a meetup? Jot down names as you meet people.",
                                style = TextStyle(
                                    fontFamily = SansFontFamily(),
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp,
                                    color = Ink.copy(alpha = 0.7f)
                                )
                            )
                        }

                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .clip(PxClipShape)
                                .background(Ink)
                                .padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Start",
                                style = TextStyle(
                                    fontFamily = PixelFontFamily(),
                                    fontSize = 16.sp,
                                    color = Color.White
                                )
                            )
                            PixelIcon(name = IconName.CHEVRON_RIGHT, size = 20.dp, tint = Color.White)
                        }
                    }
                }
            }

            if (todayEvents.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (e in todayEvents) {
                        val metCount = people.count { it.eventIds.contains(e.id) }
                        val eventColor = parseHexColor(e.color)
                        PixelPanel(
                            color = eventColor,
                            onClick = { onNavigate("/events/${e.id}") }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                PixelIcon(name = IconName.PARTY, size = 20.dp, tint = Ink)
                                Text(
                                    text = "Continue ${e.name}",
                                    style = TextStyle(
                                        fontFamily = PixelFontFamily(),
                                        fontSize = 16.sp,
                                        lineHeight = 24.sp,
                                        color = Ink
                                    ),
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "$metCount met",
                                    style = TextStyle(
                                        fontFamily = SansFontFamily(),
                                        fontSize = 13.sp,
                                        color = Ink.copy(alpha = 0.6f)
                                    )
                                )
                                PixelIcon(name = IconName.CHEVRON_RIGHT, size = 18.dp, tint = Ink.copy(alpha = 0.4f))
                            }
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                val spheresAtLimit = !state.isUnlimited && spheres.size >= FREE_SPHERES_LIMIT
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (state.isUnlimited) "SPHERES" else "SPHERES · ${spheres.size}/$FREE_SPHERES_LIMIT",
                        style = TextStyle(
                            fontFamily = PixelFontFamily(),
                            fontSize = 14.sp,
                            letterSpacing = 0.05.sp,
                            color = Muted
                        ),
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    PixelButton(
                        text = if (narrow) "New" else "New sphere",
                        icon = if (spheresAtLimit) IconName.SPARKLE else IconName.PLUS,
                        variant = ButtonVariant.SECONDARY,
                        size = ButtonSize.SM,
                        onClick = { SpheresStore.openSheet(SheetState.SphereEdit(null)) }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (sortedSpheres.isEmpty()) {
                    PixelPanel {
                        Text(
                            text = "Spheres like Family or Climbing help you see your people in context.",
                            style = TextStyle(
                                fontFamily = SansFontFamily(),
                                fontSize = 15.sp,
                                lineHeight = 24.sp,
                                color = Muted
                            )
                        )
                    }
                } else {
                    CardGrid(items = sortedSpheres, width = gridWidth) { sphere ->
                        val members = remember(people, events, sphere.id) {
                            people.filter { inSphere(it, sphere.id, events) }
                        }
                        SphereCard(
                            sphere = sphere,
                            members = members,
                            selected = LocalSelectedRoute.current == "/spheres/${sphere.id}",
                            onClick = { onNavigate("/spheres/${sphere.id}") }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier
                        .clip(PxClipShape)
                        .clickable { onNavigate("/people") }
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Everyone (${people.size}${if (!state.isUnlimited) "/$FREE_CONNECTIONS_LIMIT" else ""})",
                        style = TextStyle(
                            fontFamily = SansFontFamily(),
                            fontSize = 14.sp,
                            color = Muted
                        )
                    )
                    PixelIcon(name = IconName.CHEVRON_RIGHT, size = 16.dp, tint = Muted)
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
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

                Spacer(modifier = Modifier.height(10.dp))

                if (events.isEmpty()) {
                    PixelPanel {
                        Text(
                            text = "No events yet. Your meetups and parties will collect here.",
                            style = TextStyle(
                                fontFamily = SansFontFamily(),
                                fontSize = 15.sp,
                                lineHeight = 24.sp,
                                color = Muted
                            )
                        )
                    }
                } else {
                    CardGrid(items = events, width = gridWidth) { event ->
                        val metCount = remember(people, event.id) {
                            people.count { it.eventIds.contains(event.id) }
                        }
                        EventCard(
                            event = event,
                            metCount = metCount,
                            spheres = spheres,
                            selected = LocalSelectedRoute.current == "/events/${event.id}",
                            onClick = { onNavigate("/events/${event.id}") }
                        )
                    }
                }
            }
        }
    }
}

private val MinCardWidth = 260.dp
private val CardGap = 10.dp

// Columns follow the width so cards never get cramped; narrow lists stay a single column
@Composable
private fun <T> CardGrid(items: List<T>, width: Dp, card: @Composable (T) -> Unit) {
    val columns = ((width + CardGap) / (MinCardWidth + CardGap)).toInt().coerceIn(1, 3)
    Column(verticalArrangement = Arrangement.spacedBy(CardGap)) {
        for (row in items.chunked(columns)) {
            Row(horizontalArrangement = Arrangement.spacedBy(CardGap)) {
                for (item in row) {
                    Box(modifier = Modifier.weight(1f)) { card(item) }
                }
                repeat(columns - row.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun AvatarStack(people: List<Person>, max: Int = 4) {
    val shown = people.take(max)
    if (shown.isEmpty()) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.width(32.dp + 18.dp * (shown.size - 1)).height(32.dp)) {
            shown.forEachIndexed { i, p ->
                val info = intervalInfo(p.intervalDays)
                Box(
                    modifier = Modifier
                        .offset(x = 18.dp * i)
                        .zIndex((shown.size - i).toFloat())
                        .clip(PxClipShape)
                        .background(Color.White)
                        .padding(2.dp)
                ) {
                    PersonAvatar(
                        person = p,
                        colorHex = info.color,
                        size = 28.dp
                    )
                }
            }
        }
        if (people.size > shown.size) {
            Text(
                text = "+${people.size - shown.size}",
                style = TextStyle(
                    fontFamily = PixelFontFamily(),
                    fontSize = 14.sp,
                    color = Ink.copy(alpha = 0.6f)
                ),
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

@Composable
private fun CountPill(count: Int) {
    Row(
        modifier = Modifier
            .height(24.dp)
            .clip(PxClipShape)
            .background(Color.White)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        PixelIcon(name = IconName.USERS, size = 14.dp, tint = Ink.copy(alpha = 0.6f))
        Text(
            text = count.toString(),
            style = TextStyle(
                fontFamily = PixelFontFamily(),
                fontSize = 14.sp,
                color = Ink
            )
        )
    }
}

private fun Modifier.selectedOutline(selected: Boolean): Modifier =
    if (selected) border(2.dp, Ink, PxClipShape) else this

private data class ArcSpec(val cx: Float, val cy: Float, val r: Float, val alpha: Float)

private val ARCS = listOf(
    ArcSpec(27f, 17f, 16f, 0.35f),
    ArcSpec(20f, 17f, 11f, 0.60f),
    ArcSpec(26f, 17f, 6f, 1.0f)
)

@Composable
private fun ArcArt(
    color: Color,
    people: List<Person>,
    modifier: Modifier = Modifier
) {
    val names = remember(people) {
        if (people.isNotEmpty()) people.map { it.name } else listOf("Ada", "Ben", "Cy")
    }
    val spots = listOf(
        Pair(6.dp, 38.dp),
        Pair(48.dp, 14.dp),
        Pair(84.dp, 44.dp)
    )

    Box(
        modifier = modifier
            .size(width = 112.dp, height = 72.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cellW = size.width / 28f
            val cellH = size.height / 18f
            for (a in ARCS) {
                for (y in 0 until 18) {
                    if (y > a.cy) continue
                    for (x in 0 until 28) {
                        val d = hypot(x + 0.5f - a.cx, y + 0.5f - a.cy)
                        if (abs(d - a.r) < 0.62f) {
                            drawRect(
                                color = color.copy(alpha = a.alpha),
                                topLeft = Offset(x * cellW, y * cellH),
                                size = Size(cellW, cellH)
                            )
                        }
                    }
                }
            }
        }

        spots.forEachIndexed { i, (left, top) ->
            Box(
                modifier = Modifier
                    .offset(x = left, y = top)
                    .size(32.dp)
                    .clip(PxClipShape)
                    .background(Color.White)
                    .padding(2.dp)
            ) {
                if (people.isNotEmpty()) {
                    PersonAvatar(
                        person = people[i % people.size],
                        colorHex = "#9d7ce7",
                        size = 28.dp
                    )
                } else {
                    PixelAvatar(
                        name = names[i % names.size],
                        colorHex = "#9d7ce7",
                        size = 28.dp
                    )
                }
            }
        }
    }
}

@Composable
private fun SphereCard(
    sphere: Sphere,
    members: List<Person>,
    selected: Boolean,
    onClick: () -> Unit
) {
    val sphereColor = parseHexColor(sphere.color)
    PixelPanel(
        modifier = Modifier.selectedOutline(selected),
        color = sphereColor,
        raised = true,
        onClick = onClick
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val cw = maxWidth
            val roomy = cw >= 230.dp
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(if (roomy) 44.dp else 36.dp)
                        .clip(PxClipShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    PixelIcon(name = IconName.USERS, size = if (roomy) 26.dp else 22.dp, tint = sphereColor)
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = sphere.name,
                        style = TextStyle(
                            fontFamily = PixelFontFamily(),
                            fontSize = 18.sp,
                            lineHeight = 22.sp,
                            color = Ink
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CountPill(members.size)
                        if (roomy) AvatarStack(members, max = if (cw >= 340.dp) 4 else 3)
                    }
                }

                if (roomy) {
                    PixelIcon(name = IconName.CHEVRON_RIGHT, size = 20.dp, tint = Ink.copy(alpha = 0.4f))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EventCard(
    event: SocialEvent,
    metCount: Int,
    spheres: List<Sphere>,
    selected: Boolean,
    onClick: () -> Unit
) {
    val eventColor = parseHexColor(event.color)
    val tags = remember(spheres, event.sphereIds) {
        spheres.filter { event.sphereIds.contains(it.id) }
    }

    PixelPanel(
        modifier = Modifier.selectedOutline(selected),
        color = eventColor,
        raised = true,
        onClick = onClick
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
                    text = event.name,
                    style = TextStyle(
                        fontFamily = PixelFontFamily(),
                        fontSize = 18.sp,
                        lineHeight = 22.sp,
                        color = Ink
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${formatDay(event.date)} · $metCount met",
                    style = TextStyle(
                        fontFamily = SansFontFamily(),
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = Ink.copy(alpha = 0.6f)
                    )
                )
                if (tags.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        for (t in tags) {
                            val c = parseHexColor(t.color)
                            Box(
                                modifier = Modifier
                                    .height(20.dp)
                                    .clip(PxClipShape)
                                    .background(tintColor(c, 60))
                                    .padding(horizontal = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = t.name,
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
            }

            PixelIcon(name = IconName.CHEVRON_RIGHT, size = 20.dp, tint = Ink.copy(alpha = 0.4f))
        }
    }
}
