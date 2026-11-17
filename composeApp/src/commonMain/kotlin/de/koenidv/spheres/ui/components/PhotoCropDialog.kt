package de.koenidv.spheres.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.koenidv.spheres.platform.PlatformBackHandler
import de.koenidv.spheres.platform.cropSquareAvatarJpeg
import de.koenidv.spheres.platform.decodeImageBitmap
import de.koenidv.spheres.platform.haptic
import de.koenidv.spheres.ui.theme.Ink
import de.koenidv.spheres.ui.theme.PixelFontFamily
import de.koenidv.spheres.ui.theme.SansFontFamily
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class CropBounds(
    val leftFrac: Float,
    val topFrac: Float,
    val widthFrac: Float,
    val heightFrac: Float,
)

internal fun computeBaseScale(imgW: Float, imgH: Float, cropSizePx: Float): Float =
    max(cropSizePx / imgW.coerceAtLeast(1f), cropSizePx / imgH.coerceAtLeast(1f))

internal fun clampCropOffset(
    offsetX: Float,
    offsetY: Float,
    imgW: Float,
    imgH: Float,
    cropSizePx: Float,
    scale: Float,
): Offset {
    val dispW = imgW * scale
    val dispH = imgH * scale
    val maxX = ((dispW - cropSizePx) / 2f).coerceAtLeast(0f)
    val maxY = ((dispH - cropSizePx) / 2f).coerceAtLeast(0f)
    return Offset(
        x = offsetX.coerceIn(-maxX, maxX),
        y = offsetY.coerceIn(-maxY, maxY),
    )
}

internal fun computeCropBounds(
    imgW: Float,
    imgH: Float,
    cropSizePx: Float,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
): CropBounds {
    val cropSideImg = cropSizePx / scale
    val leftImg = (imgW / 2f) + (-cropSizePx / 2f - offsetX) / scale
    val topImg = (imgH / 2f) + (-cropSizePx / 2f - offsetY) / scale
    val clampedLeft = leftImg.coerceIn(0f, (imgW - cropSideImg).coerceAtLeast(0f))
    val clampedTop = topImg.coerceIn(0f, (imgH - cropSideImg).coerceAtLeast(0f))
    return CropBounds(
        leftFrac = (clampedLeft / imgW).coerceIn(0f, 1f),
        topFrac = (clampedTop / imgH).coerceIn(0f, 1f),
        widthFrac = (cropSideImg / imgW).coerceIn(0f, 1f),
        heightFrac = (cropSideImg / imgH).coerceIn(0f, 1f),
    )
}

