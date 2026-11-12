package de.koenidv.spheres.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.koenidv.spheres.ui.theme.Ink
import de.koenidv.spheres.ui.theme.Muted
import de.koenidv.spheres.ui.theme.PixelFontFamily
import de.koenidv.spheres.ui.theme.Scrim
import kotlin.math.roundToInt

data class PersonMenuTarget(
    val id: String,
    val anchorX: Float,
    val anchorY: Float,
    val anchorWidth: Float,
    val anchorHeight: Float
)

private const val MENU_WIDTH = 232f
private const val MENU_GAP = 8f

@Composable
fun PersonMenu(
    target: PersonMenuTarget?,
    personName: String,
    colorHex: String,
    onClose: () -> Unit,
    onCheckIn: (String) -> Unit,
    onLogInteraction: (String) -> Unit,
    onAddNote: (String) -> Unit,
    onSnooze: (String, Int) -> Unit,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = target != null,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        if (target != null) {
            BoxWithConstraints(
                modifier = modifier
                    .fillMaxSize()
                    .background(Scrim.copy(alpha = 0.7f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClose
                    )
            ) {
                val density = androidx.compose.ui.platform.LocalDensity.current.density
                val screenW = maxWidth.value
                val screenH = maxHeight.value
                val anchorXDp = target.anchorX / density
                val anchorYDp = target.anchorY / density
                val anchorWDp = target.anchorWidth / density
                val anchorHDp = target.anchorHeight / density

                val menuLeft = (anchorXDp + anchorWDp / 2f - MENU_WIDTH / 2f)
                    .coerceIn(12f, (screenW - MENU_WIDTH - 12f).coerceAtLeast(12f))
                val below = (anchorYDp + anchorHDp + 300f < screenH)
                val menuTop = if (below) {
                    anchorYDp + anchorHDp + MENU_GAP
                } else {
                    (anchorYDp - 260f - MENU_GAP).coerceAtLeast(12f)
                }

                Box(
                    modifier = Modifier
                        .offset(x = anchorXDp.dp, y = (anchorYDp - 4f).dp)
                        .width(anchorWDp.dp),
                    contentAlignment = Alignment.Center
                ) {
                    PixelAvatar(
                        name = personName,
                        colorHex = colorHex,
                        size = RADAR_AVATAR_SIZE
                    )
                }

                AnimatedVisibility(
                    visible = true,
                    enter = slideInVertically { if (below) -20 else 20 } + fadeIn(),
                    exit = slideOutVertically { if (below) -20 else 20 } + fadeOut(),
                    modifier = Modifier.offset(x = menuLeft.dp, y = menuTop.dp)
                ) {
                    PixelPanel(
                        tone = PanelTone.WHITE,
                        modifier = Modifier
                            .width(MENU_WIDTH.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {}
                            )
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = personName,
                                style = TextStyle(
                                    fontFamily = PixelFontFamily(),
                                    fontSize = 16.sp,
                                    color = Muted
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )

                            MenuItem(
                                icon = IconName.CHECK,
                                label = "Check in",
                                onClick = {
                                    onClose()
                                    onCheckIn(target.id)
                                }
                            )

                            MenuItem(
                                icon = IconName.CHAT,
                                label = "Log interaction…",
                                onClick = {
                                    onClose()
                                    onLogInteraction(target.id)
                                }
                            )

                            MenuItem(
                                icon = IconName.NOTE,
                                label = "Add note…",
                                onClick = {
                                    onClose()
                                    onAddNote(target.id)
                                }
                            )

                            MenuItem(
                                icon = IconName.SNOOZE,
                                label = "Snooze 3 days",
                                onClick = {
                                    onClose()
                                    onSnooze(target.id, 3)
                                }
                            )

                            MenuItem(
                                icon = IconName.USER,
                                label = "Open",
                                onClick = {
                                    onClose()
                                    onOpen(target.id)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuItem(
    icon: IconName,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PixelIcon(name = icon, size = 20.dp, tint = Ink)
        Text(
            text = label,
            style = TextStyle(
                fontFamily = PixelFontFamily(),
                fontSize = 16.sp,
                color = Ink
            ),
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}
