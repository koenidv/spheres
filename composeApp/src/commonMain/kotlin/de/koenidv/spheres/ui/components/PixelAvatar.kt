package de.koenidv.spheres.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.koenidv.spheres.data.AvatarPhotoCache
import de.koenidv.spheres.domain.AvatarConfig
import de.koenidv.spheres.domain.Person
import de.koenidv.spheres.ui.theme.parseHexColor
import kotlin.random.Random

val AVATAR_SKINS = listOf(
    Color(0xFFFFDFC4),
    Color(0xFFF7CFA9),
    Color(0xFFE6B48A),
    Color(0xFFC98F66),
    Color(0xFF9A6544),
    Color(0xFF6B4430)
)

val AVATAR_HAIRS = listOf(
    Color(0xFF241812),
    Color(0xFF4A2F20),
    Color(0xFF7A4A24),
    Color(0xFFD9A94E),
    Color(0xFFC0482B),
    Color(0xFF9A9A9A),
    Color(0xFF111111),
    Color(0xFFF06FB0),
    Color(0xFF4F8DF0)
)

private val EYE = Color(0xFF1A1A1A)
private val MOUTH = Color(0xFF8A3A3A)
private val GLASS = Color(0xFF1F1F1F)
private val CHEEK = Color(0xFFF4A3A3)

enum class HairStyle(val id: String, val label: String) {
    SHORT("short", "Short"),
    LONG("long", "Long"),
    BUN("bun", "Bun"),
    SPIKY("spiky", "Spiky"),
    SIDE("side", "Side"),
    CURLY("curly", "Curly"),
    BALD("bald", "Bald");

    companion object {
        fun fromId(id: String?): HairStyle? = entries.firstOrNull { it.id == id }
    }
}

enum class GlassesStyle(val id: String, val label: String) {
    NONE("none", "None"),
    GLASSES("glasses", "Glasses"),
    SHADES("shades", "Shades");

    companion object {
        fun fromId(id: String?): GlassesStyle? = entries.firstOrNull { it.id == id }
    }
}

internal data class ResolvedAvatarTraits(
    val skinIndex: Int,
    val hairStyle: HairStyle,
    val hairColorIndex: Int,
    val glasses: GlassesStyle,
)

private fun defaultTraitsForName(name: String): ResolvedAvatarTraits {
    val rng = AvatarRng(fnv1a(name.trim().lowercase()) xor 0x9E3779B9u)
    val skinIdx = rng.next(AVATAR_SKINS.size)
    val hairIdx = rng.next(AVATAR_HAIRS.size)
    val styles = HairStyle.entries
    val style = styles[rng.next(styles.size)]
    val acc = rng.next(100)
    val glasses = when {
        acc < 9 -> GlassesStyle.SHADES
        acc < 16 -> GlassesStyle.GLASSES
        else -> GlassesStyle.NONE
    }
    return ResolvedAvatarTraits(
        skinIndex = skinIdx,
        hairStyle = style,
        hairColorIndex = hairIdx,
        glasses = glasses,
    )
}

fun defaultAvatarConfig(name: String): AvatarConfig {
    val traits = defaultTraitsForName(name)
    return AvatarConfig(
        skin = traits.skinIndex,
        hairStyle = traits.hairStyle.id,
        hairColor = traits.hairColorIndex,
        glasses = traits.glasses.id,
    )
}

fun randomAvatarConfig(): AvatarConfig {
    val styles = HairStyle.entries
    val acc = Random.nextInt(100)
    val glasses = when {
        acc < 22 -> GlassesStyle.SHADES
        acc < 45 -> GlassesStyle.GLASSES
        else -> GlassesStyle.NONE
    }
    return AvatarConfig(
        skin = Random.nextInt(AVATAR_SKINS.size),
        hairStyle = styles[Random.nextInt(styles.size)].id,
        hairColor = Random.nextInt(AVATAR_HAIRS.size),
        glasses = glasses.id,
    )
}

private fun resolveTraits(name: String, config: AvatarConfig?): ResolvedAvatarTraits {
    val base = defaultTraitsForName(name)
    if (config == null) return base
    return ResolvedAvatarTraits(
        skinIndex = config.skin?.coerceIn(0, AVATAR_SKINS.lastIndex) ?: base.skinIndex,
        hairStyle = HairStyle.fromId(config.hairStyle) ?: base.hairStyle,
        hairColorIndex = config.hairColor?.coerceIn(0, AVATAR_HAIRS.lastIndex) ?: base.hairColorIndex,
        glasses = GlassesStyle.fromId(config.glasses) ?: base.glasses,
    )
}

private fun fnv1a(str: String): UInt {
    var h = 2166136261u
    for (ch in str) {
        h = h xor ch.code.toUInt()
        h *= 16777619u
    }
    return h
}

private class AvatarRng(seed: UInt) {
    private var s: UInt = if (seed == 0u) 1u else seed

    fun next(n: Int): Int {
        s = s xor (s shl 13)
        s = s xor (s shr 17)
        s = s xor (s shl 5)
        return (s % n.toUInt()).toInt()
    }
}

private typealias AvatarGrid = Array<Array<Color?>>

private fun blankGrid(): AvatarGrid = Array(12) { Array(12) { null } }

private fun fill(g: AvatarGrid, y: Int, x0: Int, x1: Int, c: Color) {
    for (x in x0..x1) g[y][x] = c
}

private fun px(g: AvatarGrid, y: Int, xs: IntArray, c: Color) {
    for (x in xs) g[y][x] = c
}

