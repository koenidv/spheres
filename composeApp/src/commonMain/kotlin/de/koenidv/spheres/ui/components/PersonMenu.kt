package de.koenidv.spheres.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.koenidv.spheres.domain.AvatarConfig
import de.koenidv.spheres.platform.PlatformBackHandler
import de.koenidv.spheres.ui.theme.Ink
import de.koenidv.spheres.ui.theme.Muted
import de.koenidv.spheres.ui.theme.PixelFontFamily
import de.koenidv.spheres.ui.theme.Scrim

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
    avatar: AvatarConfig? = null,
    photoUrl: String? = null,
    onClose: () -> Unit,
    onCheckIn: (String) -> Unit,
    onLogInteraction: (String) -> Unit,
    onAddNote: (String) -> Unit,
    onSnooze: (String, Int) -> Unit,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var activeTarget by remember { mutableStateOf(target) }
    var activeName by remember { mutableStateOf(personName) }
    var activeColor by remember { mutableStateOf(colorHex) }
    var activeAvatar by remember { mutableStateOf(avatar) }
    var activePhotoUrl by remember { mutableStateOf(photoUrl) }

    if (target != null) {
        activeTarget = target
        activeName = personName
        activeColor = colorHex
        activeAvatar = avatar
        activePhotoUrl = photoUrl
    }

    PlatformBackHandler(enabled = target != null, onBack = onClose)

    AnimatedVisibility(
        visible = target != null,
        enter = EnterTransition.None,
        exit = ExitTransition.None
    ) {
        val currentTarget = activeTarget
        if (currentTarget != null) {
            val avatarOffsetY by transition.animateDp(
                transitionSpec = { spring(stiffness = 580f, dampingRatio = 0.82f) },
                label = "avatarOffset"
            ) { state ->
                if (state == EnterExitState.Visible) (-4).dp else 0.dp
            }
            val avatarScale by transition.animateFloat(
                transitionSpec = { spring(stiffness = 580f, dampingRatio = 0.82f) },
                label = "avatarScale"
            ) { state ->
                if (state == EnterExitState.Visible) 1.04f else 1.0f
            }

            BoxWithConstraints(
                modifier = modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .animateEnterExit(
                            enter = fadeIn(tween(150)),
                            exit = fadeOut(tween(130))
                        )
                        .background(Scrim.copy(alpha = 0.7f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onClose
                        )
                )

                val density = androidx.compose.ui.platform.LocalDensity.current.density
                val screenW = maxWidth.value
                val screenH = maxHeight.value
                val anchorXDp = currentTarget.anchorX / density
                val anchorYDp = currentTarget.anchorY / density
                val anchorWDp = currentTarget.anchorWidth / density
                val anchorHDp = currentTarget.anchorHeight / density

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
                        .offset(x = anchorXDp.dp, y = anchorYDp.dp + avatarOffsetY)
                        .width(anchorWDp.dp)
                        .graphicsLayer {
                            scaleX = avatarScale
                            scaleY = avatarScale
                        },
                    contentAlignment = Alignment.Center
                ) {
                    AvatarRender(
                        name = activeName,
                        colorHex = activeColor,
                        config = activeAvatar,
                        photoUrl = activePhotoUrl,
                        size = RADAR_AVATAR_SIZE
                    )
                }

                Box(
                    modifier = Modifier
                        .offset(x = menuLeft.dp, y = menuTop.dp)
                        .animateEnterExit(
                            enter = fadeIn(tween(130)) +
                                scaleIn(
                                    initialScale = 0.90f,
                                    transformOrigin = TransformOrigin(0.5f, if (below) 0f else 1f),
                                    animationSpec = spring(stiffness = 620f, dampingRatio = 0.82f)
                                ) +
                                slideInVertically(
                                    animationSpec = spring(stiffness = 620f, dampingRatio = 0.82f)
                                ) { if (below) -16 else 16 },
                            exit = fadeOut(tween(110)) +
                                scaleOut(
                                    targetScale = 0.92f,
                                    transformOrigin = TransformOrigin(0.5f, if (below) 0f else 1f),
                                    animationSpec = tween(120)
                                )
                        )
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
                                text = activeName,
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
                                    onCheckIn(currentTarget.id)
                                }
                            )

                            MenuItem(
                                icon = IconName.CHAT,
                                label = "Log interaction…",
                                onClick = {
                                    onClose()
                                    onLogInteraction(currentTarget.id)
                                }
                            )

                            MenuItem(
                                icon = IconName.NOTE,
                                label = "Add note…",
                                onClick = {
                                    onClose()
                                    onAddNote(currentTarget.id)
                                }
                            )

                            MenuItem(
                                icon = IconName.SNOOZE,
                                label = "Snooze 3 days",
                                onClick = {
                                    onClose()
                                    onSnooze(currentTarget.id, 3)
                                }
                            )

                            MenuItem(
                                icon = IconName.USER,
                                label = "Open",
                                onClick = {
                                    onClose()
                                    onOpen(currentTarget.id)
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
