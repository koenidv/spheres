package de.koenidv.spheres

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.koenidv.spheres.data.SheetState
import de.koenidv.spheres.data.SpheresStore
import de.koenidv.spheres.domain.DueStatus
import de.koenidv.spheres.domain.dueInfo
import de.koenidv.spheres.domain.entriesByPerson
import de.koenidv.spheres.platform.currentTimeMillis
import de.koenidv.spheres.ui.components.BottomNav
import de.koenidv.spheres.ui.components.BottomNavTab
import de.koenidv.spheres.ui.components.RadarSkeleton
import de.koenidv.spheres.ui.components.SteppedPolygonShape
import de.koenidv.spheres.ui.components.ToastHost
import de.koenidv.spheres.ui.components.ToastMessage
import de.koenidv.spheres.ui.screens.DueScreen
import de.koenidv.spheres.ui.screens.EventScreen
import de.koenidv.spheres.ui.screens.PersonScreen
import de.koenidv.spheres.ui.screens.RadarScreen
import de.koenidv.spheres.ui.screens.SettingsScreen
import de.koenidv.spheres.ui.screens.SignInScreen
import de.koenidv.spheres.ui.screens.SphereScreen
import de.koenidv.spheres.ui.screens.SpheresScreen
import de.koenidv.spheres.ui.sheets.SheetHost
import de.koenidv.spheres.ui.theme.PixelFontFamily
import de.koenidv.spheres.ui.theme.SpheresTheme
import kotlin.math.roundToInt

private val TABS = listOf("/", "/due", "/spheres", "/settings")

private fun depth(path: String): Int =
    if (path in TABS) 0 else path.split("/").filter { it.isNotEmpty() }.size

private enum class NavKind { TAB, PUSH, POP }
private data class NavDir(val kind: NavKind, val sign: Int)

private fun navDirection(from: String, to: String): NavDir {
    val d = depth(to) - depth(from)
    if (d > 0) return NavDir(NavKind.PUSH, 1)
    if (d < 0) return NavDir(NavKind.POP, -1)
    val a = TABS.indexOf(from)
    val b = TABS.indexOf(to)
    return NavDir(NavKind.TAB, if (a >= 0 && b >= 0 && b < a) -1 else 1)
}

@Composable
fun App() {
    SpheresTheme {
        val state by SpheresStore.state.collectAsState()

        if (!state.authReady) {
            Splash()
        } else if (state.user == null) {
            SignInScreen()
        } else {
            Shell()
        }
    }
}

@Composable
private fun Splash(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        val colors = listOf(Color(0xFFEB78B0), Color(0xFFE36F73), Color(0xFF9D7CE7))
        val transition = rememberInfiniteTransition()

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            colors.forEachIndexed { i, color ->
                val yOffset by transition.animateFloat(
                    initialValue = 0f,
                    targetValue = 0f,
                    animationSpec = infiniteRepeatable(
                        animation = keyframes {
                            durationMillis = 900
                            delayMillis = i * 150
                            0f at 0 using FastOutSlowInEasing
                            -8f at 450 using FastOutSlowInEasing
                            0f at 900
                        },
                        repeatMode = RepeatMode.Restart
                    )
                )

                Box(
                    modifier = Modifier
                        .offset { IntOffset(0, (yOffset * density).roundToInt()) }
                        .size(12.dp)
                        .background(color)
                )
            }
        }
    }
}

@Composable
private fun HeaderSkeleton(barColor: Color) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 36.dp, start = 16.dp, end = 16.dp)
    ) {
        Box(
            modifier = Modifier
                .width(224.dp)
                .height(36.dp)
                .clip(SteppedPolygonShape(steps = 1))
                .background(barColor)
        )
        Box(
            modifier = Modifier
                .padding(top = 12.dp)
                .fillMaxWidth(0.8f)
                .height(16.dp)
                .background(barColor)
        )
        Box(
            modifier = Modifier
                .padding(top = 8.dp)
                .width(208.dp)
                .height(16.dp)
                .background(barColor)
        )
    }
}

@Composable
private fun Skeleton(location: String) {
    val transition = rememberInfiniteTransition()
    val alpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    val barColor = Color(0xFFECECEC).copy(alpha = alpha)

    if (location == "/") {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
        ) {
            HeaderSkeleton(barColor)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, start = 16.dp, end = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(44.dp, 64.dp, 56.dp, 72.dp).forEach { w ->
                    Box(
                        modifier = Modifier
                            .width(w)
                            .height(32.dp)
                            .clip(SteppedPolygonShape(steps = 1))
                            .background(barColor)
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 16.dp)
            ) {
                RadarSkeleton()
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
        ) {
            HeaderSkeleton(barColor)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp, start = 16.dp, end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                listOf(96.dp, 72.dp, 72.dp, 120.dp).forEach { h ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(h)
                            .clip(SteppedPolygonShape(steps = 3))
                            .background(barColor)
                    )
                }
            }
        }
    }
}

