package de.koenidv.spheres.ui.components

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

class SteppedPolygonShape(
    val steps: Int = 3,
    val stepDp: Dp = 4.dp,
    val insetDp: Dp = 0.dp,
    val topOnly: Boolean = false
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val s = with(density) { stepDp.toPx() }
        val inset = with(density) { insetDp.toPx() }
        val r = steps * s
        val w = size.width
        val h = size.height

        fun l(v: Float) = v + inset
        fun rx(v: Float) = w - (v + inset)
        fun ry(v: Float) = h - (v + inset)

        val path = Path()

        path.moveTo(l(0f), l(r))
        for (k in 0 until steps) {
            path.lineTo(l(k * s), l(r - k * s))
            path.lineTo(l((k + 1) * s), l(r - k * s))
        }
        path.lineTo(l(r), l(0f))
        path.lineTo(rx(r), l(0f))

        for (k in 0 until steps) {
            path.lineTo(rx(r - k * s), l((k + 1) * s))
            path.lineTo(rx(r - (k + 1) * s), l((k + 1) * s))
        }

        if (topOnly) {
            path.lineTo(rx(0f), ry(0f))
            path.lineTo(l(0f), ry(0f))
            path.close()
            return Outline.Generic(path)
        }

        path.lineTo(rx(0f), ry(r))
        for (k in 0 until steps) {
            path.lineTo(rx((k + 1) * s), ry(r - k * s))
            path.lineTo(rx((k + 1) * s), ry(r - (k + 1) * s))
        }
        path.lineTo(rx(r), ry(0f))
        path.lineTo(l(r), ry(0f))

        for (k in 0 until steps) {
            path.lineTo(l(r - k * s), ry((k + 1) * s))
            path.lineTo(l(r - (k + 1) * s), ry((k + 1) * s))
        }
        path.close()

        return Outline.Generic(path)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SteppedPolygonShape) return false
        return steps == other.steps &&
            stepDp == other.stepDp &&
            insetDp == other.insetDp &&
            topOnly == other.topOnly
    }

    override fun hashCode(): Int {
        var result = steps
        result = 31 * result + stepDp.hashCode()
        result = 31 * result + insetDp.hashCode()
        result = 31 * result + topOnly.hashCode()
        return result
    }
}

val PxClipShape = SteppedPolygonShape(steps = 1)
val PxClipInShape = SteppedPolygonShape(steps = 1, insetDp = 1.dp)
val PanelClipShape = SteppedPolygonShape(steps = 3)
val PanelClipInnerShape = SteppedPolygonShape(steps = 3, insetDp = 1.dp)
val SheetClipShape = SteppedPolygonShape(steps = 3, topOnly = true)
