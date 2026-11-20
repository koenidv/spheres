package de.koenidv.spheres.domain

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * World space of the pixel map: one unit is one cell of the land mask (0.5 degrees),
 * `u` runs west to east, `v` runs north to south.
 */
object MapGeo {
    const val WORLD_W = WORLD_MASK_COLS.toFloat()
    const val WORLD_H = WORLD_MASK_ROWS.toFloat()
    private const val CELLS_PER_DEGREE = 2f
    private const val TOP_LAT = 84.0
    private const val BOTTOM_LAT = TOP_LAT - WORLD_MASK_ROWS / CELLS_PER_DEGREE

    fun lngToU(lng: Double): Float = ((lng.coerceIn(-180.0, 180.0) + 180.0) * CELLS_PER_DEGREE).toFloat()

    fun latToV(lat: Double): Float = ((TOP_LAT - lat.coerceIn(BOTTOM_LAT, TOP_LAT)) * CELLS_PER_DEGREE).toFloat()
}

data class MapPoint(val id: String, val u: Float, val v: Float)

data class MapCluster(
    val ids: List<String>,
    val u: Float,
    val v: Float,
    val minU: Float,
    val maxU: Float,
    val minV: Float,
    val maxV: Float,
) {
    val size: Int get() = ids.size

    /** Stable across recomputations as long as the same people are grouped together. */
    val key: String get() = ids.sorted().joinToString("|")

    /** Largest extent of the members, in world cells. Zero when everyone shares one spot. */
    val spread: Float get() = max(maxU - minU, maxV - minV)
}

private class ClusterBuilder(first: MapPoint) {
    val members = mutableListOf(first)
    var u = first.u
    var v = first.v

    fun add(p: MapPoint) {
        members += p
        u += (p.u - u) / members.size
        v += (p.v - v) / members.size
    }

    fun absorb(other: ClusterBuilder) {
        val total = members.size + other.members.size
        u = (u * members.size + other.u * other.members.size) / total
        v = (v * members.size + other.v * other.members.size) / total
        members += other.members
    }

    fun distanceTo(ou: Float, ov: Float): Float {
        val du = u - ou
        val dv = v - ov
        return sqrt(du * du + dv * dv)
    }

    fun build() = MapCluster(
        ids = members.map { it.id },
        u = u,
        v = v,
        minU = members.minOf { it.u },
        maxU = members.maxOf { it.u },
        minV = members.minOf { it.v },
        maxV = members.maxOf { it.v },
    )
}

/**
 * Groups points that lie within [radius] world cells of each other. Points are visited in a fixed
 * order so the grouping doesn't flicker between recompositions, and overlapping groups are merged
 * until none are left.
 */
fun clusterPoints(points: List<MapPoint>, radius: Float): List<MapCluster> {
    val builders = mutableListOf<ClusterBuilder>()
    for (p in points.sortedWith(compareBy({ it.u }, { it.v }, { it.id }))) {
        val nearest = builders
            .map { it to it.distanceTo(p.u, p.v) }
            .filter { it.second <= radius }
            .minByOrNull { it.second }
            ?.first
        if (nearest != null) nearest.add(p) else builders += ClusterBuilder(p)
    }

    var merged = true
    while (merged) {
        merged = false
        outer@ for (i in builders.indices) {
            for (j in i + 1 until builders.size) {
                if (builders[i].distanceTo(builders[j].u, builders[j].v) <= radius) {
                    builders[i].absorb(builders[j])
                    builders.removeAt(j)
                    merged = true
                    break@outer
                }
            }
        }
    }
    return builders.map { it.build() }
}

/** Camera that frames a bounding box, as centre and pixels-per-cell. */
data class MapFrame(val u: Float, val v: Float, val scale: Float)

fun frameBounds(
    minU: Float,
    maxU: Float,
    minV: Float,
    maxV: Float,
    viewW: Float,
    viewH: Float,
    paddingPx: Float,
    minScale: Float,
    maxScale: Float,
): MapFrame {
    val w = max(maxU - minU, 0.001f)
    val h = max(maxV - minV, 0.001f)
    val fit = min((viewW - paddingPx * 2f) / w, (viewH - paddingPx * 2f) / h)
    return MapFrame(
        u = (minU + maxU) / 2f,
        v = (minV + maxV) / 2f,
        scale = fit.coerceIn(minScale, maxScale),
    )
}
