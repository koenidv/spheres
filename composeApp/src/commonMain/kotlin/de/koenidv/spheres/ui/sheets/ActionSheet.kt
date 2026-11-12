package de.koenidv.spheres.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.koenidv.spheres.data.LogMode
import de.koenidv.spheres.data.SheetState
import de.koenidv.spheres.data.SpheresStore
import de.koenidv.spheres.domain.contactScore
import de.koenidv.spheres.domain.entriesByPerson
import de.koenidv.spheres.domain.firstName
import de.koenidv.spheres.domain.intervalInfo
import de.koenidv.spheres.ui.components.IconName
import de.koenidv.spheres.ui.components.PixelAvatar
import de.koenidv.spheres.ui.components.PixelIcon
import de.koenidv.spheres.ui.components.PixelPanel
import de.koenidv.spheres.ui.theme.Ink
import de.koenidv.spheres.ui.theme.Line
import de.koenidv.spheres.ui.theme.MonthlyPurple
import de.koenidv.spheres.ui.theme.Muted
import de.koenidv.spheres.ui.theme.PixelFontFamily
import de.koenidv.spheres.ui.theme.SansFontFamily
import de.koenidv.spheres.ui.theme.WeeklyPink

@Composable
fun ActionSheet(
    onNavigate: (String) -> Unit
) {
    val state by SpheresStore.state.collectAsState()
    val people = state.people
    val entries = state.entries

    val quick = remember(people, entries) {
        val by = entriesByPerson(entries)
        people
            .mapNotNull { p ->
                val cs = contactScore(p, by[p.id]) ?: return@mapNotNull null
                p to cs
            }
            .sortedByDescending { it.second.priority }
            .take(5)
            .map { it.first }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                PixelPanel(
                    color = WeeklyPink,
                    raised = true,
                    onClick = {
                        SpheresStore.openSheet(SheetState.PickPerson(LogMode.INTERACTION))
                    }
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        PixelIcon(name = IconName.CHAT, size = 44.dp, tint = Ink)
                        Column {
                            Text(
                                text = "Interaction",
                                style = TextStyle(
                                    fontFamily = PixelFontFamily(),
                                    fontSize = 18.sp,
                                    lineHeight = 24.sp,
                                    color = Ink
                                )
                            )
                            Text(
                                text = "Log a catch-up",
                                style = TextStyle(
                                    fontFamily = SansFontFamily(),
                                    fontSize = 13.sp,
                                    lineHeight = 20.sp,
                                    color = Ink.copy(alpha = 0.6f)
                                )
                            )
                        }
                    }
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                PixelPanel(
                    color = MonthlyPurple,
                    raised = true,
                    onClick = {
                        SpheresStore.openSheet(SheetState.PickPerson(LogMode.NOTE))
                    }
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        PixelIcon(name = IconName.NOTE, size = 44.dp, tint = Ink)
                        Column {
                            Text(
                                text = "Note",
                                style = TextStyle(
                                    fontFamily = PixelFontFamily(),
                                    fontSize = 18.sp,
                                    lineHeight = 24.sp,
                                    color = Ink
                                )
                            )
                            Text(
                                text = "Jot something down",
                                style = TextStyle(
                                    fontFamily = SansFontFamily(),
                                    fontSize = 13.sp,
                                    lineHeight = 20.sp,
                                    color = Ink.copy(alpha = 0.6f)
                                )
                            )
                        }
                    }
                }
            }
        }

        if (quick.isNotEmpty()) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "QUICK LOG",
                style = TextStyle(
                    fontFamily = PixelFontFamily(),
                    fontSize = 14.sp,
                    letterSpacing = 0.05.sp,
                    color = Muted
                ),
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                quick.forEach { person ->
                    Column(
                        modifier = Modifier
                            .width(64.dp)
                            .clickable {
                                SpheresStore.openSheet(
                                    SheetState.Log(
                                        personId = person.id,
                                        mode = LogMode.INTERACTION
                                    )
                                )
                            }
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        PixelAvatar(
                            name = person.name,
                            colorHex = intervalInfo(person.intervalDays).color,
                            size = 48.dp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = firstName(person.name),
                            style = TextStyle(
                                fontFamily = PixelFontFamily(),
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                color = Ink,
                                textAlign = TextAlign.Center
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Line)
        )

        ActionRow(
            icon = IconName.USER,
            label = "Add connection",
            onClick = { SpheresStore.openSheet(SheetState.AddPerson()) }
        )

        ActionRow(
            icon = IconName.USERS,
            label = "Create sphere",
            onClick = { SpheresStore.openSheet(SheetState.SphereEdit(null)) }
        )

        ActionRow(
            icon = IconName.PARTY,
            label = "Start event",
            onClick = {
                val id = SpheresStore.addEvent()
                SpheresStore.closeSheet()
                onNavigate("/events/$id")
            }
        )

        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .height(44.dp)
                    .clickable { SpheresStore.closeSheet() }
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Cancel",
                    style = TextStyle(
                        fontFamily = PixelFontFamily(),
                        fontSize = 16.sp,
                        color = Muted
                    )
                )
            }
        }
    }
}

@Composable
private fun ActionRow(
    icon: IconName,
    label: String,
    onClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clickable(onClick = onClick)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PixelIcon(name = icon, size = 24.dp, tint = Ink)
            Text(
                text = label,
                style = TextStyle(
                    fontFamily = PixelFontFamily(),
                    fontSize = 18.sp,
                    color = Ink
                ),
                modifier = Modifier.weight(1f)
            )
            PixelIcon(
                name = IconName.CHEVRON_RIGHT,
                size = 20.dp,
                tint = Ink.copy(alpha = 0.3f)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Line)
        )
    }
}
