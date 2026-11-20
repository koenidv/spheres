package de.koenidv.spheres.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.koenidv.spheres.ui.theme.Coral
import de.koenidv.spheres.ui.theme.Ink
import de.koenidv.spheres.ui.theme.Line
import de.koenidv.spheres.ui.theme.PixelFontFamily

enum class BottomNavTab(val label: String, val icon: IconName) {
    RADAR("Radar", IconName.RADAR),
    DUE("Due", IconName.BELL),
    SPHERES("Spheres", IconName.USERS),
    MAP("Map", IconName.MAP)
}

@Composable
fun BottomNav(
    selectedTab: BottomNavTab?,
    onTabSelect: (BottomNavTab) -> Unit,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier,
    overdueCount: Int = 0,
    onTabReselect: ((BottomNavTab) -> Unit)? = null
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Line)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                verticalAlignment = Alignment.Top
            ) {
                NavTabItem(
                    tab = BottomNavTab.RADAR,
                    selected = selectedTab == BottomNavTab.RADAR,
                    onClick = {
                        if (selectedTab == BottomNavTab.RADAR) onTabReselect?.invoke(BottomNavTab.RADAR)
                        else onTabSelect(BottomNavTab.RADAR)
                    },
                    modifier = Modifier.weight(1f)
                )

                NavTabItem(
                    tab = BottomNavTab.DUE,
                    selected = selectedTab == BottomNavTab.DUE,
                    badgeCount = overdueCount,
                    onClick = {
                        if (selectedTab == BottomNavTab.DUE) onTabReselect?.invoke(BottomNavTab.DUE)
                        else onTabSelect(BottomNavTab.DUE)
                    },
                    modifier = Modifier.weight(1f)
                )

                Box(
                    modifier = Modifier.width(72.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    val addInteraction = remember { MutableInteractionSource() }
                    val isAddPressed by addInteraction.collectIsPressedAsState()

                    PixelShell(
                        modifier = Modifier
                            .offset(y = (-20).dp)
                            .size(56.dp)
                            .clickable(
                                interactionSource = addInteraction,
                                indication = null,
                                onClick = onAddClick
                            ),
                        fill = Ink,
                        shadow = Color(0xFF8A8A8A),
                        lift = 4.dp,
                        isPressed = isAddPressed
                    ) {
                        PixelIcon(name = IconName.PLUS, size = 28.dp, tint = Color.White)
                    }
                }

                NavTabItem(
                    tab = BottomNavTab.SPHERES,
                    selected = selectedTab == BottomNavTab.SPHERES,
                    onClick = {
                        if (selectedTab == BottomNavTab.SPHERES) onTabReselect?.invoke(BottomNavTab.SPHERES)
                        else onTabSelect(BottomNavTab.SPHERES)
                    },
                    modifier = Modifier.weight(1f)
                )

                NavTabItem(
                    tab = BottomNavTab.MAP,
                    selected = selectedTab == BottomNavTab.MAP,
                    onClick = {
                        if (selectedTab == BottomNavTab.MAP) onTabReselect?.invoke(BottomNavTab.MAP)
                        else onTabSelect(BottomNavTab.MAP)
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun NavTabItem(
    tab: BottomNavTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badgeCount: Int = 0
) {
    val color = if (selected) Ink else Color(0xFF9A9A9A)

    Column(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(top = 12.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box {
            PixelIcon(name = tab.icon, size = 24.dp, tint = color)
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
            text = tab.label,
            style = TextStyle(
                fontFamily = PixelFontFamily(),
                fontSize = 12.sp,
                color = color
            ),
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
