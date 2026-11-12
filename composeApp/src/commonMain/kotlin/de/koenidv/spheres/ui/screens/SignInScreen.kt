package de.koenidv.spheres.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.koenidv.spheres.data.SpheresStore
import de.koenidv.spheres.domain.INTERVALS
import de.koenidv.spheres.ui.components.ButtonSize
import de.koenidv.spheres.ui.components.ButtonVariant
import de.koenidv.spheres.ui.components.PixelAvatar
import de.koenidv.spheres.ui.components.PixelButton
import de.koenidv.spheres.ui.components.arcOffset
import de.koenidv.spheres.ui.theme.DangerText
import de.koenidv.spheres.ui.theme.Ink
import de.koenidv.spheres.ui.theme.PixelFontFamily
import de.koenidv.spheres.ui.theme.SansFontFamily
import de.koenidv.spheres.ui.theme.parseHexColor
import de.koenidv.spheres.ui.theme.tintColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

private const val STEP = 4f

private data class BandPathData(
    val strokeColor: Color,
    val fillColor: Color,
    val strokePath: Path,
    val fillPath: Path
)

private fun buildArcPaths(wDp: Float, arcTopDp: Float, hDp: Float, density: Float): Pair<Path, Path> {
    val strokePath = Path()
    val fillPath = Path()
    val cx = floor(wDp / 2f)
    val radius = 1.7f * wDp
    val b0 = floor(-cx / STEP).toInt()
    val b1 = ceil((wDp - cx) / STEP).toInt()

    var lastY = -1f
    for (b in b0 until b1) {
        val x0 = max(0f, cx + b * STEP) * density
        val x1 = min(wDp, cx + (b + 1) * STEP) * density
        if (x1 <= x0) continue
        val y = (arcTopDp + arcOffset(radius, cx, cx + b * STEP + 1f)) * density
        if (lastY < 0f) {
            strokePath.moveTo(x0, y)
            fillPath.moveTo(x0, y)
        } else if (y != lastY) {
            strokePath.lineTo(x0, y)
            fillPath.lineTo(x0, y)
        }
        strokePath.lineTo(x1, y)
        fillPath.lineTo(x1, y)
        lastY = y
    }
    fillPath.lineTo(wDp * density, (hDp + 100f) * density)
    fillPath.lineTo(0f, (hDp + 100f) * density)
    fillPath.close()

    return Pair(strokePath, fillPath)
}

private val G_GRID = listOf(
    "..BBBBBB..",
    ".BBBBBBBB.",
    "RRR....BBB",
    "RR......BB",
    "RR.YYYYYYY",
    "RR.YYYYYYY",
    "RR......GG",
    "RRR....GGG",
    ".RRGGGGGG.",
    "..GGGGGG.."
)

private val G_COLORS = mapOf(
    'R' to Color(0xFFEA4335),
    'B' to Color(0xFF4285F4),
    'Y' to Color(0xFFFBBC05),
    'G' to Color(0xFF34A853)
)

@Composable
fun PixelG(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(20.dp)) {
        val stepX = size.width / 10f
        val stepY = size.height / 10f
        G_GRID.forEachIndexed { y, row ->
            row.forEachIndexed { x, ch ->
                val color = G_COLORS[ch]
                if (color != null) {
                    drawRect(
                        color = color,
                        topLeft = Offset(x * stepX, y * stepY),
                        size = Size(stepX, stepY)
                    )
                }
            }
        }
    }
}

private data class PeekItem(
    val name: String,
    val bandIndex: Int,
    val leftPercent: Float,
    val topPercent: Float
)

private val PEEKS = listOf(
    PeekItem("Mira", 0, 0.30f, 0.16f),
    PeekItem("Jan", 0, 0.58f, 0.14f),
    PeekItem("Theo", 1, 0.20f, 0.44f),
    PeekItem("Sofia", 1, 0.68f, 0.42f),
    PeekItem("Yuki", 2, 0.12f, 0.74f),
    PeekItem("Leila", 2, 0.46f, 0.68f)
)

