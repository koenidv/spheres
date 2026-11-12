package de.koenidv.spheres

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.koenidv.spheres.data.SheetState
import de.koenidv.spheres.data.SpheresStore
import de.koenidv.spheres.domain.DueStatus
import de.koenidv.spheres.domain.dueInfo
import de.koenidv.spheres.domain.entriesByPerson
import de.koenidv.spheres.platform.PlatformBackHandler
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
import kotlinx.coroutines.launch

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
    val density = LocalDensity.current
    var currentPath by remember { mutableStateOf("/") }
    val backStack = remember { mutableStateListOf("/") }
    var lastNavDir by remember { mutableStateOf(NavDir(NavKind.TAB, 1)) }

    val pop: () -> Unit = {
        lastNavDir = NavDir(NavKind.POP, -1)
        if (backStack.size > 1) {
            backStack.removeAt(backStack.lastIndex)
            currentPath = backStack.last()
        } else {
            currentPath = "/"
        }
    }

    val navigate: (String) -> Unit = { target ->
        if (target == "..") {
            pop()
        } else if (target in TABS) {
            if (depth(currentPath) > 0) {
                lastNavDir = NavDir(NavKind.POP, -1)
            } else {
                lastNavDir = navDirection(currentPath, target)
            }
            backStack.clear()
            backStack.add(target)
            currentPath = target
        } else {
            lastNavDir = NavDir(NavKind.PUSH, 1)
            backStack.add(target)
            currentPath = target
        }
    }

    PlatformBackHandler(
        enabled = state.sheet == null && (backStack.size > 1 || currentPath != "/")
    ) {
        if (backStack.size > 1) pop() else navigate("/")
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
                        val dir = lastNavDir
                        val pushPopSpringInt = spring<IntOffset>(stiffness = 520f, dampingRatio = 0.96f)
                        val pushPopSpringFloat = spring<Float>(stiffness = 520f, dampingRatio = 0.96f)
                        when (dir.kind) {
                            NavKind.PUSH -> {
                                (
                                    slideInHorizontally(
                                        initialOffsetX = { fullWidth -> fullWidth },
                                        animationSpec = pushPopSpringInt
                                    ) togetherWith (
                                        slideOutHorizontally(
                                            targetOffsetX = { fullWidth -> -(fullWidth * 0.25f).roundToInt() },
                                            animationSpec = pushPopSpringInt
                                        ) + fadeOut(
                                            targetAlpha = 0.75f,
                                            animationSpec = pushPopSpringFloat
                                        )
                                    )
                                ).apply {
                                    targetContentZIndex = 1f
                                }
                            }
                            NavKind.POP -> {
                                (
                                    (
                                        slideInHorizontally(
                                            initialOffsetX = { fullWidth -> -(fullWidth * 0.25f).roundToInt() },
                                            animationSpec = pushPopSpringInt
                                        ) + fadeIn(
                                            initialAlpha = 0.75f,
                                            animationSpec = pushPopSpringFloat
                                        )
                                    ) togetherWith slideOutHorizontally(
                                        targetOffsetX = { fullWidth -> fullWidth },
                                        animationSpec = pushPopSpringInt
                                    )
                                ).apply {
                                    targetContentZIndex = -1f
                                }
                            }
                            NavKind.TAB -> {
                                val tabSpring = spring<IntOffset>(stiffness = 560f, dampingRatio = 0.95f)
                                val offsetPx = with(density) { (36.dp * dir.sign).roundToPx() }
                                (
                                    slideInHorizontally(
                                        initialOffsetX = { offsetPx },
                                        animationSpec = tabSpring
                                    ) + fadeIn(tween(160))
                                ) togetherWith (
                                    slideOutHorizontally(
                                        targetOffsetX = { -offsetPx },
                                        animationSpec = tabSpring
                                    ) + fadeOut(tween(120))
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                ) { loc ->
                    val bottomPadding = if (loc == "/") 0.dp else 96.dp
                    val isDetail = depth(loc) > 0

                    val swipeBackOffset = remember(loc) { Animatable(0f) }
                    var currentSwipeOffset by remember(loc) { mutableFloatStateOf(0f) }
                    var screenWidthPx by remember(loc) { mutableFloatStateOf(0f) }
                    val scope = rememberCoroutineScope()
                    val viewConfig = LocalViewConfiguration.current

                    val swipeModifier = if (isDetail) {
                        Modifier
                            .onSizeChanged { screenWidthPx = it.width.toFloat() }
                            .offset { IntOffset(swipeBackOffset.value.roundToInt(), 0) }
                            .pointerInput(loc) {
                                val edgeThresholdPx = 28.dp.toPx()
                                val flingThresholdPx = 600.dp.toPx()
                                val velocityTracker = VelocityTracker()
                                val touchSlop = viewConfig.touchSlop

                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    if (down.position.x <= edgeThresholdPx) {
                                        velocityTracker.resetTracking()
                                        velocityTracker.addPosition(down.uptimeMillis, down.position)
                                        var isHorizontalDrag: Boolean? = null
                                        var totalDx = 0f
                                        var totalDy = 0f

                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                            if (!change.pressed) {
                                                if (isHorizontalDrag == true) {
                                                    val velocityX = velocityTracker.calculateVelocity().x
                                                    val dismissThreshold = if (screenWidthPx > 0f) screenWidthPx * 0.30f else 300f
                                                    if (currentSwipeOffset > dismissThreshold || velocityX > flingThresholdPx) {
                                                        pop()
                                                    } else {
                                                        scope.launch {
                                                            swipeBackOffset.animateTo(
                                                                targetValue = 0f,
                                                                animationSpec = spring(stiffness = 550f, dampingRatio = 0.9f),
                                                                initialVelocity = velocityX
                                                            )
                                                            currentSwipeOffset = 0f
                                                        }
                                                    }
                                                }
                                                break
                                            }

                                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                                            val dx = change.position.x - change.previousPosition.x
                                            val dy = change.position.y - change.previousPosition.y

                                            if (isHorizontalDrag == null) {
                                                totalDx += dx
                                                totalDy += dy
                                                if (totalDx > touchSlop && kotlin.math.abs(totalDx) > kotlin.math.abs(totalDy)) {
                                                    isHorizontalDrag = true
                                                    change.consume()
                                                } else if (kotlin.math.abs(totalDy) > touchSlop || totalDx < -touchSlop) {
                                                    isHorizontalDrag = false
                                                    break
                                                }
                                            } else if (isHorizontalDrag == true) {
                                                change.consume()
                                                currentSwipeOffset = (currentSwipeOffset + dx).coerceAtLeast(0f)
                                                val target = currentSwipeOffset
                                                scope.launch {
                                                    swipeBackOffset.snapTo(target)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                    } else {
                        Modifier
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = bottomPadding)
                            .then(swipeModifier)
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
                        lastNavDir = if (depth(currentPath) > 0) {
                            NavDir(NavKind.POP, -1)
                        } else {
                            navDirection(currentPath, route)
                        }
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
