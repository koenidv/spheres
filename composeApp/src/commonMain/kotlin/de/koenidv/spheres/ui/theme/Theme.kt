package de.koenidv.spheres.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import de.koenidv.spheres.resources.*
import org.jetbrains.compose.resources.Font

val Ink = Color(0xFF000000)
val Muted = Color(0xFF6B6B6B)
val Line = Color(0xFFECECEC)
val CardBg = Color(0xFFF6F6F6)
val FieldBg = Color(0xFFF3F3F3)
val Scrim = Color(0xFFC1C1C1)
val WeeklyPink = Color(0xFFEB78B0)
val BiweeklyCoral = Color(0xFFE36F73)
val MonthlyPurple = Color(0xFF9D7CE7)
val Coral = Color(0xFFE36F73)
val Amber = Color(0xFFF0A83C)
val DangerText = Color(0xFFC2383F)
val DangerFill = Color(0xFFFDE8E9)
val DangerShadow = Color(0xFFF2B8BB)

fun parseHexColor(hex: String): Color {
    val clean = hex.removePrefix("#").trim()
    return when (clean.length) {
        3 -> {
            val r = clean[0].digitToInt(16) * 17
            val g = clean[1].digitToInt(16) * 17
            val b = clean[2].digitToInt(16) * 17
            Color(r, g, b)
        }
        4 -> {
            val r = clean[0].digitToInt(16) * 17
            val g = clean[1].digitToInt(16) * 17
            val b = clean[2].digitToInt(16) * 17
            val a = clean[3].digitToInt(16) * 17
            Color(r, g, b, a)
        }
        6 -> {
            val num = clean.toLong(16)
            Color(0xFF000000L or num)
        }
        8 -> {
            val num = clean.toLong(16)
            val r = ((num shr 24) and 0xFF).toInt()
            val g = ((num shr 16) and 0xFF).toInt()
            val b = ((num shr 8) and 0xFF).toInt()
            val a = (num and 0xFF).toInt()
            Color(r, g, b, a)
        }
        else -> Color.Black
    }
}

fun mixColors(a: Color, b: Color, amountA: Float): Color {
    val clamped = amountA.coerceIn(0f, 1f)
    val amountB = 1f - clamped
    return Color(
        red = a.red * clamped + b.red * amountB,
        green = a.green * clamped + b.green * amountB,
        blue = a.blue * clamped + b.blue * amountB,
        alpha = a.alpha * clamped + b.alpha * amountB
    )
}

fun tintColor(color: Color, pct: Int = 20): Color {
    return mixColors(color, Color.White, pct / 100f)
}

@Composable
fun PixelFontFamily(): FontFamily {
    return FontFamily(
        Font(Res.font.pixelify_sans_regular, FontWeight.Normal),
        Font(Res.font.pixelify_sans_medium, FontWeight.Medium),
        Font(Res.font.pixelify_sans_semibold, FontWeight.SemiBold),
        Font(Res.font.pixelify_sans_bold, FontWeight.Bold),
    )
}

@Composable
fun SansFontFamily(): FontFamily {
    return FontFamily(
        Font(Res.font.inter_regular, FontWeight.Normal),
        Font(Res.font.inter_medium, FontWeight.Medium),
        Font(Res.font.inter_semibold, FontWeight.SemiBold),
        Font(Res.font.inter_bold, FontWeight.Bold),
    )
}

private val LightColorScheme = lightColorScheme(
    primary = Ink,
    onPrimary = Color.White,
    secondary = Muted,
    onSecondary = Color.White,
    background = Color.White,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = CardBg,
    onSurfaceVariant = Ink,
    outline = Line,
    error = DangerText,
    onError = Color.White
)

@Composable
fun SpheresTheme(content: @Composable () -> Unit) {
    val sansFont = SansFontFamily()
    val typography = Typography(
        bodyLarge = TextStyle(
            fontFamily = sansFont,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 24.sp
        ),
        bodyMedium = TextStyle(
            fontFamily = sansFont,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 20.sp
        ),
        bodySmall = TextStyle(
            fontFamily = sansFont,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            lineHeight = 16.sp
        ),
        titleLarge = TextStyle(
            fontFamily = sansFont,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            lineHeight = 28.sp
        ),
        titleMedium = TextStyle(
            fontFamily = sansFont,
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp,
            lineHeight = 24.sp
        )
    )

    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = typography
    ) {
        Surface(
            color = Color.White,
            contentColor = Ink,
            content = content
        )
    }
}
