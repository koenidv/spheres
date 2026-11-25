package de.koenidv.spheres.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.koenidv.spheres.domain.MapGeo
import de.koenidv.spheres.domain.Person
import de.koenidv.spheres.domain.WorldMask
import de.koenidv.spheres.domain.intervalInfo
import de.koenidv.spheres.ui.theme.Ink
import de.koenidv.spheres.ui.theme.PixelFontFamily
import de.koenidv.spheres.ui.theme.parseHexColor
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

val MapWater = Color(0xFFE3E8F8)
val UserLocationBlue = Color(0xFF4C7CF5)
private val UserLocationHighlight = Color(0xFF9EC0FF)
private val MapShallow = Color(0xFFEBEFFB)
private val MapLand = Color(0xFFFFFFFF)
private val MapLandTexture = Color(0xFFF4F4F9)
private val MapCoast = Color(0xFFBFC6E6)
private val MapBorder = Color(0xFFD9DCF0)

/** Edge length of one map pixel on screen, between this and twice this. */
private const val TARGET_CELL_DP = 7f
private const val BORDER_FADE_START_CELLS = 60f
private const val BORDER_FADE_RANGE_CELLS = 24f

/** Pin box the markers live in. The pin's tip sits at the bottom centre. */
val MapPinBoxWidth = 96.dp
val MapPinBoxHeight = 72.dp
val UserLocationBoxSize = 44.dp

/**
 * View onto the world: [cx]/[cy] is the world cell in the centre of the view and [scale] the
 * screen pixels per cell.
 */
@Stable
class MapCamera(private val maxScalePx: Float) {
    var cx by mutableFloatStateOf(MapGeo.WORLD_W / 2f)
        private set
    var cy by mutableFloatStateOf(MapGeo.WORLD_H / 2f)
        private set
    var scale by mutableFloatStateOf(1f)
        private set
    var viewW by mutableFloatStateOf(0f)
        private set
    var viewH by mutableFloatStateOf(0f)
        private set

    private var job: Job? = null

    val minScale: Float
        get() = if (viewW <= 0f) 0.1f else max(viewW / MapGeo.WORLD_W, viewH / MapGeo.WORLD_H) * 0.8f
    val maxScale: Float get() = maxScalePx

    fun xOf(u: Float) = (u - cx) * scale + viewW / 2f
    fun yOf(v: Float) = (v - cy) * scale + viewH / 2f

    fun setViewSize(w: Float, h: Float) {
        viewW = w
        viewH = h
        set(cx, cy, scale)
    }

    private fun set(u: Float, v: Float, s: Float) {
        val sc = s.coerceIn(minScale, maxScale)
        val halfW = viewW / 2f / sc
        val halfH = viewH / 2f / sc
        cx = if (halfW * 2f >= MapGeo.WORLD_W) MapGeo.WORLD_W / 2f else u.coerceIn(halfW, MapGeo.WORLD_W - halfW)
        cy = if (halfH * 2f >= MapGeo.WORLD_H) MapGeo.WORLD_H / 2f else v.coerceIn(halfH, MapGeo.WORLD_H - halfH)
        scale = sc
    }

    fun jumpTo(u: Float, v: Float, s: Float) {
        cancelAnimation()
        set(u, v, s)
    }

    fun cancelAnimation() {
        job?.cancel()
        job = null
    }

    fun panBy(dxPx: Float, dyPx: Float) {
        set(cx - dxPx / scale, cy - dyPx / scale, scale)
    }

    /** Zooms by [factor] keeping the world point under ([fx], [fy]) fixed. */
    fun zoomAt(fx: Float, fy: Float, factor: Float) {
        val newScale = (scale * factor).coerceIn(minScale, maxScale)
        val u = cx + (fx - viewW / 2f) / scale
        val v = cy + (fy - viewH / 2f) / scale
        set(u - (fx - viewW / 2f) / newScale, v - (fy - viewH / 2f) / newScale, newScale)
    }

    fun animateTo(scope: CoroutineScope, u: Float, v: Float, s: Float, durationMs: Int = 480) {
        cancelAnimation()
        val u0 = cx
        val v0 = cy
        val s0 = scale
        val s1 = s.coerceIn(minScale, maxScale)
        job = scope.launch {
            Animatable(0f).animateTo(1f, tween(durationMs, easing = FastOutSlowInEasing)) {
                val t = value
                set(
                    u0 + (u - u0) * t,
                    v0 + (v - v0) * t,
                    exp(ln(s0) + (ln(s1) - ln(s0)) * t)
                )
            }
        }
    }

