package de.koenidv.spheres.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.koenidv.spheres.domain.AvatarConfig
import de.koenidv.spheres.domain.INTERVALS
import de.koenidv.spheres.domain.Quality
import de.koenidv.spheres.ui.theme.Ink
import de.koenidv.spheres.ui.theme.PixelFontFamily
import de.koenidv.spheres.ui.theme.parseHexColor
import de.koenidv.spheres.ui.theme.tintColor
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sqrt

private const val STEP = 4f
private const val GUTTER = 12f
private const val CELL_H = 84f
val RADAR_AVATAR_SIZE = 48.dp
private const val ROW_OFFSET = 36f
private const val SPHERE_GAP = 20f
private const val MIN_BAND = 140f
private const val TOP_PAD = 20f
private const val BOTTOM_PAD = 140f
private const val HIDDEN_ROW = 48f
private const val FILL_EXTRA = 4000f

private fun snap(n: Float): Float = round(n / STEP) * STEP

fun arcOffset(radius: Float, cx: Float, x: Float): Float {
    val b = floor((x - cx) / STEP)
    val dx = abs(b * STEP + STEP / 2f)
    val dy = radius - sqrt(max(0f, radius * radius - dx * dx))
    return snap(dy)
}

data class RadarCell(val x: Float, val y: Float, val index: Int, val ghost: Boolean)
data class BandLayout(val id: String, val arcTop: Float, val bottom: Float, val cells: List<RadarCell>)
data class RadarLayout(
    val width: Float,
    val height: Float,
    val cx: Float,
    val radius: Float,
    val cellW: Float,
    val bands: List<BandLayout>
)

fun layoutRadar(width: Float, counts: List<Int>): RadarLayout {
    val cx = floor(width / 2f)
    val radius = 1.7f * width
    val cellW = min(84f, max(64f, floor((width - GUTTER * 2f) / 5f)))
    val kMax = max(0, floor((width / 2f - GUTTER - cellW / 2f) / cellW).toInt())
    val order = mutableListOf(0)
    for (k in 1..kMax) {
        order.add(k)
        order.add(-k)
    }
    val perRow = order.size
    val dip = order.map { k -> arcOffset(radius, cx, cx + k * cellW) }

    val out = mutableListOf<BandLayout>()
    var arcTop = TOP_PAD
    var lowest = TOP_PAD
    for (i in counts.indices) {
        val total = counts[i] + 1
        val rows = ceil(total.toFloat() / perRow).toInt()
        val cells = mutableListOf<RadarCell>()
        for (n in 0 until total) {
            val row = n / perRow
            val slot = n % perRow
            val k = order[slot]
            cells.add(
                RadarCell(
                    x = cx + k * cellW,
                    y = arcTop + ROW_OFFSET + row * CELL_H + dip[slot],
                    index = n,
                    ghost = (n == total - 1)
                )
            )
        }
        val bottom = arcTop + ROW_OFFSET + (rows - 1) * CELL_H + CELL_H
        lowest = max(lowest, cells.maxOfOrNull { it.y + CELL_H } ?: bottom)
        out.add(BandLayout(id = i.toString(), arcTop = arcTop, bottom = bottom, cells = cells))
        arcTop = snap(max(bottom + SPHERE_GAP, arcTop + MIN_BAND))
    }
    return RadarLayout(
        width = width,
        height = lowest + BOTTOM_PAD,
        cx = cx,
        radius = radius,
        cellW = cellW,
        bands = out
    )
}

private class RadarMorph {
    var last: RadarLayout? = null
    var from: RadarLayout? = null
}

data class ArcPaths(val strokePath: Path, val fillPath: Path)

fun buildArcPaths(layout: RadarLayout, arcTop: Float, canvasHeightDp: Float, density: Float): ArcPaths {
    val stroke = Path()
    val fill = Path()
    val cx = layout.cx
    val width = layout.width
    val radius = layout.radius
    val h = canvasHeightDp - arcTop
    val b0 = floor(-cx / STEP).toInt()
    val b1 = ceil((width - cx) / STEP).toInt()

    var lastY = -1f
    for (b in b0 until b1) {
        val x0 = max(0f, cx + b * STEP) * density
        val x1 = min(width, cx + (b + 1) * STEP) * density
        val y = (arcOffset(radius, cx, cx + b * STEP + 1f) + 1f) * density
        if (lastY < 0f) {
            stroke.moveTo(x0, y)
            fill.moveTo(x0, y)
        } else if (y != lastY) {
            stroke.lineTo(x0, y)
            fill.lineTo(x0, y)
        }
        stroke.lineTo(x1, y)
        fill.lineTo(x1, y)
        lastY = y
    }
    fill.lineTo(width * density, (h + FILL_EXTRA) * density)
    fill.lineTo(0f, (h + FILL_EXTRA) * density)
    fill.close()

    return ArcPaths(stroke, fill)
}

