package de.koenidv.spheres.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.koenidv.spheres.domain.Sphere
import de.koenidv.spheres.ui.theme.CardBg
import de.koenidv.spheres.ui.theme.FieldBg
import de.koenidv.spheres.ui.theme.SansFontFamily
import de.koenidv.spheres.ui.theme.Coral
import de.koenidv.spheres.ui.theme.Ink
import de.koenidv.spheres.ui.theme.Line
import de.koenidv.spheres.ui.theme.Muted
import de.koenidv.spheres.ui.theme.PixelFontFamily
import de.koenidv.spheres.ui.theme.parseHexColor

private val InactiveTint = Color(0xFF9A9A9A)

@Composable
fun NavRail(
    selectedTab: BottomNavTab?,
    searchSelected: Boolean,
    settingsSelected: Boolean,
    onTabSelect: (BottomNavTab) -> Unit,
    onAddClick: () -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    overdueCount: Int = 0
) {
    Row(modifier = modifier.fillMaxHeight().background(Color.White)) {
        Column(
            modifier = Modifier
                .width(80.dp)
                .fillMaxHeight()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(start = 8.dp, end = 8.dp, top = 16.dp, bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            RailItem(
                icon = IconName.SEARCH,
                label = "Search",
                selected = searchSelected,
                onClick = onSearchClick,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            val addInteraction = remember { MutableInteractionSource() }
            val isAddPressed by addInteraction.collectIsPressedAsState()
            PixelShell(
                modifier = Modifier
                    .size(56.dp)
                    .clickable(interactionSource = addInteraction, indication = null, onClick = onAddClick),
                fill = Ink,
                shadow = Color(0xFF8A8A8A),
                lift = 4.dp,
                isPressed = isAddPressed
            ) {
                PixelIcon(name = IconName.PLUS, size = 28.dp, tint = Color.White)
            }

            Spacer(Modifier.height(16.dp))
            Box(Modifier.width(24.dp).height(1.dp).background(Line))
            Spacer(Modifier.height(12.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                BottomNavTab.entries.forEach { tab ->
                    RailItem(
                        icon = tab.icon,
                        label = tab.label,
                        selected = selectedTab == tab,
                        badgeCount = if (tab == BottomNavTab.DUE) overdueCount else 0,
                        onClick = { onTabSelect(tab) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            RailItem(
                icon = IconName.SETTINGS,
                label = "Settings",
                selected = settingsSelected,
                onClick = onSettingsClick,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(Line))
    }
}

@Composable
private fun RailItem(
    icon: IconName,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badgeCount: Int = 0
) {
    val color = if (selected) Ink else InactiveTint
    Column(
        modifier = modifier
            .height(60.dp)
            .clip(PxClipShape)
            .then(if (selected) Modifier.background(CardBg) else Modifier)
            .hoverHighlight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box {
            PixelIcon(name = icon, size = 24.dp, tint = color)
            if (badgeCount > 0) {
                Box(
                    modifier = Modifier
                        .offset(x = 12.dp, y = (-6).dp)
                        .clip(PxClipShape)
                        .background(Coral)
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (badgeCount > 99) "99+" else badgeCount.toString(),
                        style = TextStyle(
                            fontFamily = PixelFontFamily(),
                            fontSize = 11.sp,
                            lineHeight = 11.sp,
                            color = Color.White
                        )
                    )
                }
            }
        }
        Text(
            text = label,
            style = TextStyle(fontFamily = PixelFontFamily(), fontSize = 12.sp, color = color),
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
fun NavSidebar(
    selectedTab: BottomNavTab?,
    selectedPath: String,
    searchSelected: Boolean,
    settingsSelected: Boolean,
    spheres: List<Sphere>,
    onTabSelect: (BottomNavTab) -> Unit,
    onAddClick: () -> Unit,
    onSearchClick: () -> Unit,
    onSphereClick: (String) -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    overdueCount: Int = 0
) {
    Row(modifier = modifier.fillMaxHeight().background(Color.White)) {
        Column(
            modifier = Modifier
                .width(240.dp)
                .fillMaxHeight()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(PxClipShape)
                    .background(if (searchSelected) CardBg else FieldBg)
                    .hoverHighlight()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onSearchClick
                    )
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PixelIcon(name = IconName.SEARCH, size = 20.dp, tint = Muted)
                Text(
                    text = "Search",
                    style = TextStyle(fontFamily = PixelFontFamily(), fontSize = 16.sp, color = Muted),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "\u2318F",
                    style = TextStyle(fontFamily = SansFontFamily(), fontSize = 13.sp, color = Muted.copy(alpha = 0.7f))
                )
            }

            Spacer(Modifier.height(12.dp))

            val addInteraction = remember { MutableInteractionSource() }
            val isAddPressed by addInteraction.collectIsPressedAsState()
            PixelShell(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clickable(interactionSource = addInteraction, indication = null, onClick = onAddClick),
                fill = Ink,
                shadow = Color(0xFF8A8A8A),
                lift = 4.dp,
                isPressed = isAddPressed
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PixelIcon(name = IconName.PLUS, size = 20.dp, tint = Color.White)
                    Text(
                        text = "New",
                        style = TextStyle(fontFamily = PixelFontFamily(), fontSize = 18.sp, color = Color.White)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            BottomNavTab.entries.forEach { tab ->
                SidebarRow(
                    selected = selectedTab == tab,
                    onClick = { onTabSelect(tab) }
                ) { color ->
                    PixelIcon(name = tab.icon, size = 24.dp, tint = color)
                    Text(
                        text = tab.label,
                        style = TextStyle(fontFamily = PixelFontFamily(), fontSize = 16.sp, color = color),
                        modifier = Modifier.weight(1f)
                    )
                    if (tab == BottomNavTab.DUE && overdueCount > 0) {
                        Box(
                            modifier = Modifier
                                .clip(PxClipShape)
                                .background(Coral)
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (overdueCount > 99) "99+" else overdueCount.toString(),
                                style = TextStyle(
                                    fontFamily = PixelFontFamily(),
                                    fontSize = 11.sp,
                                    lineHeight = 11.sp,
                                    color = Color.White
                                )
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                if (spheres.isNotEmpty()) {
                    Text(
                        text = "Spheres",
                        style = TextStyle(fontFamily = PixelFontFamily(), fontSize = 14.sp, color = Muted),
                        modifier = Modifier.padding(start = 12.dp, top = 20.dp, bottom = 6.dp)
                    )
                    spheres.forEach { sphere ->
                        val route = "/spheres/${sphere.id}"
                        SidebarRow(
                            selected = selectedPath == route,
                            onClick = { onSphereClick(route) },
                            height = 40.dp
                        ) { color ->
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(PxClipShape)
                                    .background(parseHexColor(sphere.color))
                            )
                            Text(
                                text = sphere.name,
                                style = TextStyle(fontFamily = PixelFontFamily(), fontSize = 16.sp, color = color),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            SidebarRow(selected = settingsSelected, onClick = onSettingsClick) { color ->
                PixelIcon(name = IconName.SETTINGS, size = 24.dp, tint = color)
                Text(
                    text = "Settings",
                    style = TextStyle(fontFamily = PixelFontFamily(), fontSize = 16.sp, color = color)
                )
            }
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(Line))
    }
}

@Composable
private fun SidebarRow(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 48.dp,
    content: @Composable RowScope.(Color) -> Unit
) {
    val color = if (selected) Ink else InactiveTint
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(PxClipShape)
            .then(if (selected) Modifier.background(CardBg) else Modifier)
            .hoverHighlight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        content(color)
    }
}
