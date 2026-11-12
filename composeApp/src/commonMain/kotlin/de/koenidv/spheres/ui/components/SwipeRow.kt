package de.koenidv.spheres.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.koenidv.spheres.ui.theme.PixelFontFamily
import de.koenidv.spheres.ui.theme.parseHexColor
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

data class SwipeAction(
    val label: String,
    val icon: IconName,
    val colorHex: String,
    val run: () -> Unit
)

private val THRESHOLD = 88.dp
private val MAX_DRAG = 160.dp

@Composable
fun SwipeRow(
    modifier: Modifier = Modifier,
    left: SwipeAction? = null,
    right: SwipeAction? = null,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    val thresholdPx = with(density) { THRESHOLD.toPx() }
    val maxDragPx = with(density) { MAX_DRAG.toPx() }

    val offsetX = remember { Animatable(0f) }
    var armed by remember { mutableStateOf(false) }

    val currentOffset = offsetX.value
    val side = when {
        currentOffset > 0 -> "right"
        currentOffset < 0 -> "left"
        else -> null
    }
    val action = when (side) {
        "right" -> right
        "left" -> left
        else -> null
    }

    val iconScale = min(1f, 0.6f + (abs(currentOffset) / thresholdPx) * 0.4f)
    val actionColor = if (action != null) parseHexColor(action.colorHex) else Color(0xFFECECEC)
    val bgColor = if (armed) actionColor else Color(0xFFECECEC)
    val fgColor = if (armed) Color.White else Color(0xFF8A8A8A)

    Box(modifier = modifier.fillMaxWidth()) {
        if (action != null) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(PanelClipShape)
                    .background(bgColor)
                    .padding(horizontal = 20.dp),
                contentAlignment = if (side == "right") Alignment.CenterStart else Alignment.CenterEnd
            ) {
                Row(
                    modifier = Modifier.scale(iconScale),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PixelIcon(name = action.icon, size = 22.dp, tint = fgColor)
                    Text(
                        text = action.label,
                        style = TextStyle(
                            fontFamily = PixelFontFamily(),
                            fontSize = 15.sp,
                            color = fgColor
                        ),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(currentOffset.roundToInt(), 0) }
                .pointerInput(left, right) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val v = offsetX.value
                            val hit = when {
                                v >= thresholdPx -> right
                                v <= -thresholdPx -> left
                                else -> null
                            }
                            coroutineScope.launch {
                                offsetX.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(stiffness = 520f, dampingRatio = 0.8f)
                                )
                                armed = false
                            }
                            hit?.run()
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            val minBound = if (left != null) -maxDragPx else 0f
                            val maxBound = if (right != null) maxDragPx else 0f
                            val newTarget = (offsetX.value + dragAmount).coerceIn(minBound, maxBound)

                            coroutineScope.launch {
                                offsetX.snapTo(newTarget)
                            }

                            val nextArmed = abs(newTarget) >= thresholdPx &&
                                ((newTarget > 0 && right != null) || (newTarget < 0 && left != null))
                            if (nextArmed != armed) {
                                armed = nextArmed
                                if (nextArmed) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            }
                        }
                    )
                }
        ) {
            content()
        }
    }
}