private fun hairBack(g: AvatarGrid, style: HairStyle, c: Color) {
    if (style == HairStyle.LONG) {
        for (y in 2..9) fill(g, y, 1, 10, c)
    }
    if (style == HairStyle.CURLY) {
        fill(g, 0, 4, 7, c)
        fill(g, 1, 2, 9, c)
        for (y in 2..6) fill(g, y, 1, 10, c)
    }
}

private fun hairFront(g: AvatarGrid, style: HairStyle, c: Color) {
    when (style) {
        HairStyle.SHORT, HairStyle.LONG -> {
            fill(g, 1, 3, 8, c)
            fill(g, 2, 2, 9, c)
            px(g, 3, intArrayOf(2, 3, 8, 9), c)
        }
        HairStyle.BUN -> {
            fill(g, 0, 4, 7, c)
            fill(g, 1, 3, 8, c)
            fill(g, 2, 2, 9, c)
            px(g, 3, intArrayOf(2, 3, 8, 9), c)
        }
        HairStyle.SPIKY -> {
            px(g, 0, intArrayOf(3, 5, 6, 8), c)
            fill(g, 1, 2, 9, c)
            fill(g, 2, 2, 9, c)
            px(g, 3, intArrayOf(2, 3, 8, 9), c)
        }
        HairStyle.SIDE -> {
            fill(g, 1, 3, 8, c)
            fill(g, 2, 2, 9, c)
            fill(g, 3, 2, 6, c)
            px(g, 3, intArrayOf(9), c)
            px(g, 4, intArrayOf(2, 9), c)
        }
        HairStyle.CURLY -> {
            fill(g, 2, 2, 9, c)
            px(g, 3, intArrayOf(2, 3, 8, 9), c)
        }
        HairStyle.BALD -> {}
    }
}

private fun buildAvatarGrid(traits: ResolvedAvatarTraits, shirt: Color): AvatarGrid {
    val skin = AVATAR_SKINS[traits.skinIndex]
    val hair = AVATAR_HAIRS[traits.hairColorIndex]
    val style = traits.hairStyle
    val g = blankGrid()

    hairBack(g, style, hair)

    fill(g, 9, 2, 9, shirt)
    fill(g, 10, 1, 10, shirt)
    fill(g, 11, 0, 11, shirt)
    px(g, 9, intArrayOf(5, 6), skin)

    fill(g, 2, 3, 8, skin)
    for (y in 3..6) fill(g, y, 2, 9, skin)
    fill(g, 7, 3, 8, skin)
    fill(g, 8, 4, 7, skin)

    px(g, 6, intArrayOf(3, 8), CHEEK)
    px(g, 5, intArrayOf(4, 7), EYE)
    px(g, 7, intArrayOf(5, 6), MOUTH)

    hairFront(g, style, hair)

    when (traits.glasses) {
        GlassesStyle.SHADES -> {
            fill(g, 5, 3, 8, GLASS)
            px(g, 4, intArrayOf(3, 4, 5, 6, 7, 8), GLASS)
        }
        GlassesStyle.GLASSES -> {
            fill(g, 4, 3, 8, GLASS)
            px(g, 5, intArrayOf(3, 5, 6, 8), GLASS)
            px(g, 5, intArrayOf(4, 7), EYE)
        }
        GlassesStyle.NONE -> Unit
    }

    return g
}

data class AvatarRun(val x: Int, val y: Int, val w: Int, val color: Color)

private val avatarRunCache = mutableMapOf<String, List<AvatarRun>>()

fun getAvatarRuns(
    name: String,
    colorHex: String,
    config: AvatarConfig? = null
): List<AvatarRun> {
    val traits = resolveTraits(name, config)
    val key = "${traits.skinIndex}|${traits.hairStyle.id}|${traits.hairColorIndex}|${traits.glasses.id}|$colorHex"
    return avatarRunCache.getOrPut(key) {
        val shirt = parseHexColor(colorHex)
        val g = buildAvatarGrid(traits, shirt)
        val out = mutableListOf<AvatarRun>()
        for (y in 0 until 12) {
            var x = 0
            while (x < 12) {
                val c = g[y][x]
                if (c == null) {
                    x++
                    continue
                }
                var w = 1
                while (x + w < 12 && g[y][x + w] == c) {
                    w++
                }
                out.add(AvatarRun(x, y, w, c))
                x += w
            }
        }
        out
    }
}

@Composable
fun PixelAvatar(
    name: String,
    colorHex: String,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier,
    config: AvatarConfig? = null,
) {
    val runs = remember(name, colorHex, config) { getAvatarRuns(name, colorHex, config) }
    Canvas(modifier = modifier.size(size)) {
        val stepX = this.size.width / 12f
        val stepY = this.size.height / 12f
        for (r in runs) {
            drawRect(
                color = r.color,
                topLeft = Offset(r.x * stepX, r.y * stepY),
                size = Size(r.w * stepX, stepY)
            )
        }
    }
}

@Composable
fun AvatarRender(
    name: String,
    colorHex: String,
    config: AvatarConfig? = null,
    photoUrl: String? = null,
    photoOverride: ImageBitmap? = null,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier,
) {
    val resolvedBitmap = photoOverride ?: AvatarPhotoCache.rememberPhotoBitmap(photoUrl)
    if (resolvedBitmap != null) {
        Image(
            bitmap = resolvedBitmap,
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(PxClipShape)
        )
    } else {
        PixelAvatar(
            name = name,
            colorHex = colorHex,
            size = size,
            modifier = modifier,
            config = config,
        )
    }
}

@Composable
fun PersonAvatar(
    person: Person,
    colorHex: String,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier,
) {
    AvatarRender(
        name = person.name,
        colorHex = colorHex,
        config = person.avatar,
        photoUrl = person.photoUrl,
        size = size,
        modifier = modifier,
    )
}