    /** Animated zoom by [factor] around a screen point. */
    fun animateZoomAt(scope: CoroutineScope, fx: Float, fy: Float, factor: Float) {
        val newScale = (scale * factor).coerceIn(minScale, maxScale)
        val u = cx + (fx - viewW / 2f) / scale
        val v = cy + (fy - viewH / 2f) / scale
        animateTo(scope, u - (fx - viewW / 2f) / newScale, v - (fy - viewH / 2f) / newScale, newScale, 260)
    }
}

/** The pixel-art world, drawn on a grid of fixed on-screen cell size so it stays crisp at every zoom. */
@Composable
fun PixelTerrain(camera: MapCamera, modifier: Modifier = Modifier) {
    val density = LocalDensity.current.density
    Canvas(
        modifier = modifier.onSizeChanged { camera.setViewSize(it.width.toFloat(), it.height.toFloat()) }
    ) {
        drawTerrain(camera, density)
    }
}

private fun cellHash(i: Int, j: Int, k: Int): Int {
    var h = i * 73856093 xor j * 19349663 xor k * 83492791
    h = h xor (h ushr 13)
    return h and 0x7fffffff
}

private fun DrawScope.drawTerrain(camera: MapCamera, density: Float) {
    val w = size.width
    val h = size.height
    drawRect(MapWater)
    val scalePx = camera.scale
    if (w <= 0f || h <= 0f || scalePx <= 0f) return

    val k = ceil(log2(TARGET_CELL_DP * density / scalePx)).toInt()
    val s = 2f.pow(k)
    val cellPx = s * scalePx

    val i0 = floor(((0f - w / 2f) / scalePx + camera.cx) / s).toInt() - 1
    val i1 = floor(((w - w / 2f) / scalePx + camera.cx) / s).toInt() + 1
    val j0 = floor(((0f - h / 2f) / scalePx + camera.cy) / s).toInt() - 1
    val j1 = floor(((h - h / 2f) / scalePx + camera.cy) / s).toInt() + 1
    val nx = i1 - i0 + 1
    val ny = j1 - j0 + 1
    if (nx <= 0 || ny <= 0 || nx * ny > 40_000) return

    // Grid with a one cell border so neighbour checks never leave the array.
    val gw = nx + 2
    val land = BooleanArray(gw * (ny + 2))
    // Borders appear once about two Germanys (~36 cells, 18 degrees) fit across the view, fading in over a few steps.
    val borderAlpha = ((BORDER_FADE_START_CELLS - w / scalePx) / BORDER_FADE_RANGE_CELLS).coerceIn(0f, 1f)
    val showBorders = borderAlpha > 0f
    val borderColor = MapBorder.copy(alpha = borderAlpha)
    val country = if (showBorders) IntArray(gw * (ny + 2)) else null
    for (gy in 0 until ny + 2) {
        val j = j0 - 1 + gy
        for (gx in 0 until gw) {
            val i = i0 - 1 + gx
            land[gy * gw + gx] = if (k >= 0) {
                WorldMask.coverage(k, i, j) >= 0.35f
            } else {
                WorldMask.sample((i + 0.5f) * s, (j + 0.5f) * s) >= 0.5f
            }
            if (country != null && land[gy * gw + gx]) {
                country[gy * gw + gx] =
                    WorldMask.countryAt(floor((i + 0.5f) * s).toInt(), floor((j + 0.5f) * s).toInt())
            }
        }
    }

    val originX = (i0 * s - camera.cx) * scalePx + w / 2f
    val originY = (j0 * s - camera.cy) * scalePx + h / 2f
    fun edgeX(i: Int) = floor(originX + (i - i0) * cellPx)
    fun edgeY(j: Int) = floor(originY + (j - j0) * cellPx)
    fun isLand(i: Int, j: Int) = land[(j - j0 + 1) * gw + (i - i0 + 1)]
    fun borderBetween(i: Int, j: Int, i2: Int, j2: Int): Boolean {
        if (country == null) return false
        val a = country[(j - j0 + 1) * gw + (i - i0 + 1)]
        val b = country[(j2 - j0 + 1) * gw + (i2 - i0 + 1)]
        return a != 0 && b != 0 && a != b
    }

    // Shallow water hugging the coasts.
    for (j in j0..j1) for (i in i0..i1) {
        if (isLand(i, j)) continue
        var near = false
        for (dj in -1..1) for (di in -1..1) if (isLand(i + di, j + dj)) near = true
        if (near) {
            drawRect(
                MapShallow,
                Offset(edgeX(i), edgeY(j)),
                Size(edgeX(i + 1) - edgeX(i), edgeY(j + 1) - edgeY(j))
            )
        }
    }

    // Land, merged into horizontal runs.
    for (j in j0..j1) {
        var i = i0
        while (i <= i1) {
            if (!isLand(i, j)) { i++; continue }
            val start = i
            while (i <= i1 && isLand(i, j)) i++
            drawRect(
                MapLand,
                Offset(edgeX(start), edgeY(j)),
                Size(edgeX(i) - edgeX(start), edgeY(j + 1) - edgeY(j))
            )
        }
    }

    // A sprinkle of texture and the coastline.
    val borderPx = floor(min(max(1.5f * density, cellPx * 0.12f), cellPx / 3f)).coerceAtLeast(1f)
    val edge = min(max(2f * density, cellPx * 0.2f), cellPx / 2f).let { floor(it) }
    for (j in j0..j1) for (i in i0..i1) {
        if (!isLand(i, j)) continue
        val x = edgeX(i)
        val y = edgeY(j)
        val cw = edgeX(i + 1) - x
        val ch = edgeY(j + 1) - y
        if (cellHash(i, j, k) % 9 == 0) drawRect(MapLandTexture, Offset(x, y), Size(cw, ch))
        if (!isLand(i - 1, j)) drawRect(MapCoast, Offset(x, y), Size(edge, ch))
        if (!isLand(i + 1, j)) drawRect(MapCoast, Offset(x + cw - edge, y), Size(edge, ch))
        if (!isLand(i, j - 1)) drawRect(MapCoast, Offset(x, y), Size(cw, edge))
        if (!isLand(i, j + 1)) drawRect(MapCoast, Offset(x, y + ch - edge), Size(cw, edge))
        if (showBorders) {
            if (isLand(i + 1, j) && borderBetween(i, j, i + 1, j)) {
                drawRect(borderColor, Offset(x + cw - borderPx, y), Size(borderPx, ch))
            }
            if (isLand(i, j + 1) && borderBetween(i, j, i, j + 1)) {
                drawRect(borderColor, Offset(x, y + ch - borderPx), Size(cw, borderPx))
            }
        }
    }
}