@Composable
fun SignInScreen(modifier: Modifier = Modifier) {
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current.density

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, top = 72.dp, bottom = 24.dp)
            ) {
                Text(
                    text = "SPHERES",
                    style = TextStyle(
                        fontFamily = PixelFontFamily(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 48.sp,
                        lineHeight = 52.sp,
                        letterSpacing = 3.84.sp,
                        color = Ink
                    )
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Stay close to the people who matter.",
                    style = TextStyle(
                        fontFamily = SansFontFamily(),
                        fontSize = 18.sp,
                        lineHeight = 28.sp,
                        color = Ink
                    ),
                    modifier = Modifier.width(280.dp)
                )
            }

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clipToBounds()
            ) {
                val wDp = maxWidth.value
                val hDp = maxHeight.value

                val firstThreeIntervals = remember { INTERVALS.take(3) }
                val bandPaths = remember(wDp, hDp, density) {
                    if (wDp > 0f && hDp > 0f) {
                        firstThreeIntervals.mapIndexed { i, interval ->
                            val arcTopDp = hDp * (0.06f + i * 0.27f)
                            val color = parseHexColor(interval.color)
                            val (strokePath, fillPath) = buildArcPaths(wDp, arcTopDp, hDp, density)
                            BandPathData(
                                strokeColor = color,
                                fillColor = tintColor(color, 20),
                                strokePath = strokePath,
                                fillPath = fillPath
                            )
                        }
                    } else emptyList()
                }

                Canvas(modifier = Modifier.fillMaxSize()) {
                    for (band in bandPaths) {
                        drawPath(path = band.fillPath, color = band.fillColor)
                        drawPath(path = band.strokePath, color = band.strokeColor, style = Stroke(1.dp.toPx()))
                    }
                }

                PEEKS.forEachIndexed { i, p ->
                    val colorHex = firstThreeIntervals[p.bandIndex].color
                    val animY = remember { Animatable(12f) }
                    val animAlpha = remember { Animatable(0f) }

                    LaunchedEffect(Unit) {
                        delay((150 + i * 70).toLong())
                        launch {
                            animAlpha.animateTo(1f, spring(stiffness = Spring.StiffnessLow))
                        }
                        launch {
                            animY.animateTo(0f, spring(stiffness = 300f, dampingRatio = 0.6f))
                        }
                    }

                    Box(
                        modifier = Modifier
                            .offset(
                                x = (wDp * p.leftPercent).dp,
                                y = (hDp * p.topPercent + animY.value).dp
                            )
                            .alpha(animAlpha.value)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) {
                            PixelAvatar(name = p.name, colorHex = colorHex, size = 44.dp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = p.name,
                                style = TextStyle(
                                    fontFamily = SansFontFamily(),
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 13.sp,
                                    color = Ink
                                )
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 24.dp, vertical = 20.dp)
                    .navigationBarsPadding()
            ) {
                PixelButton(
                    onClick = {
                        busy = true
                        error = null
                        scope.launch {
                            try {
                                SpheresStore.signIn()
                            } catch (e: Exception) {
                                val msg = e.message
                                if (msg != null && !msg.contains("cancel", ignoreCase = true)) {
                                    error = msg
                                }
                            } finally {
                                busy = false
                            }
                        }
                    },
                    variant = ButtonVariant.PRIMARY,
                    size = ButtonSize.LG,
                    fullWidth = true,
                    enabled = !busy
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(Color.White, shape = de.koenidv.spheres.ui.components.PxClipShape),
                            contentAlignment = Alignment.Center
                        ) {
                            PixelG()
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Sign in with Google",
                            style = TextStyle(
                                fontFamily = PixelFontFamily(),
                                fontWeight = FontWeight.Medium,
                                fontSize = 20.sp,
                                color = Color.White
                            )
                        )
                    }
                }

                if (error != null) {
                    Text(
                        text = error!!,
                        style = TextStyle(
                            fontFamily = SansFontFamily(),
                            fontSize = 14.sp,
                            color = DangerText
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    )
                }
            }
        }
    }
}