enum class DueStatus {
    OVERDUE, SOON, OK
}

data class RadarPerson(
    val id: String,
    val name: String,
    val intervalDays: Int,
    val score: Int,
    val status: DueStatus,
    val quality: Quality? = null,
    val avatar: AvatarConfig? = null,
    val photoUrl: String? = null,
)

data class RadarInterval(
    val days: Int,
    val label: String,
    val colorHex: String
)

val DEFAULT_RADAR_INTERVALS = INTERVALS.map { RadarInterval(it.days, it.label, it.color) }

private fun statusLabel(status: DueStatus): String = when (status) {
    DueStatus.OVERDUE -> "Overdue"
    DueStatus.SOON -> "Due soon"
    DueStatus.OK -> "On track"
}

@Composable
private fun RadarTooltip(name: String, detail: String) {
    val offsetPx = with(LocalDensity.current) { 6.dp.roundToPx() }
    Popup(alignment = Alignment.TopCenter, offset = IntOffset(0, -offsetPx)) {
        Column(
            modifier = Modifier
                .background(Ink)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = name,
                style = TextStyle(fontFamily = PixelFontFamily(), fontSize = 13.sp, lineHeight = 16.sp, color = Color.White),
                maxLines = 1
            )
            Text(
                text = detail,
                style = TextStyle(fontFamily = PixelFontFamily(), fontSize = 12.sp, lineHeight = 14.sp, color = Color.White.copy(alpha = 0.7f)),
                maxLines = 1
            )
        }
    }
}

// Pixel-stepped ring with notched corners, floating outside the avatar; opens a gap where the status badge sits
private fun DrawScope.drawSelectionRing(progress: Float, leaveBadgeRoom: Boolean) {
    val stroke = 2.dp.toPx()
    val gap = (3f + 5f * (1f - progress)).dp.toPx()
    val a = size.width
    val l = -(gap + stroke)
    val t = l
    val r = a + gap + stroke
    val b = r
    val ink = Ink.copy(alpha = progress.coerceIn(0f, 1f))
    val topEnd = if (leaveBadgeRoom) a - 18.dp.toPx() else r - stroke
    val rightStart = if (leaveBadgeRoom) 18.dp.toPx() else t + stroke
    drawRect(ink, Offset(l + stroke, t), Size(topEnd - (l + stroke), stroke))
    drawRect(ink, Offset(l + stroke, b - stroke), Size(r - stroke - (l + stroke), stroke))
    drawRect(ink, Offset(l, t + stroke), Size(stroke, b - stroke - (t + stroke)))
    drawRect(ink, Offset(r - stroke, rightStart), Size(stroke, b - stroke - rightStart))
}

fun firstName(name: String): String = name.trim().split("\\s+".toRegex()).firstOrNull() ?: name