/**
 * Marker for one person (their avatar in a pin) or, for several, a stack of avatars with a count.
 * Lives in a [MapPinBoxWidth] x [MapPinBoxHeight] box with the tip at the bottom centre.
 */
@Composable
fun MapPin(
    people: List<Person>,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    open: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    if (people.isEmpty()) return
    val single = people.size == 1
    val shown = people.take(3)
    val avatarSize = if (single) 40.dp else 30.dp
    val overlap = 18.dp
    val bodyW = if (single) 48.dp else avatarSize + overlap * (shown.size - 1) + 12.dp
    val bodyH = if (single) 48.dp else 42.dp
    val ring = if (single) parseHexColor(intervalInfo(people[0].intervalDays).color) else Ink
    val lift by animateDpAsState(if (selected || open) 6.dp else 0.dp, spring(stiffness = 600f))
    val openRing by animateFloatAsState(
        targetValue = if (open) 1f else 0f,
        animationSpec = spring(stiffness = 520f, dampingRatio = 0.7f),
        label = "PinOpenRing"
    )

    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        appear.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow))
    }

    Box(
        modifier = modifier
            .size(MapPinBoxWidth, MapPinBoxHeight)
            .graphicsLayer {
                scaleX = appear.value
                scaleY = appear.value
                alpha = appear.value.coerceIn(0f, 1f)
                transformOrigin = TransformOrigin(0.5f, 1f)
            },
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .offset(y = -lift)
                .then(
                    if (onClick != null) Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick
                    ) else Modifier
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.drawBehind {
                    if (openRing > 0.01f) drawPinOpenRing(openRing, leaveBadgeRoom = !single)
                }
            ) {
                Box(
                    modifier = Modifier
                        .offset(y = 3.dp)
                        .size(bodyW, bodyH)
                        .clip(PxClipShape)
                        .background(Ink.copy(alpha = 0.16f))
                )
                Box(
                    modifier = Modifier
                        .size(bodyW, bodyH)
                        .clip(PxClipShape)
                        .background(ring),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(bodyW - 6.dp, bodyH - 6.dp)
                            .clip(PxClipShape)
                            .background(Color.White),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (single) {
                            Box(Modifier.padding(start = 1.dp).clip(PxClipShape)) {
                                PersonAvatar(
                                    person = people[0],
                                    colorHex = intervalInfo(people[0].intervalDays).color,
                                    size = avatarSize
                                )
                            }
                        } else {
                            shown.forEachIndexed { i, p ->
                                Box(
                                    modifier = Modifier
                                        .offset(x = 3.dp + overlap * i)
                                        .size(avatarSize)
                                        .clip(PxClipShape)
                                        .background(Color.White)
                                        .padding(1.5.dp)
                                ) {
                                    PersonAvatar(
                                        person = p,
                                        colorHex = intervalInfo(p.intervalDays).color,
                                        size = avatarSize - 3.dp
                                    )
                                }
                            }
                        }
                    }
                }
                if (!single) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 6.dp, y = (-6).dp)
                            .clip(PxClipShape)
                            .background(Ink)
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (people.size > 99) "99+" else people.size.toString(),
                            style = TextStyle(
                                fontFamily = PixelFontFamily(),
                                fontSize = 12.sp,
                                lineHeight = 14.sp,
                                color = Color.White
                            )
                        )
                    }
                }
            }
            Canvas(Modifier.size(12.dp, 6.dp)) {
                val step = size.height / 3f
                drawRect(ring, Offset(0f, 0f), Size(size.width, step))
                drawRect(ring, Offset(size.width * 2f / 12f * 1f, step), Size(size.width * 8f / 12f, step))
                drawRect(ring, Offset(size.width * 4f / 12f, step * 2f), Size(size.width * 4f / 12f, step))
            }
        }
    }
}