@Composable
private fun Shell() {
    val state by SpheresStore.state.collectAsState()
    var currentPath by remember { mutableStateOf("/") }
    val backStack = remember { mutableStateListOf("/") }

    val navigate: (String) -> Unit = { target ->
        if (target == "..") {
            if (backStack.size > 1) {
                backStack.removeAt(backStack.lastIndex)
                currentPath = backStack.last()
            } else {
                currentPath = "/"
            }
        } else if (target in TABS) {
            if (backStack.size > 1 && depth(currentPath) > 0) {
                backStack.removeAt(backStack.lastIndex)
                currentPath = backStack.last()
            } else {
                backStack.clear()
                backStack.add(target)
                currentPath = target
            }
        } else {
            backStack.add(target)
            currentPath = target
        }
    }

    val selectedTab = when {
        currentPath == "/" -> BottomNavTab.RADAR
        currentPath.startsWith("/due") -> BottomNavTab.DUE
        currentPath.startsWith("/spheres") -> BottomNavTab.SPHERES
        currentPath.startsWith("/settings") -> BottomNavTab.SETTINGS
        else -> null
    }

    val overdueCount = remember(state.people, state.entries) {
        val by = entriesByPerson(state.entries)
        val now = currentTimeMillis()
        state.people.count { p ->
            dueInfo(p, by[p.id], now)?.status == DueStatus.OVERDUE
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxSize()
        ) {
            if (state.dataReady) {
                AnimatedContent(
                    targetState = currentPath,
                    transitionSpec = {
                        val dir = navDirection(initialState, targetState)
                        val springSpec = spring<IntOffset>(
                            stiffness = Spring.StiffnessMediumLow,
                            dampingRatio = Spring.DampingRatioNoBouncy
                        )
                        when (dir.kind) {
                            NavKind.PUSH -> {
                                (slideInHorizontally(
                                    initialOffsetX = { fullWidth -> fullWidth },
                                    animationSpec = springSpec
                                ) + fadeIn(tween(180))).togetherWith(
                                    slideOutHorizontally(
                                        targetOffsetX = { fullWidth -> -(fullWidth * 0.22f).toInt() },
                                        animationSpec = springSpec
                                    ) + fadeOut(tween(140))
                                )
                            }
                            NavKind.POP -> {
                                (slideInHorizontally(
                                    initialOffsetX = { fullWidth -> -(fullWidth * 0.22f).toInt() },
                                    animationSpec = springSpec
                                ) + fadeIn(tween(180))).togetherWith(
                                    slideOutHorizontally(
                                        targetOffsetX = { fullWidth -> fullWidth },
                                        animationSpec = springSpec
                                    ) + fadeOut(tween(140))
                                )
                            }
                            NavKind.TAB -> {
                                (slideInHorizontally(
                                    initialOffsetX = { fullWidth -> (dir.sign * (fullWidth * 0.15f)).toInt() },
                                    animationSpec = springSpec
                                ) + fadeIn(tween(180))).togetherWith(
                                    slideOutHorizontally(
                                        targetOffsetX = { fullWidth -> (-dir.sign * (fullWidth * 0.15f)).toInt() },
                                        animationSpec = springSpec
                                    ) + fadeOut(tween(140))
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                ) { loc ->
                    val bottomPadding = if (loc == "/") 0.dp else 96.dp
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = bottomPadding)
                    ) {
                        when {
                            loc == "/" -> RadarScreen(onNavigate = navigate)
                            loc == "/due" -> DueScreen(onNavigate = navigate)
                            loc == "/spheres" -> SpheresScreen(onNavigate = navigate)
                            loc == "/settings" -> SettingsScreen(onNavigate = navigate)
                            loc.startsWith("/spheres/") -> SphereScreen(
                                id = loc.removePrefix("/spheres/"),
                                onNavigate = navigate
                            )
                            loc.startsWith("/events/") -> EventScreen(
                                id = loc.removePrefix("/events/"),
                                onNavigate = navigate
                            )
                            loc.startsWith("/people/") -> PersonScreen(
                                id = loc.removePrefix("/people/"),
                                onNavigate = navigate
                            )
                            else -> {
                                Box(
                                    modifier = Modifier
                                        .statusBarsPadding()
                                        .padding(top = 36.dp, start = 16.dp)
                                ) {
                                    Text(
                                        text = "Nothing here.",
                                        fontFamily = PixelFontFamily(),
                                        fontSize = 20.sp
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Skeleton(location = currentPath)
            }

            BottomNav(
                selectedTab = selectedTab,
                onTabSelect = { tab ->
                    val route = when (tab) {
                        BottomNavTab.RADAR -> "/"
                        BottomNavTab.DUE -> "/due"
                        BottomNavTab.SPHERES -> "/spheres"
                        BottomNavTab.SETTINGS -> "/settings"
                    }
                    if (currentPath != route) {
                        backStack.clear()
                        backStack.add(route)
                        currentPath = route
                    }
                },
                onAddClick = { SpheresStore.openSheet(SheetState.Action) },
                overdueCount = overdueCount,
                modifier = Modifier.align(Alignment.BottomCenter)
            )

            SheetHost(onNavigate = navigate)

            ToastHost(
                toast = state.toast?.let { ToastMessage(it.id.toString(), it.text, it.undo) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 80.dp)
            )
        }
    }
}