@Composable
fun RadarBadge(status: DueStatus, modifier: Modifier = Modifier) {
    when (status) {
        DueStatus.OVERDUE -> {
            val infiniteTransition = rememberInfiniteTransition()
            val bounceY by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = -2f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                )
            )
            Canvas(
                modifier = modifier
                    .size(18.dp)
                    .offset(y = bounceY.dp)
            ) {
                val step = size.width / 9f
                drawRect(Color.White, Offset(step, 0f), Size(7 * step, step))
                drawRect(Color.White, Offset(8 * step, step), Size(step, 7 * step))
                drawRect(Color.White, Offset(step, 8 * step), Size(7 * step, step))
                drawRect(Color.White, Offset(0f, step), Size(step, 7 * step))
                drawRect(Color(0xFFE36F73), Offset(step, step), Size(7 * step, 7 * step))
                drawRect(Color.White, Offset(4 * step, 2 * step), Size(step, 3 * step))
                drawRect(Color.White, Offset(4 * step, 6 * step), Size(step, step))
            }
        }
        DueStatus.SOON -> {
            Canvas(modifier = modifier.size(14.dp)) {
                val step = size.width / 7f
                drawRect(Color.White, Offset(2 * step, 0f), Size(3 * step, step))
                drawRect(Color.White, Offset(5 * step, step), Size(step, step))
                drawRect(Color.White, Offset(6 * step, 2 * step), Size(step, 3 * step))
                drawRect(Color.White, Offset(5 * step, 5 * step), Size(step, step))
                drawRect(Color.White, Offset(2 * step, 6 * step), Size(3 * step, step))
                drawRect(Color.White, Offset(step, 5 * step), Size(step, step))
                drawRect(Color.White, Offset(0f, 2 * step), Size(step, 3 * step))
                drawRect(Color.White, Offset(step, step), Size(step, step))
                drawRect(Color(0xFFF0A83C), Offset(2 * step, step), Size(3 * step, 5 * step))
                drawRect(Color(0xFFF0A83C), Offset(step, 2 * step), Size(5 * step, 3 * step))
            }
        }
        DueStatus.OK -> {}
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Radar(
    people: List<RadarPerson>,
    onPersonClick: (String) -> Unit,
    onPersonLongPress: (id: String, anchorX: Float, anchorY: Float, anchorWidth: Float, anchorHeight: Float) -> Unit,
    onAddClick: (intervalDays: Int) -> Unit,
    modifier: Modifier = Modifier,
    minHeightDp: Float = 0f,
    intervals: List<RadarInterval> = DEFAULT_RADAR_INTERVALS,
    scrollOffsetDp: () -> Float = { 0f },
    viewportHeightDp: Float = Float.MAX_VALUE,
) {
    val haptic = LocalHapticFeedback.current
    val selectedId = LocalSelectedPersonId.current
    val density = LocalDensity.current.density
    val scrollBucket by remember(scrollOffsetDp, viewportHeightDp) {
        derivedStateOf { (scrollOffsetDp() / 160f).toInt() }
    }
    val visTopDp = scrollBucket * 160f - 240f
    val visBottomDp = scrollBucket * 160f + viewportHeightDp + 240f

    val bandsWithPeople = remember(people, intervals) {
        val map = intervals.associateWith { interval ->
            people.filter { p ->
                intervals.minByOrNull { abs(it.days - p.intervalDays) } == interval
            }.sortedWith(compareByDescending<RadarPerson> { 100 - it.score }.thenBy { it.name })
        }
        val empty = map.values.all { it.isEmpty() }
        val shownIntervals = intervals.filterIndexed { index, i ->
            if (empty) index < 3 else (map[i]?.isNotEmpty() == true)
        }
        val list = shownIntervals.map { i -> Pair(i, map[i] ?: emptyList()) }
        val hidden = if (empty) emptyList() else intervals.filter { !shownIntervals.contains(it) }
        Pair(list, hidden)
    }

    val shownBands = bandsWithPeople.first
    val hiddenIntervals = bandsWithPeople.second

    val targetWidthDp = LocalRadarTargetWidth.current?.value
    val morph = remember { mutableFloatStateOf(1f) }
    val tracker = remember { RadarMorph() }

    BoxWithConstraints(
        modifier = modifier
    ) {
        val widthDp = maxWidth.value
        val layoutWidthDp = targetWidthDp?.takeIf { it > 0f } ?: widthDp
        val layout = remember(layoutWidthDp, shownBands) {
            if (layoutWidthDp > 0f) layoutRadar(layoutWidthDp, shownBands.map { it.second.size }) else null
        }

        if (layout != null && targetWidthDp != null) {
            val prev = tracker.last
            if (prev != null && prev.width != layout.width && prev.bands.size == layout.bands.size) {
                tracker.from = prev
                morph.floatValue = 0f
            }
        }
        tracker.last = layout
        LaunchedEffect(layout?.width) {
            if (tracker.from != null) {
                animate(0f, 1f, animationSpec = spring(stiffness = 520f, dampingRatio = 0.96f)) { v, _ ->
                    morph.floatValue = v
                }
                tracker.from = null
            }
        }

        val minContentHeightDp = (layout?.height ?: 400f) + (if (hiddenIntervals.isNotEmpty()) HIDDEN_ROW else 0f)
        val totalHeightDp = max(minHeightDp, minContentHeightDp)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(totalHeightDp.dp)
        ) {
            if (layout != null) {
                // Cells keep the target layout and glide from the previous one while the area resizes
                val shiftDp = (widthDp - layout.width) / 2f
                val arcLayout = remember(layout, widthDp) {
                    if (layout.width == widthDp) layout else layout.copy(width = widthDp, cx = floor(widthDp / 2f))
                }
                fun fromBand(si: Int): BandLayout? = tracker.from?.bands?.getOrNull(si)
                fun Density.arcTopPx(si: Int): Int {
                    val to = layout.bands[si].arcTop
                    val f = fromBand(si)?.arcTop ?: to
                    return (f + (to - f) * morph.floatValue).dp.roundToPx()
                }
                fun Density.cellOffset(si: Int, c: RadarCell): IntOffset {
                    val t = morph.floatValue
                    val nx = c.x - layout.cellW / 2f + shiftDp
                    val f = fromBand(si)?.cells?.getOrNull(c.index)
                    val from = tracker.from
                    return if (f == null || from == null) {
                        IntOffset(nx.dp.roundToPx(), c.y.dp.roundToPx())
                    } else {
                        val fx = f.x - from.cellW / 2f
                        IntOffset((fx + (nx - fx) * t).dp.roundToPx(), (f.y + (c.y - f.y) * t).dp.roundToPx())
                    }
                }

                shownBands.forEachIndexed { si, (band, _) ->
                    val bl = layout.bands[si]
                    val color = parseHexColor(band.colorHex)
                    val arcPaths = remember(arcLayout, bl.arcTop, totalHeightDp, density) {
                        buildArcPaths(arcLayout, bl.arcTop, totalHeightDp, density)
                    }
                    val canvasTop = minOf(bl.arcTop, fromBand(si)?.arcTop ?: bl.arcTop)

                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset { IntOffset(0, arcTopPx(si)) }
                            .height((totalHeightDp - canvasTop).dp)
                    ) {
                            drawPath(path = arcPaths.fillPath, color = tintColor(color, 20))
                            drawPath(
                                path = arcPaths.strokePath,
                                color = color,
                                style = Stroke(width = 1.dp.toPx())
                            )
                        }
                    }

                    shownBands.forEachIndexed { si, (band, personList) ->
                        val bl = layout.bands[si]
                        val color = parseHexColor(band.colorHex)

                        key(band.days) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .offset { IntOffset(0, arcTopPx(si) - 11.dp.roundToPx()) },
                                contentAlignment = Alignment.TopCenter
                            ) {
                                SpherePill(label = band.label, color = color)
                            }

                            bl.cells.forEach { c ->
                                if (c.y + CELL_H < visTopDp || c.y > visBottomDp) return@forEach
                                val person = if (c.ghost) null else personList.getOrNull(c.index)

                                key(person?.id ?: "ghost-${band.days}-${c.index}") {
                                    if (person == null) {
                                        val interactionSource = remember { MutableInteractionSource() }
                                        val isPressed by interactionSource.collectIsPressedAsState()
                                        val pressOffset = if (isPressed) 2.dp else 0.dp

                                        Box(
                                            modifier = Modifier
                                                .offset { cellOffset(si, c) }
                                                .size(layout.cellW.dp, CELL_H.dp)
                                                .clickable(
                                                    interactionSource = interactionSource,
                                                    indication = null,
                                                    onClick = { onAddClick(band.days) }
                                                )
                                                .padding(top = 2.dp),
                                            contentAlignment = Alignment.TopCenter
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(RADAR_AVATAR_SIZE)
                                                        .offset(y = pressOffset)
                                                        .background(Color.White.copy(alpha = if (isPressed) 0.8f else 0.5f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                                        val dash = 4.dp.toPx()
                                                        val strokeW = 2.dp.toPx()
                                                        var x = 0f
                                                        while (x < size.width) {
                                                            val segW = min(dash, size.width - x)
                                                            drawRect(color, Offset(x, 0f), Size(segW, strokeW))
                                                            drawRect(color, Offset(x, size.height - strokeW), Size(segW, strokeW))
                                                            x += dash * 2f
                                                        }
                                                        var y = 0f
                                                        while (y < size.height) {
                                                            val segH = min(dash, size.height - y)
                                                            drawRect(color, Offset(0f, y), Size(strokeW, segH))
                                                            drawRect(color, Offset(size.width - strokeW, y), Size(strokeW, segH))
                                                            y += dash * 2f
                                                        }
                                                    }
                                                    PixelIcon(name = IconName.PLUS, size = 24.dp, tint = color)
                                                }
                                                Text(
                                                    text = "Add",
                                                    style = TextStyle(
                                                        fontFamily = PixelFontFamily(),
                                                        fontSize = 13.sp,
                                                        lineHeight = 16.sp,
                                                        color = Ink.copy(alpha = 0.4f)
                                                    ),
                                                    modifier = Modifier.padding(top = 4.dp)
                                                )
                                            }
                                        }
                                    } else {
                                        val coordsRef = remember(person.id) { arrayOfNulls<LayoutCoordinates>(1) }
                                        val hoverSource = remember(person.id) { MutableInteractionSource() }
                                        val hovered by hoverSource.collectIsHoveredAsState()

                                        Box(
                                            modifier = Modifier
                                                .offset { cellOffset(si, c) }
                                                .size(layout.cellW.dp, CELL_H.dp)
                                                .onGloballyPositioned { coords ->
                                                    coordsRef[0] = coords
                                                }
                                                .hoverable(hoverSource)
                                                .pointerHoverIcon(PointerIcon.Hand)
                                                .onSecondaryClick {
                                                    val bounds = coordsRef[0]?.boundsInWindow()
                                                    onPersonLongPress(
                                                        person.id,
                                                        bounds?.left ?: 0f,
                                                        bounds?.top ?: 0f,
                                                        bounds?.width ?: 0f,
                                                        bounds?.height ?: 0f
                                                    )
                                                }
                                                .pointerInput(person.id) {
                                                    detectTapGestures(
                                                        onLongPress = {
                                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            val bounds = coordsRef[0]?.boundsInWindow()
                                                            onPersonLongPress(
                                                                person.id,
                                                                bounds?.left ?: 0f,
                                                                bounds?.top ?: 0f,
                                                                bounds?.width ?: 0f,
                                                                bounds?.height ?: 0f
                                                            )
                                                        },
                                                        onTap = {
                                                            onPersonClick(person.id)
                                                        }
                                                    )
                                                }
                                                .padding(top = 2.dp),
                                            contentAlignment = Alignment.TopCenter
                                        ) {
                                            if (hovered) {
                                                RadarTooltip(
                                                    name = person.name,
                                                    detail = "${statusLabel(person.status)} · ${band.label}"
                                                )
                                            }
                                            val selection by animateFloatAsState(
                                                targetValue = if (person.id == selectedId) 1f else 0f,
                                                animationSpec = spring(stiffness = 520f, dampingRatio = 0.7f),
                                                label = "RadarSelection"
                                            )
                                            val dim by animateFloatAsState(
                                                targetValue = if (selectedId != null && person.id != selectedId) 0.55f else 1f,
                                                animationSpec = tween(220),
                                                label = "RadarDim"
                                            )
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(RADAR_AVATAR_SIZE)
                                                        .drawBehind {
                                                            if (selection > 0.01f) {
                                                                drawSelectionRing(
                                                                    progress = selection,
                                                                    leaveBadgeRoom = person.status != DueStatus.OK
                                                                )
                                                            }
                                                        }
                                                ) {
                                                    Box(Modifier.graphicsLayer { alpha = dim }) {
                                                        AvatarRender(
                                                            name = person.name,
                                                            colorHex = band.colorHex,
                                                            config = person.avatar,
                                                            photoUrl = person.photoUrl,
                                                            size = RADAR_AVATAR_SIZE
                                                        )
                                                    }
                                                    if (person.status != DueStatus.OK) {
                                                        Box(
                                                            modifier = Modifier
                                                                .align(Alignment.TopEnd)
                                                                .offset(x = 4.dp, y = (-4).dp)
                                                                .graphicsLayer { alpha = dim }
                                                        ) {
                                                            RadarBadge(status = person.status)
                                                        }
                                                    }
                                                }

                                        Box(modifier = Modifier.padding(top = 4.dp).graphicsLayer { alpha = dim }) {
                                            HealthBar(score = person.score)
                                        }

                                        Row(
                                            modifier = Modifier
                                                .padding(top = 4.dp, start = 4.dp, end = 4.dp)
                                                .graphicsLayer { alpha = dim },
                                            verticalAlignment = Alignment.Bottom,
                                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Text(
                                                text = firstName(person.name),
                                                style = TextStyle(
                                                    fontFamily = PixelFontFamily(),
                                                    fontSize = 13.sp,
                                                    lineHeight = 16.sp,
                                                    color = Ink
                                                ),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            val mark = when (person.quality) {
                                                Quality.GOOD -> "++"
                                                Quality.AMAZING -> "+++"
                                                else -> null
                                            }
                                            if (mark != null) {
                                                Text(
                                                    text = mark,
                                                    style = TextStyle(
                                                        fontFamily = PixelFontFamily(),
                                                        fontWeight = FontWeight.SemiBold,
                                                        fontSize = 11.sp,
                                                        color = color
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        }
                    }

                    if (hiddenIntervals.isNotEmpty()) {
                        val contentBottom = layout.height - BOTTOM_PAD
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .offset(y = (contentBottom + 8f).dp)
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                        ) {
                            hiddenIntervals.forEach { interval ->
                                val color = parseHexColor(interval.colorHex)
                                Box(
                                    modifier = Modifier
                                        .height(28.dp)
                                        .clip(PxClipShape)
                                        .background(Color.White.copy(alpha = 0.7f))
                                        .clickable { onAddClick(interval.days) }
                                ) {
                                    Canvas(modifier = Modifier.matchParentSize()) {
                                        val dash = 4.dp.toPx()
                                        val strokeH = 1.dp.toPx()
                                        var x = 0f
                                        while (x < size.width) {
                                            val segW = min(dash, size.width - x)
                                            drawRect(color, Offset(x, 0f), Size(segW, strokeH))
                                            drawRect(color, Offset(x, size.height - strokeH), Size(segW, strokeH))
                                            x += dash * 2f
                                        }
                                    }
                                    Row(
                                        modifier = Modifier
                                            .height(28.dp)
                                            .padding(horizontal = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        PixelIcon(name = IconName.PLUS, size = 12.dp, tint = color)
                                        Text(
                                            text = interval.label,
                                            style = TextStyle(
                                                fontFamily = PixelFontFamily(),
                                                fontSize = 13.sp,
                                                color = Ink.copy(alpha = 0.6f)
                                            )
                                        )
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

private val SKELETON_BANDS = listOf(Color(0xFFF6F6F6), Color(0xFFF0F0F0), Color(0xFFEAEAEA))

@Composable
fun RadarSkeleton(modifier: Modifier = Modifier) {
    val density = LocalDensity.current.density
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
    ) {
        val widthDp = maxWidth.value
        val viewportHeightDp = maxHeight.value
        val layout = remember(widthDp) {
            if (widthDp > 0f) layoutRadar(widthDp, listOf(2, 4, 3)) else null
        }

        if (layout != null) {
            val totalHeightDp = max(viewportHeightDp, layout.height)
            layout.bands.forEachIndexed { i, b ->
                val arcPaths = remember(layout, b.arcTop, totalHeightDp, density) {
                    buildArcPaths(layout, b.arcTop, totalHeightDp, density)
                }
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = b.arcTop.dp)
                        .height((totalHeightDp - b.arcTop).dp)
                        .clipToBounds()
                ) {
                    drawPath(path = arcPaths.fillPath, color = SKELETON_BANDS[i % SKELETON_BANDS.size])
                    drawPath(path = arcPaths.strokePath, color = Color(0xFFE0E0E0), style = Stroke(1.dp.toPx()))
                }
            }

            layout.bands.forEach { b ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = (b.arcTop - 11f).dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp, 24.dp)
                            .clip(PxClipShape)
                            .background(Color(0xFFE4E4E4))
                    )
                }

                b.cells.filter { !it.ghost }.forEach { c ->
                    Column(
                        modifier = Modifier
                            .offset(x = (c.x - layout.cellW / 2f).dp, y = c.y.dp)
                            .width(layout.cellW.dp)
                            .padding(top = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(RADAR_AVATAR_SIZE)
                                .clip(PxClipShape)
                                .background(Color(0xFFE4E4E4))
                        )
                        Box(
                            modifier = Modifier
                                .padding(top = 6.dp)
                                .size(40.dp, 12.dp)
                                .background(Color(0xFFE9E9E9))
                        )
                    }
                }
            }
        }
    }
}