// Same stepped ring as the radar's selection, with gaps where the count badge and the pin tip sit
private fun DrawScope.drawPinOpenRing(progress: Float, leaveBadgeRoom: Boolean) {
    val stroke = 2.dp.toPx()
    val gap = (3f + 5f * (1f - progress)).dp.toPx()
    val l = -(gap + stroke)
    val t = l
    val r = size.width + gap + stroke
    val b = size.height + gap + stroke
    val ink = Ink.copy(alpha = progress.coerceIn(0f, 1f))
    val topEnd = if (leaveBadgeRoom) size.width - 14.dp.toPx() else r - stroke
    val rightStart = if (leaveBadgeRoom) 14.dp.toPx() else t + stroke
    val tipGap = 10.dp.toPx()
    val mid = size.width / 2f
    drawRect(ink, Offset(l + stroke, t), Size(topEnd - (l + stroke), stroke))
    drawRect(ink, Offset(l + stroke, b - stroke), Size(mid - tipGap - (l + stroke), stroke))
    drawRect(ink, Offset(mid + tipGap, b - stroke), Size(r - stroke - (mid + tipGap), stroke))
    drawRect(ink, Offset(l, t + stroke), Size(stroke, b - stroke - (t + stroke)))
    drawRect(ink, Offset(r - stroke, rightStart), Size(stroke, b - stroke - rightStart))
}

/**
 * Pixel-art marker for the user's own current location, centered in a [UserLocationBoxSize] square.
 */
@Composable
fun UserLocationMarker(
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        appear.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow))
    }

    val pulse = rememberInfiniteTransition(label = "userLocationPulse")
    val pulseProgress by pulse.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseProgress"
    )
    val lift by animateDpAsState(if (selected) 3.dp else 0.dp, spring(stiffness = 600f))

    Box(
        modifier = modifier
            .size(UserLocationBoxSize)
            .graphicsLayer {
                scaleX = appear.value
                scaleY = appear.value
                alpha = appear.value.coerceIn(0f, 1f)
            }
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                ) else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        val pulseSize = 18.dp + (22.dp * pulseProgress)
        Box(
            modifier = Modifier
                .size(pulseSize)
                .clip(PxClipShape)
                .background(UserLocationBlue.copy(alpha = (1f - pulseProgress) * 0.38f))
        )

        Box(
            modifier = Modifier.offset(y = -lift),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .offset(y = 2.dp)
                    .size(22.dp)
                    .clip(PxClipShape)
                    .background(Ink.copy(alpha = 0.16f))
            )
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(PxClipShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(PxClipShape)
                        .background(UserLocationBlue),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .offset(x = (-3).dp, y = (-3).dp)
                            .size(4.dp)
                            .background(UserLocationHighlight)
                    )
                }
            }
        }
    }
}
