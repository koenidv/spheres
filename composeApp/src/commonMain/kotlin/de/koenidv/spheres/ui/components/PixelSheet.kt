package de.koenidv.spheres.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.koenidv.spheres.platform.PlatformBackHandler
import de.koenidv.spheres.ui.theme.Ink
import de.koenidv.spheres.ui.theme.PixelFontFamily
import de.koenidv.spheres.ui.theme.Scrim
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Composable
fun PixelSheet(
    open: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    PlatformBackHandler(enabled = open, onBack = onClose)

    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val dragOffset = remember { Animatable(0f) }
    var currentDragOffset by remember { mutableFloatStateOf(0f) }
    var sheetHeightPx by remember { mutableFloatStateOf(0f) }
    val scrollState = rememberScrollState()

    LaunchedEffect(open) {
        if (open) {
            currentDragOffset = 0f
            dragOffset.snapTo(0f)
            scrollState.scrollTo(0)
        }
    }

    val dismissThresholdPx = with(density) { 96.dp.toPx() }
    val flingDismissThresholdPx = with(density) { 650.dp.toPx() }

    val handleRelease: (Float) -> Unit = { velocityY ->
        if (currentDragOffset > dismissThresholdPx || velocityY > flingDismissThresholdPx) {
            onClose()
        } else {
            scope.launch {
                dragOffset.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(stiffness = 550f, dampingRatio = 0.85f),
                    initialVelocity = velocityY
                )
                currentDragOffset = 0f
            }
        }
    }

    val nestedScrollConnection = remember(onClose, dismissThresholdPx, flingDismissThresholdPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                if (currentDragOffset > 0f && delta < 0f) {
                    val newOffset = (currentDragOffset + delta).coerceAtLeast(0f)
                    val consumed = newOffset - currentDragOffset
                    currentDragOffset = newOffset
                    scope.launch { dragOffset.snapTo(newOffset) }
                    return Offset(0f, consumed)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                val delta = available.y
                if (delta > 0f && scrollState.value == 0) {
                    currentDragOffset += delta
                    val newOffset = currentDragOffset
                    scope.launch { dragOffset.snapTo(newOffset) }
                    return Offset(0f, delta)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (currentDragOffset > 0f) {
                    handleRelease(available.y)
                    return available
                }
                return Velocity.Zero
            }
        }
    }

    val velocityTracker = remember { VelocityTracker() }

    AnimatedVisibility(
        visible = open,
        enter = EnterTransition.None,
        exit = ExitTransition.None
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .animateEnterExit(
                        enter = fadeIn(tween(220, easing = FastOutSlowInEasing)),
                        exit = fadeOut(tween(180, easing = FastOutLinearInEasing))
                    )
                    .graphicsLayer {
                        val dragFraction = if (sheetHeightPx > 0f) {
                            (dragOffset.value / sheetHeightPx).coerceIn(0f, 1f)
                        } else 0f
                        alpha = 1f - dragFraction
                    }
                    .background(Scrim.copy(alpha = 0.7f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClose
                    )
            )

            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.BottomCenter
            ) {
                val maxHeight = maxHeight * 0.92f

                Column(
                    modifier = modifier
                        .widthIn(max = 480.dp)
                        .fillMaxWidth()
                        .heightIn(max = maxHeight)
                        .onSizeChanged { sheetHeightPx = it.height.toFloat() }
                        .animateEnterExit(
                            enter = slideInVertically(
                                initialOffsetY = { it },
                                animationSpec = spring(stiffness = 460f, dampingRatio = 0.92f)
                            ),
                            exit = slideOutVertically(
                                targetOffsetY = { it },
                                animationSpec = tween(220, easing = FastOutLinearInEasing)
                            )
                        )
                        .offset { IntOffset(0, dragOffset.value.coerceAtLeast(0f).roundToInt()) }
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
                            .pointerInput(open) {
                                if (!open) return@pointerInput
                                detectVerticalDragGestures(
                                    onDragStart = {
                                        velocityTracker.resetTracking()
                                    },
                                    onVerticalDrag = { change, dragAmount ->
                                        change.consume()
                                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                                        currentDragOffset = (currentDragOffset + dragAmount).coerceAtLeast(0f)
                                        val newTarget = currentDragOffset
                                        scope.launch {
                                            dragOffset.snapTo(newTarget)
                                        }
                                    },
                                    onDragEnd = {
                                        val velocityY = velocityTracker.calculateVelocity().y
                                        handleRelease(velocityY)
                                    },
                                    onDragCancel = {
                                        handleRelease(0f)
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
                            .nestedScroll(nestedScrollConnection)
                            .then(if (scrollable) Modifier.verticalScroll(scrollState) else Modifier)
                            .padding(horizontal = 24.dp)
                            .padding(bottom = 20.dp),
                        content = content
                    )
                }
            }
        }
    }
}
