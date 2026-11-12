package de.koenidv.spheres.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.koenidv.spheres.ui.theme.Ink
import de.koenidv.spheres.ui.theme.PixelFontFamily
import de.koenidv.spheres.ui.theme.Scrim
import kotlin.math.roundToInt

@Composable
fun PixelSheet(
    open: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    AnimatedVisibility(
        visible = open,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        val density = LocalDensity.current
        var dragOffsetY by remember { mutableFloatStateOf(0f) }
        val dismissThresholdPx = with(density) { 110.dp.toPx() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Scrim.copy(alpha = 0.7f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClose
                ),
            contentAlignment = Alignment.BottomCenter
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val maxHeight = maxHeight * 0.92f

                Column(
                    modifier = modifier
                        .widthIn(max = 480.dp)
                        .fillMaxWidth()
                        .heightIn(max = maxHeight)
                        .offset { IntOffset(0, dragOffsetY.coerceAtLeast(0f).roundToInt()) }
                        .clip(SheetClipShape)
                        .background(Color.White)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {}
                        )
                        .imePadding()
                        .navigationBarsPadding(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                                detectVerticalDragGestures(
                                    onDragEnd = {
                                        if (dragOffsetY > dismissThresholdPx) {
                                            dragOffsetY = 0f
                                            onClose()
                                        } else {
                                            dragOffsetY = 0f
                                        }
                                    },
                                    onVerticalDrag = { change, dragAmount ->
                                        change.consume()
                                        dragOffsetY = (dragOffsetY + dragAmount).coerceAtLeast(0f)
                                    }
                                )
                            },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(0.dp)
                        ) {
                            for (i in 0 until 8) {
                                val c = if (i == 0 || i == 7) Color(0xFFE2E2E2) else Color(0xFFC9C9C9)
                                Box(
                                    modifier = Modifier
                                        .size(4.dp)
                                        .background(c)
                                )
                            }
                        }

                        if (title != null) {
                            Text(
                                text = title,
                                style = TextStyle(
                                    fontFamily = PixelFontFamily(),
                                    fontSize = 20.sp,
                                    lineHeight = 24.sp,
                                    color = Ink
                                ),
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp)
                            .padding(bottom = 20.dp),
                        content = content
                    )
                }
            }
        }
    }
}
