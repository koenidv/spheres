package de.koenidv.spheres.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.round

fun healthColor(score: Int): Color = when {
    score >= 60 -> Color(0xFF3FBF9F)
    score >= 30 -> Color(0xFFF0A83C)
    else -> Color(0xFFE36F73)
}

private const val SEGMENTS = 6

@Composable
fun HealthBar(score: Int, width: Dp = 36.dp, modifier: Modifier = Modifier) {
    val filled = ceil((score / 100.0) * SEGMENTS).toInt().coerceIn(0, SEGMENTS)
    val color = healthColor(score)
    val emptyColor = Color.Black.copy(alpha = 0.12f)

    Canvas(modifier = modifier.size(width = width, height = 4.dp)) {
        val totalGaps = (SEGMENTS - 1) * 1.dp.toPx()
        val segW = (size.width - totalGaps) / SEGMENTS.toFloat()
        val gapPx = 1.dp.toPx()
        val h = size.height
        for (i in 0 until SEGMENTS) {
            val left = i * (segW + gapPx)
            drawRect(
                color = if (i < filled) color else emptyColor,
                topLeft = Offset(left, 0f),
                size = Size(segW, h)
            )
        }
    }
}

data class RingPixel(val x: Int, val y: Int, val angle: Double)

val HEALTH_RING_PIXELS: List<RingPixel> = run {
    val c = 4.0
    val cells = mutableListOf<RingPixel>()
    for (y in 0 until 9) {
        for (x in 0 until 9) {
            val d = hypot(x - c, y - c)
            if (abs(d - 3.6) < 0.6) {
                val a = (atan2(x - c, c - y) + 2 * PI) % (2 * PI)
                cells.add(RingPixel(x, y, a))
            }
        }
    }
    cells.sortBy { it.angle }
    cells
}

@Composable
fun HealthRing(score: Int, size: Dp = 18.dp, modifier: Modifier = Modifier) {
    val filled = round((score / 100.0) * HEALTH_RING_PIXELS.size).toInt().coerceIn(0, HEALTH_RING_PIXELS.size)
    val color = healthColor(score)
    val emptyColor = Color.Black.copy(alpha = 0.12f)

    Canvas(modifier = modifier.size(size)) {
        val step = this.size.width / 9f
        HEALTH_RING_PIXELS.forEachIndexed { i, p ->
            drawRect(
                color = if (i < filled) color else emptyColor,
                topLeft = Offset(p.x * step, p.y * step),
                size = Size(step, step)
            )
        }
    }
}
