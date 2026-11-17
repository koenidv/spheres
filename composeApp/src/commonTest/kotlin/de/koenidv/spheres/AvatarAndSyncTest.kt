package de.koenidv.spheres

import de.koenidv.spheres.data.FirestoreBackend
import de.koenidv.spheres.domain.AvatarConfig
import de.koenidv.spheres.domain.Person
import de.koenidv.spheres.ui.components.GlassesStyle
import de.koenidv.spheres.ui.components.HairStyle
import de.koenidv.spheres.ui.components.clampCropOffset
import de.koenidv.spheres.ui.components.computeBaseScale
import de.koenidv.spheres.ui.components.computeCropBounds
import de.koenidv.spheres.ui.components.defaultAvatarConfig
import de.koenidv.spheres.ui.components.getAvatarRuns
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AvatarAndSyncTest {

    @Test
    fun testDefaultAvatarMatchesExplicitDefaultConfig() {
        val defaultCfg = defaultAvatarConfig("Alex")
        val runsImplicit = getAvatarRuns("Alex", "#9d7ce7", null)
        val runsExplicit = getAvatarRuns("Alex", "#9d7ce7", defaultCfg)
        assertEquals(runsImplicit, runsExplicit)

        val shadesCfg = defaultCfg.copy(glasses = GlassesStyle.SHADES.id)
        val runsShades = getAvatarRuns("Alex", "#9d7ce7", shadesCfg)
        if (defaultCfg.glasses != GlassesStyle.SHADES.id) {
            assertNotEquals(runsImplicit, runsShades)
        }

        val baldCfg = defaultCfg.copy(hairStyle = HairStyle.BALD.id)
        val runsBald = getAvatarRuns("Alex", "#9d7ce7", baldCfg)
        if (defaultCfg.hairStyle != HairStyle.BALD.id) {
            assertNotEquals(runsImplicit, runsBald)
        }
    }

    @Test
    fun testPersonFirestoreRoundTripWithAvatarAndPhoto() {
        val backend = FirestoreBackend()
        val person = Person(
            id = "p_avatar",
            name = "Jordan",
            intervalDays = 14,
            avatar = AvatarConfig(
                skin = 2,
                hairStyle = HairStyle.CURLY.id,
                hairColor = 4,
                glasses = GlassesStyle.SHADES.id,
            ),
            photoUrl = "https://firebasestorage.googleapis.com/v0/b/friendly-spheres.firebasestorage.app/o/users%2Fu1%2Favatars%2Fp_avatar_123.jpg?alt=media&token=abc",
            createdAt = 1_700_000_000_000L,
        )

        val fields = backend.encodeFirestoreFields(person)
        val doc = buildJsonObject {
            put("name", JsonPrimitive("projects/friendly-spheres/databases/(default)/documents/users/u1/people/p_avatar"))
            put("fields", fields)
        }

        val decoded = backend.decodeFirestoreDoc<Person>(doc)
        assertNotNull(decoded)
        assertEquals(person, decoded)
    }

    @Test
    fun testLegacyPersonJsonWithoutAvatarFieldsDecodesCleanly() {
        val json = Json { ignoreUnknownKeys = true }
        val legacy = """{"id":"p_old","name":"Sam","createdAt":1700000000000}"""
        val person = json.decodeFromString<Person>(legacy)
        assertEquals("p_old", person.id)
        assertEquals("Sam", person.name)
        assertNull(person.avatar)
        assertNull(person.photoUrl)
    }

    @Test
    fun testCropMathCoversFrameAndClampsOffsets() {
        val base = computeBaseScale(imgW = 1000f, imgH = 1500f, cropSizePx = 300f)
        assertEquals(0.3f, base, 0.0001f)

        val clamped = clampCropOffset(
            offsetX = 50f,
            offsetY = 200f,
            imgW = 1000f,
            imgH = 1500f,
            cropSizePx = 300f,
            scale = base
        )
        assertEquals(0f, clamped.x, 0.0001f)
        assertEquals(75f, clamped.y, 0.0001f)

        val center = computeCropBounds(
            imgW = 1000f,
            imgH = 1500f,
            cropSizePx = 300f,
            scale = base,
            offsetX = 0f,
            offsetY = 0f
        )
        assertEquals(0f, center.leftFrac, 0.0001f)
        assertEquals(250f / 1500f, center.topFrac, 0.0001f)
        assertEquals(1f, center.widthFrac, 0.0001f)
        assertEquals(1000f / 1500f, center.heightFrac, 0.0001f)

        val topEdge = computeCropBounds(
            imgW = 1000f,
            imgH = 1500f,
            cropSizePx = 300f,
            scale = base,
            offsetX = 0f,
            offsetY = 75f
        )
        assertEquals(0f, topEdge.topFrac, 0.0001f)

        val bottomEdge = computeCropBounds(
            imgW = 1000f,
            imgH = 1500f,
            cropSizePx = 300f,
            scale = base,
            offsetX = 0f,
            offsetY = -75f
        )
        assertEquals(500f / 1500f, bottomEdge.topFrac, 0.0001f)
    }
}
