package de.koenidv.spheres.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.koenidv.spheres.data.SheetState
import de.koenidv.spheres.data.SpheresStore
import de.koenidv.spheres.domain.contactScore
import de.koenidv.spheres.domain.dueInfo
import de.koenidv.spheres.domain.entriesByPerson
import de.koenidv.spheres.domain.funFact
import de.koenidv.spheres.domain.inSphere
import de.koenidv.spheres.domain.intervalInfo
import de.koenidv.spheres.domain.relationshipQuality
import de.koenidv.spheres.ui.components.DueStatus
import de.koenidv.spheres.ui.components.IconName
import de.koenidv.spheres.ui.components.PixelIconButton
import de.koenidv.spheres.ui.components.PersonMenu
import de.koenidv.spheres.ui.components.PersonMenuTarget
import de.koenidv.spheres.ui.components.PxClipShape
import de.koenidv.spheres.domain.Quality
import de.koenidv.spheres.ui.components.Radar
import de.koenidv.spheres.ui.components.RadarPerson
import de.koenidv.spheres.ui.components.rememberLiveNow
import de.koenidv.spheres.ui.components.ScreenHeader
import de.koenidv.spheres.ui.theme.FieldBg
import de.koenidv.spheres.ui.theme.Ink
import de.koenidv.spheres.ui.theme.PixelFontFamily
import de.koenidv.spheres.ui.theme.parseHexColor

@Composable
fun RadarScreen(
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by SpheresStore.state.collectAsState()
    val spheres = state.spheres
    val people = state.people
    val entries = state.entries
    val events = state.events

    var filterSphereId by remember { mutableStateOf<String?>(null) }
    var menuTarget by remember { mutableStateOf<PersonMenuTarget?>(null) }

    val tracked = remember(people) { people.filter { it.intervalDays != null } }
    val fact = remember(tracked) { funFact(tracked) }
    val filterSphere = remember(spheres, filterSphereId) { spheres.firstOrNull { it.id == filterSphereId } }
    val shownPeople = remember(tracked, filterSphere, events) {
        if (filterSphere != null) {
            tracked.filter { inSphere(it, filterSphere.id, events) }
        } else {
            tracked
        }
    }

    val byPerson = remember(entries) { entriesByPerson(entries) }
    val now = rememberLiveNow()

    val radarPeople = remember(shownPeople, byPerson, now) {
        shownPeople.map { p ->
            val pEntries = byPerson[p.id]
            val cs = contactScore(p, pEntries, now)?.score ?: 100
            val di = dueInfo(p, pEntries, now)
            val st = when (di?.status) {
                de.koenidv.spheres.domain.DueStatus.OVERDUE -> DueStatus.OVERDUE
                de.koenidv.spheres.domain.DueStatus.SOON -> DueStatus.SOON
                else -> DueStatus.OK
            }
            val rq = relationshipQuality(pEntries)?.quality
            val q = when (rq) {
                de.koenidv.spheres.domain.Quality.BAD -> Quality.BAD
                de.koenidv.spheres.domain.Quality.REGULAR -> Quality.REGULAR
                de.koenidv.spheres.domain.Quality.GOOD -> Quality.GOOD
                de.koenidv.spheres.domain.Quality.AMAZING -> Quality.AMAZING
                null -> null
            }
            RadarPerson(
                id = p.id,
                name = p.name,
                intervalDays = p.intervalDays ?: 30,
                score = cs,
                status = st,
                quality = q,
                avatar = p.avatar,
                photoUrl = p.photoUrl
            )
        }
    }

    val subtitle = if (tracked.isEmpty()) {
        "Your radar is empty for now. Tap a + to add the first connection you want to stay in touch with."
    } else {
        val countText = "You're staying in touch with ${tracked.size} ${if (tracked.size == 1) "person" else "people"}!"
        if (fact != null) {
            "$countText\nFun fact: ${fact.label} ${fact.value}."
        } else {
            countText
        }
    }

    val targetPerson = remember(menuTarget, people) {
        people.firstOrNull { it.id == menuTarget?.id }
    }
    val targetPersonColor = remember(targetPerson) {
        intervalInfo(targetPerson?.intervalDays).color
    }

    val density = LocalDensity.current.density
    val scrollState = rememberScrollState()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        val minRadarHeightDp = (maxHeight.value - 160f).coerceAtLeast(360f)
        val viewportHeightDp = maxHeight.value
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .statusBarsPadding()
        ) {
            ScreenHeader(
                title = "Connections",
                subtitle = subtitle,
                right = {
                    PixelIconButton(
                        icon = IconName.SETTINGS,
                        onClick = { onNavigate("/settings") },
                        tint = Ink.copy(alpha = 0.4f)
                    )
                }
            )

            if (spheres.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val allSelected = filterSphereId == null
                    Box(
                        modifier = Modifier
                            .height(32.dp)
                            .clip(PxClipShape)
                            .background(if (allSelected) Ink else FieldBg)
                            .clickable { filterSphereId = null }
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "All",
                            style = TextStyle(
                                fontFamily = PixelFontFamily(),
                                fontSize = 14.sp,
                                color = if (allSelected) Color.White else Ink.copy(alpha = 0.7f)
                            )
                        )
                    }

                    for (s in spheres) {
                        val on = filterSphereId == s.id
                        val color = parseHexColor(s.color)
                        Row(
                            modifier = Modifier
                                .height(32.dp)
                                .clip(PxClipShape)
                                .background(if (on) Ink else FieldBg)
                                .clickable { filterSphereId = s.id }
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(color)
                            )
                            Text(
                                text = s.name,
                                style = TextStyle(
                                    fontFamily = PixelFontFamily(),
                                    fontSize = 14.sp,
                                    color = if (on) Color.White else Ink.copy(alpha = 0.7f)
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Radar(
                people = radarPeople,
                onPersonClick = { id -> onNavigate("/people/$id") },
                onPersonLongPress = { id, x, y, w, h ->
                    menuTarget = PersonMenuTarget(id, x, y, w, h)
                },
                onAddClick = { intervalDays ->
                    SpheresStore.openSheet(
                        SheetState.AddPerson(
                            intervalDays = intervalDays,
                            sphereId = filterSphere?.id
                        )
                    )
                },
                minHeightDp = minRadarHeightDp,
                scrollOffsetDp = { scrollState.value / density },
                viewportHeightDp = viewportHeightDp,
                modifier = Modifier.fillMaxWidth()
            )
        }

        PersonMenu(
            target = menuTarget,
            personName = targetPerson?.name ?: "",
            colorHex = targetPersonColor,
            avatar = targetPerson?.avatar,
            photoUrl = targetPerson?.photoUrl,
            onClose = { menuTarget = null },
            onCheckIn = { id ->
                SpheresStore.quickCheckIn(id)
                menuTarget = null
            },
            onLogInteraction = { id ->
                menuTarget = null
                SpheresStore.openSheet(SheetState.Log(id, de.koenidv.spheres.data.LogMode.INTERACTION))
            },
            onAddNote = { id ->
                menuTarget = null
                SpheresStore.openSheet(SheetState.Log(id, de.koenidv.spheres.data.LogMode.NOTE))
            },
            onSnooze = { id, days ->
                SpheresStore.snooze(id, days)
                menuTarget = null
            },
            onOpen = { id ->
                menuTarget = null
                onNavigate("/people/$id")
            }
        )
    }
}
