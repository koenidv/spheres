package de.koenidv.spheres.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MapGeoTest {
    @Test
    fun peopleAtTheSameSpotShareOneCluster() {
        val c = clusterPoints(listOf(MapPoint("a", 100f, 50f), MapPoint("b", 100f, 50f)), radius = 1f)
        assertEquals(1, c.size)
        assertEquals(2, c[0].size)
        assertEquals(0f, c[0].spread)
    }

    @Test
    fun farApartPointsStaySeparateUntilRadiusGrows() {
        val pts = listOf(MapPoint("a", 100f, 50f), MapPoint("b", 110f, 50f), MapPoint("c", 400f, 200f))
        assertEquals(3, clusterPoints(pts, radius = 5f).size)
        val merged = clusterPoints(pts, radius = 12f)
        assertEquals(2, merged.size)
        assertTrue(merged.any { it.size == 2 && it.ids.toSet() == setOf("a", "b") })
    }

    @Test
    fun resultingClustersNeverOverlap() {
        val pts = (0 until 40).map { MapPoint("p$it", (it * 7 % 31).toFloat(), (it * 13 % 17).toFloat()) }
        val radius = 5f
        val out = clusterPoints(pts, radius)
        assertEquals(pts.size, out.sumOf { it.size })
        for (i in out.indices) for (j in i + 1 until out.size) {
            val du = out[i].u - out[j].u
            val dv = out[i].v - out[j].v
            assertTrue(du * du + dv * dv > radius * radius)
        }
    }

    @Test
    fun clusterKeyIsOrderIndependent() {
        val a = clusterPoints(listOf(MapPoint("a", 1f, 1f), MapPoint("b", 1f, 1f)), 1f)[0]
        val b = clusterPoints(listOf(MapPoint("b", 1f, 1f), MapPoint("a", 1f, 1f)), 1f)[0]
        assertEquals(a.key, b.key)
    }

    @Test
    fun projectionMatchesMaskGridAndLandLooksRight() {
        assertEquals(360f, MapGeo.lngToU(0.0))
        assertEquals(168f, MapGeo.latToV(0.0))
        // Berlin is land, mid-Atlantic is water.
        val berlin = WorldMask.coverage(0, MapGeo.lngToU(13.4).toInt(), MapGeo.latToV(52.5).toInt())
        val atlantic = WorldMask.coverage(0, MapGeo.lngToU(-30.0).toInt(), MapGeo.latToV(30.0).toInt())
        assertEquals(1f, berlin)
        assertEquals(0f, atlantic)
    }
}

class CountryMaskTest {
    @Test
    fun neighbouringCountriesDiffer() {
        fun at(lng: Double, lat: Double) = WorldMask.countryAt(MapGeo.lngToU(lng).toInt(), MapGeo.latToV(lat).toInt())
        val germany = at(10.0, 51.0)
        val france = at(2.5, 47.0)
        assertTrue(germany != 0 && france != 0 && germany != france)
        assertEquals(0, at(-30.0, 30.0))
    }
}
