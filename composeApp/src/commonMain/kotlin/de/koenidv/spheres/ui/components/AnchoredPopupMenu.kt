package de.koenidv.spheres.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.koenidv.spheres.platform.PlatformBackHandler
import de.koenidv.spheres.ui.theme.Ink
import de.koenidv.spheres.ui.theme.Muted
import de.koenidv.spheres.ui.theme.PixelFontFamily
import de.koenidv.spheres.ui.theme.Scrim

data class PopupAnchor(
    val anchorX: Float,
    val anchorY: Float,
    val anchorWidth: Float,
    val anchorHeight: Float,
    val centerHorizontally: Boolean = false,
)

data class PopupMenuOption(
    val key: String?,
    val label: String,
    val color: Color? = null,
    val icon: IconName? = null,
    val selected: Boolean = false,
)

private const val POPUP_MENU_WIDTH = 240f
private const val POPUP_MENU_GAP = 8f

@Composable
fun AnchoredPopupMenu(
    anchor: PopupAnchor?,
    title: String,
    options: List<PopupMenuOption>,
    onSelect: (String?) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var activeAnchor by remember { mutableStateOf(anchor) }
    var activeTitle by remember { mutableStateOf(title) }
    var activeOptions by remember { mutableStateOf(options) }

    if (anchor != null) {
        activeAnchor = anchor
        activeTitle = title
        activeOptions = options
    }

    PlatformBackHandler(enabled = anchor != null, onBack = onClose)

    AnimatedVisibility(
        visible = anchor != null,
        enter = EnterTransition.None,
        exit = ExitTransition.None,
        modifier = modifier
    ) {
        val current = activeAnchor
        if (current != null) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
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

                val density = LocalDensity.current.density
                val screenW = maxWidth.value
                val anchorXDp = current.anchorX / density
                val anchorYDp = current.anchorY / density
                val anchorWDp = current.anchorWidth / density
                val anchorHDp = current.anchorHeight / density

                val rawLeft = if (current.centerHorizontally) {
                    anchorXDp + anchorWDp / 2f - POPUP_MENU_WIDTH / 2f
                } else {
                    anchorXDp
                }
                val menuLeft = rawLeft.coerceIn(12f, (screenW - POPUP_MENU_WIDTH - 12f).coerceAtLeast(12f))
                val menuTop = anchorYDp + anchorHDp + POPUP_MENU_GAP

                Box(
                    modifier = Modifier
                        .offset(x = menuLeft.dp, y = menuTop.dp)
                        .animateEnterExit(
                            enter = fadeIn(tween(130)) +
                                scaleIn(
                                    initialScale = 0.90f,
                                    transformOrigin = TransformOrigin(0.5f, 0f),
                                    animationSpec = spring(stiffness = 620f, dampingRatio = 0.82f)
                                ) +
                                slideInVertically(
                                    animationSpec = spring(stiffness = 620f, dampingRatio = 0.82f)
                                ) { -16 },
                            exit = fadeOut(tween(110)) +
                                scaleOut(
                                    targetScale = 0.92f,
                                    transformOrigin = TransformOrigin(0.5f, 0f),
                                    animationSpec = tween(120)
                                )
                        )
                ) {
                    PixelPanel(
                        tone = PanelTone.WHITE,
                        modifier = Modifier
                            .width(POPUP_MENU_WIDTH.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {}
                            )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 320.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = activeTitle,
                                style = TextStyle(
                                    fontFamily = PixelFontFamily(),
                                    fontSize = 15.sp,
                                    color = Muted
                                ),
                                modifier = Modifier.padding(bottom = 6.dp)
                            )

                            activeOptions.forEach { option ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(42.dp)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = { onSelect(option.key) }
                                        ),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    if (option.icon != null) {
                                        PixelIcon(
                                            name = option.icon,
                                            size = 10.dp,
                                            tint = option.color ?: Muted
                                        )
                                    } else if (option.color != null) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(PxClipShape)
                                                .background(option.color)
                                        )
                                    }
                                    Text(
                                        text = option.label,
                                        style = TextStyle(
                                            fontFamily = PixelFontFamily(),
                                            fontSize = 16.sp,
                                            color = Ink
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (option.selected) {
                                        PixelIcon(name = IconName.CHECK, size = 18.dp, tint = Ink)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