@Composable
fun PhotoCropDialog(
    sourceBytes: ByteArray,
    accentColor: Color,
    onConfirm: (ByteArray) -> Unit,
    onCancel: () -> Unit,
) {
    val bitmap: ImageBitmap? = remember(sourceBytes) { decodeImageBitmap(sourceBytes) }
    val scope = rememberCoroutineScope()
    var cropping by remember(sourceBytes) { mutableStateOf(false) }

    Dialog(
        onDismissRequest = { if (!cropping) onCancel() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        PlatformBackHandler(enabled = !cropping, onBack = onCancel)

        var zoom by remember(sourceBytes) { mutableFloatStateOf(1f) }
        var offsetX by remember(sourceBytes) { mutableFloatStateOf(0f) }
        var offsetY by remember(sourceBytes) { mutableFloatStateOf(0f) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0E0E0E))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(PxClipShape)
                        .background(Color.White.copy(alpha = 0.12f))
                        .clickable(enabled = !cropping) {
                            haptic()
                            onCancel()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    PixelIcon(
                        name = IconName.CLOSE,
                        size = 18.dp,
                        tint = Color.White
                    )
                }

                Text(
                    text = "Crop photo",
                    style = TextStyle(
                        fontFamily = PixelFontFamily(),
                        fontSize = 20.sp,
                        color = Color.White
                    )
                )

                val canReset = zoom > 1.02f || kotlin.math.abs(offsetX) > 2f || kotlin.math.abs(offsetY) > 2f
                Box(
                    modifier = Modifier
                        .height(34.dp)
                        .clip(PxClipShape)
                        .background(if (canReset) Color.White.copy(alpha = 0.14f) else Color.Transparent)
                        .clickable(enabled = canReset && !cropping) {
                            haptic()
                            zoom = 1f
                            offsetX = 0f
                            offsetY = 0f
                        }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (canReset) "Reset" else "",
                        style = TextStyle(
                            fontFamily = PixelFontFamily(),
                            fontSize = 14.sp,
                            color = Color.White
                        )
                    )
                }
            }

            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clipToBounds()
            ) {
                val density = LocalDensity.current
                val containerW = with(density) { maxWidth.toPx() }
                val containerH = with(density) { maxHeight.toPx() }
                val cropPaddingPx = with(density) { 24.dp.toPx() }
                val cropSizePx = (min(containerW, containerH) - cropPaddingPx * 2f).coerceAtLeast(120f)
                val borderPx = with(density) { 2.dp.toPx() }
                val cornerLenPx = with(density) { 14.dp.toPx() }
                val cornerStrokePx = with(density) { 3.dp.toPx() }

                if (bitmap != null && containerW > 0f && containerH > 0f) {
                    val imgW = bitmap.width.toFloat().coerceAtLeast(1f)
                    val imgH = bitmap.height.toFloat().coerceAtLeast(1f)
                    val baseScale = computeBaseScale(imgW, imgH, cropSizePx)
                    val currentScale = baseScale * zoom
                    val clamped = clampCropOffset(offsetX, offsetY, imgW, imgH, cropSizePx, currentScale)
                    if (clamped.x != offsetX) offsetX = clamped.x
                    if (clamped.y != offsetY) offsetY = clamped.y

                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(sourceBytes, containerW, containerH, cropSizePx) {
                                detectTapGestures(
                                    onDoubleTap = { tap ->
                                        haptic()
                                        val cx = containerW / 2f
                                        val cy = containerH / 2f
                                        if (zoom > 1.05f) {
                                            zoom = 1f
                                            offsetX = 0f
                                            offsetY = 0f
                                        } else {
                                            val targetZoom = 2.2f
                                            val ratio = targetZoom / zoom
                                            val fx = tap.x - cx
                                            val fy = tap.y - cy
                                            val next = clampCropOffset(
                                                offsetX = fx - ratio * (fx - offsetX),
                                                offsetY = fy - ratio * (fy - offsetY),
                                                imgW = imgW,
                                                imgH = imgH,
                                                cropSizePx = cropSizePx,
                                                scale = baseScale * targetZoom,
                                            )
                                            zoom = targetZoom
                                            offsetX = next.x
                                            offsetY = next.y
                                        }
                                    }
                                )
                            }
                            .pointerInput(sourceBytes, containerW, containerH, cropSizePx) {
                                detectTransformGestures { centroid, pan, gestureZoom, _ ->
                                    val oldZoom = zoom
                                    val newZoom = (oldZoom * gestureZoom).coerceIn(1f, 5f)
                                    val ratio = newZoom / oldZoom
                                    val cx = containerW / 2f
                                    val cy = containerH / 2f
                                    val fx = centroid.x - cx
                                    val fy = centroid.y - cy
                                    val rawX = fx - ratio * (fx - offsetX) + pan.x
                                    val rawY = fy - ratio * (fy - offsetY) + pan.y
                                    val next = clampCropOffset(
                                        offsetX = rawX,
                                        offsetY = rawY,
                                        imgW = imgW,
                                        imgH = imgH,
                                        cropSizePx = cropSizePx,
                                        scale = baseScale * newZoom,
                                    )
                                    zoom = newZoom
                                    offsetX = next.x
                                    offsetY = next.y
                                }
                            }
                    ) {
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val scale = baseScale * zoom
                        val dispW = imgW * scale
                        val dispH = imgH * scale

                        drawImage(
                            image = bitmap,
                            dstOffset = IntOffset(
                                (cx + offsetX - dispW / 2f).roundToInt(),
                                (cy + offsetY - dispH / 2f).roundToInt()
                            ),
                            dstSize = IntSize(
                                dispW.roundToInt().coerceAtLeast(1),
                                dispH.roundToInt().coerceAtLeast(1)
                            ),
                            filterQuality = FilterQuality.Medium
                        )

                        val cropLeft = cx - cropSizePx / 2f
                        val cropTop = cy - cropSizePx / 2f
                        val cropRight = cropLeft + cropSizePx
                        val cropBottom = cropTop + cropSizePx
                        val scrim = Color.Black.copy(alpha = 0.65f)

                        drawRect(scrim, Offset.Zero, Size(size.width, cropTop.coerceAtLeast(0f)))
                        drawRect(
                            scrim,
                            Offset(0f, cropBottom),
                            Size(size.width, (size.height - cropBottom).coerceAtLeast(0f))
                        )
                        drawRect(
                            scrim,
                            Offset(0f, cropTop),
                            Size(cropLeft.coerceAtLeast(0f), cropSizePx)
                        )
                        drawRect(
                            scrim,
                            Offset(cropRight, cropTop),
                            Size((size.width - cropRight).coerceAtLeast(0f), cropSizePx)
                        )

                        val third = cropSizePx / 3f
                        val gridColor = Color.White.copy(alpha = 0.25f)
                        for (i in 1..2) {
                            drawLine(
                                color = gridColor,
                                start = Offset(cropLeft + third * i, cropTop),
                                end = Offset(cropLeft + third * i, cropBottom),
                                strokeWidth = 1f
                            )
                            drawLine(
                                color = gridColor,
                                start = Offset(cropLeft, cropTop + third * i),
                                end = Offset(cropRight, cropTop + third * i),
                                strokeWidth = 1f
                            )
                        }

                        drawRect(
                            color = Color.White,
                            topLeft = Offset(cropLeft, cropTop),
                            size = Size(cropSizePx, cropSizePx),
                            style = Stroke(width = borderPx)
                        )

                        val corners = listOf(
                            Offset(cropLeft, cropTop) to Offset(1f, 1f),
                            Offset(cropRight, cropTop) to Offset(-1f, 1f),
                            Offset(cropLeft, cropBottom) to Offset(1f, -1f),
                            Offset(cropRight, cropBottom) to Offset(-1f, -1f),
                        )
                        corners.forEach { (pt, dir) ->
                            drawLine(
                                color = accentColor,
                                start = pt,
                                end = Offset(pt.x + dir.x * cornerLenPx, pt.y),
                                strokeWidth = cornerStrokePx
                            )
                            drawLine(
                                color = accentColor,
                                start = pt,
                                end = Offset(pt.x, pt.y + dir.y * cornerLenPx),
                                strokeWidth = cornerStrokePx
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "Drag to position · Pinch or double-tap to zoom",
                            style = TextStyle(
                                fontFamily = SansFontFamily(),
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CropActionButton(
                                text = "Cancel",
                                fill = Color(0xFF232323),
                                shadow = Color(0xFF141414),
                                textColor = Color.White,
                                enabled = !cropping,
                                onClick = onCancel,
                                modifier = Modifier.weight(1f)
                            )
                            CropActionButton(
                                text = if (cropping) "Cropping…" else "Use photo",
                                fill = Color.White,
                                shadow = accentColor,
                                textColor = Ink,
                                enabled = !cropping,
                                onClick = {
                                    cropping = true
                                    val bounds = computeCropBounds(
                                        imgW = imgW,
                                        imgH = imgH,
                                        cropSizePx = cropSizePx,
                                        scale = baseScale * zoom,
                                        offsetX = offsetX,
                                        offsetY = offsetY,
                                    )
                                    scope.launch {
                                        val cropped = withContext(Dispatchers.Default) {
                                            cropSquareAvatarJpeg(
                                                sourceBytes = sourceBytes,
                                                leftFrac = bounds.leftFrac,
                                                topFrac = bounds.topFrac,
                                                widthFrac = bounds.widthFrac,
                                                heightFrac = bounds.heightFrac,
                                            )
                                        }
                                        cropping = false
                                        if (cropped != null) {
                                            onConfirm(cropped)
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1.4f)
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

@Composable
private fun CropActionButton(
    text: String,
    fill: Color,
    shadow: Color,
    textColor: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    PixelShell(
        fill = fill,
        shadow = shadow,
        lift = 3.dp,
        isPressed = pressed && enabled,
        modifier = modifier
            .height(50.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = {
                    haptic()
                    onClick()
                }
            )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = TextStyle(
                    fontFamily = PixelFontFamily(),
                    fontSize = 18.sp,
                    color = textColor
                )
            )
        }
    }
}
