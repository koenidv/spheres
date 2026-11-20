package de.koenidv.spheres.domain

import kotlin.math.floor

/**
 * Land/water mask of the world with box-filtered coarser levels, so the map can be drawn on a
 * fixed pixel grid at any zoom without losing coastlines to aliasing.
 */
object WorldMask {
    private val levels: Array<FloatArray>
    private val levelCols: IntArray
    private val levelRows: IntArray

    init {
        val base = FloatArray(WORLD_MASK_COLS * WORLD_MASK_ROWS)
        WORLD_MASK_RLE.split(';').forEachIndexed { y, row ->
            var x = 0
            var land = false
            for (run in row.split(',')) {
                val n = run.toInt(36)
                if (land) for (i in x until x + n) base[y * WORLD_MASK_COLS + i] = 1f
                x += n
                land = !land
            }
        }

        val all = mutableListOf(base)
        val cols = mutableListOf(WORLD_MASK_COLS)
        val rows = mutableListOf(WORLD_MASK_ROWS)
        while (cols.last() > 1 && all.size < MAX_LEVEL + 1) {
            val pc = cols.last()
            val pr = rows.last()
            val prev = all.last()
            val nc = (pc + 1) / 2
            val nr = (pr + 1) / 2
            val next = FloatArray(nc * nr)
            for (y in 0 until nr) {
                for (x in 0 until nc) {
                    var sum = 0f
                    var count = 0
                    for (dy in 0..1) for (dx in 0..1) {
                        val sx = x * 2 + dx
                        val sy = y * 2 + dy
                        if (sx < pc && sy < pr) {
                            sum += prev[sy * pc + sx]
                            count++
                        }
                    }
                    next[y * nc + x] = sum / count
                }
            }
            all += next
            cols += nc
            rows += nr
        }
        levels = all.toTypedArray()
        levelCols = cols.toIntArray()
        levelRows = rows.toIntArray()
    }

    /** Land coverage (0..1) of a cell of a coarser [level], where each cell spans 2^level mask cells. */
    fun coverage(level: Int, x: Int, y: Int): Float {
        val l = level.coerceIn(0, levels.size - 1)
        if (x < 0 || y < 0 || x >= levelCols[l] || y >= levelRows[l]) return 0f
        return levels[l][y * levelCols[l] + x]
    }

    /** Smoothly interpolated land value at a world position, used when zoomed in past the mask resolution. */
    fun sample(u: Float, v: Float): Float {
        val fu = u - 0.5f
        val fv = v - 0.5f
        val x0 = floor(fu).toInt()
        val y0 = floor(fv).toInt()
        val tx = fu - x0
        val ty = fv - y0
        val a = coverage(0, x0, y0)
        val b = coverage(0, x0 + 1, y0)
        val c = coverage(0, x0, y0 + 1)
        val d = coverage(0, x0 + 1, y0 + 1)
        return (a * (1 - tx) + b * tx) * (1 - ty) + (c * (1 - tx) + d * tx) * ty
    }

    private const val MAX_LEVEL = 7

    private val countries: ShortArray by lazy {
        val out = ShortArray(WORLD_MASK_COLS * WORLD_MASK_ROWS)
        WORLD_COUNTRY_RLE.split(';').forEachIndexed { y, row ->
            var x = 0
            for (token in row.split(',')) {
                val dot = token.indexOf('.')
                val id = token.substring(0, dot).toInt(36).toShort()
                val n = token.substring(dot + 1).toInt(36)
                for (i in x until x + n) out[y * WORLD_MASK_COLS + i] = id
                x += n
            }
        }
        out
    }

    /** Country id at a mask cell, 0 where unknown or out of range. */
    fun countryAt(x: Int, y: Int): Int {
        if (x < 0 || y < 0 || x >= WORLD_MASK_COLS || y >= WORLD_MASK_ROWS) return 0
        return countries[y * WORLD_MASK_COLS + x].toInt()
    }
}
