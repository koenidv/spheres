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
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.koenidv.spheres.ui.components.EmptyState
import de.koenidv.spheres.ui.components.IconName
import de.koenidv.spheres.ui.components.LocalDetailPaneDepth
import de.koenidv.spheres.ui.components.LocalRadarTargetWidth
import de.koenidv.spheres.ui.components.LocalSelectedPersonId
import de.koenidv.spheres.ui.components.LocalSelectedRoute
import de.koenidv.spheres.ui.components.LocalOpenPersonMenu
import de.koenidv.spheres.ui.components.LocalPersonOrder
import de.koenidv.spheres.ui.components.PersonMenuHost
import de.koenidv.spheres.ui.components.PersonMenuTarget
import de.koenidv.spheres.ui.components.PersonOrder
import de.koenidv.spheres.ui.theme.Line
import de.koenidv.spheres.data.LogMode
import de.koenidv.spheres.data.SheetState
import de.koenidv.spheres.data.SpheresStore
import de.koenidv.spheres.domain.DueStatus
import de.koenidv.spheres.domain.dueInfo
import de.koenidv.spheres.domain.entriesByPerson
import de.koenidv.spheres.platform.PlatformAnalytics
import de.koenidv.spheres.platform.PlatformBackHandler
import de.koenidv.spheres.platform.StatusBarEffect
import de.koenidv.spheres.platform.isWebPlatform
import de.koenidv.spheres.ui.components.ShortcutsDialog
import de.koenidv.spheres.platform.currentTimeMillis
import de.koenidv.spheres.ui.components.BottomNav
import de.koenidv.spheres.ui.components.ContentMaxWidth
import de.koenidv.spheres.ui.components.LocalBottomChromeInset
import de.koenidv.spheres.ui.components.LocalWindowClass
import de.koenidv.spheres.ui.components.NavRail
import de.koenidv.spheres.ui.components.NavSidebar
import de.koenidv.spheres.ui.components.WindowClass
import de.koenidv.spheres.ui.components.windowClassFor
import de.koenidv.spheres.ui.components.DatePickerHost
import de.koenidv.spheres.ui.components.BottomNavTab
import de.koenidv.spheres.ui.components.RadarSkeleton
import de.koenidv.spheres.ui.components.SteppedPolygonShape
import de.koenidv.spheres.ui.components.ToastHost
import de.koenidv.spheres.ui.components.ToastMessage
import de.koenidv.spheres.ui.components.rememberLiveNow
import de.koenidv.spheres.ui.screens.DueScreen
import de.koenidv.spheres.ui.screens.EventScreen
import de.koenidv.spheres.ui.screens.MapScreen
import de.koenidv.spheres.ui.screens.PeopleScreen
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val TABS = listOf("/", "/due", "/spheres", "/map")

// Settings is a tab-level destination wherever the nav rail or sidebar offers it
private fun isTab(path: String, flat: Boolean): Boolean = path in TABS || (flat && path == "/settings")

private fun depth(path: String, flat: Boolean = false): Int =
    if (isTab(path, flat)) 0 else path.split("/").filter { it.isNotEmpty() }.size

private enum class NavKind { TAB, PUSH, POP }
private data class NavDir(val kind: NavKind, val sign: Int)

private fun navDirection(from: String, to: String, flat: Boolean = false): NavDir {
    val d = depth(to, flat) - depth(from, flat)
    if (d > 0) return NavDir(NavKind.PUSH, 1)
    if (d < 0) return NavDir(NavKind.POP, -1)
    return tabDirection(from, to)
}

private fun tabDirection(from: String, to: String): NavDir {
    val order = TABS + "/settings"
    fun indexOf(path: String) = order.indexOfFirst { it == path || (it != "/" && path.startsWith("$it/")) }
    val a = indexOf(from)
    val b = indexOf(to)
    return NavDir(NavKind.TAB, if (a >= 0 && b >= 0 && b < a) -1 else 1)
}

private fun isDetailRoute(path: String): Boolean =
    path.startsWith("/people/") || path.startsWith("/events/")

// The spheres list is only a pane layout on LARGE, where it is the left column of three
private fun hasPane(path: String, large: Boolean): Boolean = when {
    path == "/spheres" -> large
    else -> path in TABS || path == "/people" || path == "/people?search=1" ||
        path.startsWith("/spheres/") || path.startsWith("/events/")
}

// Events opened straight from the spheres list are screens of their own, not detail
private fun isDetailEntry(stack: List<String>, i: Int): Boolean =
    isDetailRoute(stack[i]) && !(stack[i].startsWith("/events/") && i > 0 && stack[i - 1] == "/spheres")

// Last entry that is not drilled into from a detail route; everything after it lives in the detail pane
private fun primaryIndex(stack: List<String>): Int {
    var idx = 0
    for (i in stack.indices) {
        if (!isDetailEntry(stack, i) && (i == 0 || !isDetailEntry(stack, i - 1))) idx = i
    }
    return idx
}

private fun isSpheresFlow(path: String): Boolean =
    path.startsWith("/spheres") || path.startsWith("/events/")

private val SpheresListWidth = 300.dp
private val SphereContentMaxWidth = 640.dp

private enum class AppStage { SPLASH, SIGN_IN, SHELL }

@Composable
fun App() {
    SpheresTheme {
        val state by SpheresStore.state.collectAsState()
        StatusBarEffect(colorHex = 0xFFFFFFFFL, dimmed = state.sheet != null)

        val stage = when {
            !state.authReady -> AppStage.SPLASH
            state.user == null -> AppStage.SIGN_IN
            else -> AppStage.SHELL
        }

        AnimatedContent(
            targetState = stage,
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White),
            transitionSpec = {
                if (initialState == AppStage.SIGN_IN && targetState == AppStage.SHELL) {
                    (fadeIn(tween(320, easing = FastOutSlowInEasing)) +
                        scaleIn(initialScale = 0.96f, animationSpec = tween(320, easing = FastOutSlowInEasing)))
                        .togetherWith(fadeOut(tween(220, easing = FastOutSlowInEasing)))
                } else {
                    fadeIn(tween(240, easing = FastOutSlowInEasing))
                        .togetherWith(fadeOut(tween(180, easing = FastOutSlowInEasing)))
                }
            },
            label = "AppStage"
        ) { currentStage ->
            when (currentStage) {
                AppStage.SPLASH -> Splash()
                AppStage.SIGN_IN -> SignInScreen()
                AppStage.SHELL -> Shell()
            }
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
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        ShellContent(windowClassFor(maxWidth))
    }
}

@Composable
private fun ShellContent(windowClass: WindowClass) {
    val state by SpheresStore.state.collectAsState()
    val density = LocalDensity.current
    var currentPath by remember { mutableStateOf("/") }
    val backStack = remember { mutableStateListOf("/") }
    var lastNavDir by remember { mutableStateOf(NavDir(NavKind.TAB, 1)) }
    val frozenDetail = remember { HashMap<String, List<String>>() }
    val frozenPrimary = remember { HashMap<String, String>() }

    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    // Screens swap out under a focused field on navigation, which leaves the IME open
    val rootFocus = remember { FocusRequester() }
    var rootFocused by remember { mutableStateOf(false) }
    LaunchedEffect(currentPath) {
        focusManager.clearFocus(force = true)
        keyboard?.hide()
        runCatching { rootFocus.requestFocus() }
    }
    // Sheets clear focus on their way out; take it back so shortcuts keep working
    LaunchedEffect(state.sheet == null) {
        if (state.sheet == null) {
            delay(300)
            runCatching { rootFocus.requestFocus() }
        }
    }

    LaunchedEffect(currentPath) {
        val screenName = when {
            currentPath == "/" -> "radar"
            currentPath.startsWith("/due") -> "due"
            currentPath.startsWith("/spheres") -> "spheres"
            currentPath == "/map" -> "map"
            currentPath == "/settings" -> "settings"
            currentPath.startsWith("/people") -> "people"
            currentPath.startsWith("/person/") -> "person"
            currentPath.startsWith("/sphere/") -> "sphere"
            currentPath.startsWith("/event/") -> "event"
            else -> currentPath.trimStart('/').substringBefore('/').ifBlank { "unknown" }
        }
        PlatformAnalytics.screen(screenName)
    }

    val paneMode = windowClass.hasSidebar
    val primaryIdx = primaryIndex(backStack)
    val primaryPath = backStack[primaryIdx]
    val large = windowClass == WindowClass.LARGE
    val detailStack = if (paneMode && hasPane(primaryPath, large)) backStack.drop(primaryIdx + 1) else emptyList()
    val navPath = if (paneMode) primaryPath else currentPath
    // On LARGE the spheres list, a sphere and an event share one layout, so picking one must not re-enter the screen
    val navKey = if (large && isSpheresFlow(navPath)) "/spheres" else navPath

    val pop: () -> Unit = {
        lastNavDir = NavDir(NavKind.POP, -1)
        if (backStack.size > 1) {
            backStack.removeAt(backStack.lastIndex)
            currentPath = backStack.last()
        } else {
            if (!windowClass.isCompact) {
                lastNavDir = NavDir(NavKind.TAB, -1)
                backStack.clear()
                backStack.add("/")
            }
            currentPath = "/"
        }
    }

    val trimDetail: () -> Unit = {
        val keep = primaryIndex(backStack) + 1
        while (backStack.size > keep) backStack.removeAt(backStack.lastIndex)
        currentPath = backStack.last()
    }

    val flat = !windowClass.isCompact
    val tabDir: (String) -> NavDir = { target ->
        when {
            paneMode -> tabDirection(primaryPath, target)
            depth(currentPath, flat) > 0 -> NavDir(NavKind.POP, -1)
            else -> navDirection(currentPath, target, flat)
        }
    }

    val go: (String, Boolean) -> Unit = { target, fromDetail ->
        if (target == "..") {
            if (!fromDetail && paneMode) trimDetail()
            pop()
        } else if (isTab(target, flat)) {
            lastNavDir = tabDir(target)
            backStack.clear()
            backStack.add(target)
            currentPath = target
        } else {
            if (paneMode && !fromDetail) trimDetail()
            lastNavDir = NavDir(NavKind.PUSH, 1)
            backStack.add(target)
            currentPath = target
        }
    }
    val navigate: (String) -> Unit = { go(it, false) }
    val detailNavigate: (String) -> Unit = { go(it, true) }
    val selectSphere: (String) -> Unit = { target ->
        when {
            primaryPath == "/spheres" -> go(target, false)
            navKey == "/spheres" -> {
                trimDetail()
                backStack[backStack.lastIndex] = target
                currentPath = target
            }
            else -> {
                lastNavDir = tabDir("/spheres")
                backStack.clear()
                backStack.add("/spheres")
                backStack.add(target)
                currentPath = target
            }
        }
    }

    PlatformBackHandler(
        enabled = state.sheet == null && (backStack.size > 1 || currentPath != "/")
    ) {
        if (backStack.size > 1) pop() else navigate("/")
    }

    val selectTab: (BottomNavTab) -> Unit = { tab ->
        val route = when (tab) {
            BottomNavTab.RADAR -> "/"
            BottomNavTab.DUE -> "/due"
            BottomNavTab.SPHERES -> "/spheres"
            BottomNavTab.MAP -> "/map"
        }
        if (currentPath != route) {
            lastNavDir = tabDir(route)
            backStack.clear()
            backStack.add(route)
            currentPath = route
        }
    }

    val selectedTab = when {
        navPath == "/" -> BottomNavTab.RADAR
        navPath.startsWith("/due") -> BottomNavTab.DUE
        navPath.startsWith("/spheres") || (paneMode && navPath.startsWith("/events/")) -> BottomNavTab.SPHERES
        navPath == "/map" -> BottomNavTab.MAP
        else -> null
    }

    val personOrder = remember { PersonOrder() }
    var personMenu by remember { mutableStateOf<PersonMenuTarget?>(null) }

    val openPersonId = currentPath.takeIf { it.startsWith("/people/") }?.removePrefix("/people/")

    val runShortcut: (AppShortcut) -> Boolean = run@{ shortcut ->
        if (state.sheet != null) return@run false
        when (shortcut) {
            AppShortcut.NEW_CONNECTION -> SpheresStore.openSheet(SheetState.AddPerson())
            AppShortcut.LOG_INTERACTION -> SpheresStore.openSheet(
                if (openPersonId != null) SheetState.Log(openPersonId, LogMode.INTERACTION)
                else SheetState.PickPerson(LogMode.INTERACTION)
            )
            AppShortcut.SEARCH -> if (currentPath != "/people?search=1") navigate("/people?search=1")
            AppShortcut.RADAR -> selectTab(BottomNavTab.RADAR)
            AppShortcut.DUE -> selectTab(BottomNavTab.DUE)
            AppShortcut.SPHERES -> selectTab(BottomNavTab.SPHERES)
            AppShortcut.MAP -> selectTab(BottomNavTab.MAP)
            AppShortcut.SETTINGS -> if (currentPath != "/settings") navigate("/settings")
            AppShortcut.HELP -> ShortcutHelp.open.value = true
        }
        true
    }
    val currentRunShortcut by rememberUpdatedState(runShortcut)
    LaunchedEffect(Unit) {
        AppShortcuts.events.collect { currentRunShortcut(it) }
    }

    val moveSelection: (Int) -> Boolean = move@{ delta ->
        val listed = navPath == "/due" || navPath == "/people" || navPath == "/people?search=1"
        val ids = personOrder.ids
        if (!paneMode || !listed || ids.isEmpty()) return@move false
        val selected = detailStack.lastOrNull()?.takeIf { it.startsWith("/people/") }?.removePrefix("/people/")
        val cur = ids.indexOf(selected)
        val next = if (cur < 0) {
            if (delta > 0) 0 else ids.lastIndex
        } else {
            (cur + delta).coerceIn(0, ids.lastIndex)
        }
        if (next != cur || detailStack.size > 1) navigate("/people/${ids[next]}")
        true
    }

    var goPrefixAt by remember { mutableStateOf(0L) }
    val onKey: (KeyEvent) -> Boolean = key@{ e ->
        if (e.type != KeyEventType.KeyDown) return@key false
        if (e.key == Key.Escape) {
            return@key when {
                personMenu != null -> { personMenu = null; true }
                state.sheet != null -> { SpheresStore.closeSheet(); true }
                !rootFocused -> {
                    focusManager.clearFocus(force = true)
                    keyboard?.hide()
                    runCatching { rootFocus.requestFocus() }
                    true
                }
                paneMode && detailStack.isNotEmpty() -> {
                    if (detailStack.size > 1) pop() else trimDetail()
                    true
                }
                backStack.size > 1 -> { pop(); true }
                else -> false
            }
        }
        if (state.sheet != null || personMenu != null) return@key false
        val cmd = e.isMetaPressed || e.isCtrlPressed
        if (cmd) {
            val shortcut = when (e.key) {
                Key.N -> AppShortcut.NEW_CONNECTION
                Key.L -> AppShortcut.LOG_INTERACTION
                Key.F -> AppShortcut.SEARCH
                Key.One -> AppShortcut.RADAR
                Key.Two -> AppShortcut.DUE
                Key.Three -> AppShortcut.SPHERES
                Key.Four -> AppShortcut.MAP
                Key.Comma -> AppShortcut.SETTINGS
                else -> null
            }
            return@key shortcut?.let(runShortcut) ?: false
        }
        // Plain keys would end up in the text field otherwise
        if (!rootFocused || e.isAltPressed) return@key false
        if (isWebPlatform && !e.isCtrlPressed && !e.isMetaPressed) {
            val goPending = currentTimeMillis() - goPrefixAt < 1500
            goPrefixAt = 0L
            val web = when {
                e.utf16CodePoint == '?'.code -> AppShortcut.HELP
                goPending && e.key == Key.R -> AppShortcut.RADAR
                goPending && e.key == Key.D -> AppShortcut.DUE
                goPending && e.key == Key.S -> AppShortcut.SPHERES
                goPending && e.key == Key.M -> AppShortcut.MAP
                e.isShiftPressed -> null
                e.key == Key.N -> AppShortcut.NEW_CONNECTION
                e.key == Key.L -> AppShortcut.LOG_INTERACTION
                e.key == Key.Comma -> AppShortcut.SETTINGS
                else -> null
            }
            if (web != null) return@key runShortcut(web)
            if (e.key == Key.G && !e.isShiftPressed) {
                goPrefixAt = currentTimeMillis()
                return@key true
            }
        }
        when {
            e.key == Key.Slash || e.utf16CodePoint == '/'.code -> runShortcut(AppShortcut.SEARCH)
            e.key == Key.DirectionDown -> moveSelection(1)
            e.key == Key.DirectionUp -> moveSelection(-1)
            else -> false
        }
    }

    val now = rememberLiveNow()
    val overdueCount = remember(state.people, state.entries, now) {
        val by = entriesByPerson(state.entries)
        state.people.count { p ->
            dueInfo(p, by[p.id], now)?.status == DueStatus.OVERDUE
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent(onKey)
            .focusRequester(rootFocus)
            .onFocusChanged { rootFocused = it.isFocused }
            .focusable()
    ) {
        val bottomInset = if (windowClass.isCompact) 96.dp else 16.dp

        CompositionLocalProvider(
            LocalWindowClass provides windowClass,
            LocalBottomChromeInset provides bottomInset,
            LocalPersonOrder provides personOrder,
            LocalOpenPersonMenu provides { personMenu = it }
        ) {
            val screens: @Composable () -> Unit = {
            if (state.dataReady) {
                AnimatedContent(
                    targetState = navKey,
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
                    val isDetail = depth(loc) > 0

                    val swipeBackOffset = remember(loc) { Animatable(0f) }
                    var currentSwipeOffset by remember(loc) { mutableFloatStateOf(0f) }
                    var screenWidthPx by remember(loc) { mutableFloatStateOf(0f) }
                    val scope = rememberCoroutineScope()
                    val viewConfig = LocalViewConfiguration.current

                    val swipeModifier = if (isDetail && windowClass.isCompact) {
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

                    val paned = paneMode && hasPane(loc, large)
                    // The outgoing primary keeps its detail pane while it animates out
                    val locDetail = if (loc == navKey) detailStack.also { frozenDetail[loc] = it } else frozenDetail[loc].orEmpty()
                    val locPrimary = if (loc == navKey) navPath.also { frozenPrimary[loc] = it } else frozenPrimary[loc] ?: loc
                    val constrain = !windowClass.isCompact && !paned && loc != "/" && loc != "/map" &&
                        !loc.startsWith("/people/")
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(if (windowClass.isCompact) Modifier else Modifier.background(Color.White))
                            .then(swipeModifier),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Box(
                            modifier = if (constrain) {
                                Modifier.widthIn(max = ContentMaxWidth).fillMaxSize()
                            } else {
                                Modifier.fillMaxSize()
                            }
                        ) {
                        if (paned) {
                            PaneLayout(
                                primary = locPrimary,
                                detail = locDetail,
                                windowClass = windowClass,
                                onNavigate = navigate,
                                onDetailNavigate = detailNavigate,
                                onSelectSphere = selectSphere
                            )
                        } else {
                            RouteContent(loc, navigate)
                        }
                        }
                    }
                }
            } else {
                Skeleton(location = currentPath)
            }

            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White),
                contentAlignment = Alignment.TopCenter
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (windowClass.isCompact) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            screens()
                            BottomNav(
                                selectedTab = selectedTab,
                                onTabSelect = selectTab,
                                onAddClick = { SpheresStore.openSheet(SheetState.Action) },
                                overdueCount = overdueCount,
                                modifier = Modifier.align(Alignment.BottomCenter)
                            )
                        }
                    } else {
                        Row(modifier = Modifier.fillMaxSize()) {
                            if (windowClass.hasFullSidebar) {
                                NavSidebar(
                                    selectedTab = selectedTab,
                                    selectedPath = navPath,
                                    searchSelected = navPath == "/people?search=1",
                                    settingsSelected = navPath == "/settings",
                                    spheres = state.spheres,
                                    onTabSelect = selectTab,
                                    onAddClick = { SpheresStore.openSheet(SheetState.Action) },
                                    onSearchClick = { if (currentPath != "/people?search=1") navigate("/people?search=1") },
                                    onSphereClick = selectSphere,
                                    onSettingsClick = { navigate("/settings") },
                                    overdueCount = overdueCount
                                )
                            } else {
                                NavRail(
                                    selectedTab = selectedTab,
                                    searchSelected = navPath == "/people?search=1",
                                    settingsSelected = navPath == "/settings",
                                    onTabSelect = selectTab,
                                    onAddClick = { SpheresStore.openSheet(SheetState.Action) },
                                    onSearchClick = { if (currentPath != "/people?search=1") navigate("/people?search=1") },
                                    onSettingsClick = { navigate("/settings") },
                                    overdueCount = overdueCount
                                )
                            }
                            Box(modifier = Modifier.weight(1f).fillMaxHeight().clipToBounds()) {
                                screens()
                            }
                        }
                    }

            SheetHost(onNavigate = navigate)
            val shortcutHelpOpen by ShortcutHelp.open.collectAsState()
            if (shortcutHelpOpen) ShortcutsDialog(onClose = { ShortcutHelp.open.value = false })
            PersonMenuHost(
                target = personMenu,
                onClose = { personMenu = null },
                onNavigate = navigate
            )
            DatePickerHost()

            ToastHost(
                toast = state.toast?.let { ToastMessage(it.id.toString(), it.text, it.undo) },
                onDismiss = { SpheresStore.dismissToast() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = bottomInset, start = 16.dp, end = 16.dp)
            )
                }
            }
        }
    }
}


@Composable
private fun RouteContent(loc: String, onNavigate: (String) -> Unit) {
    when {
        loc == "/" -> RadarScreen(onNavigate = onNavigate)
        loc == "/due" -> DueScreen(onNavigate = onNavigate)
        loc == "/spheres" -> SpheresScreen(onNavigate = onNavigate)
        loc == "/people" || loc == "/people?search=1" -> PeopleScreen(
            onNavigate = onNavigate,
            focusSearch = loc == "/people?search=1"
        )
        loc == "/map" -> MapScreen(onNavigate = onNavigate)
        loc == "/settings" -> SettingsScreen(onNavigate = onNavigate)
        loc.startsWith("/spheres/") -> SphereScreen(
            id = loc.removePrefix("/spheres/"),
            onNavigate = onNavigate
        )
        loc.startsWith("/events/") -> EventScreen(
            id = loc.removePrefix("/events/"),
            onNavigate = onNavigate
        )
        loc.startsWith("/people/") -> PersonScreen(
            id = loc.removePrefix("/people/"),
            onNavigate = onNavigate
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

@Composable
private fun PaneDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .fillMaxHeight()
            .background(Line)
    )
}

@Composable
private fun DetailPane(
    detail: List<String>,
    primary: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val depth = detail.size
    Box(modifier = modifier.fillMaxHeight().background(Color.White)) {
        AnimatedContent(
            targetState = detail.lastOrNull(),
            transitionSpec = {
                (fadeIn(tween(180)) + slideInHorizontally(tween(200)) { it / 24 })
                    .togetherWith(fadeOut(tween(100)))
            },
            modifier = Modifier.fillMaxSize(),
            label = "DetailPane"
        ) { path ->
            if (path == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (primary == "/spheres") {
                        EmptyState(
                            icon = IconName.PARTY,
                            title = "Nothing selected",
                            text = "Open an event to see it here."
                        )
                    } else {
                        EmptyState(
                            icon = IconName.USER,
                            title = "Pick someone",
                            text = "Open a connection to see their details here."
                        )
                    }
                }
            } else {
                CompositionLocalProvider(LocalDetailPaneDepth provides depth) {
                    RouteContent(path, onNavigate)
                }
            }
        }
    }
}

// Lays the pane out at its full width and only reveals it, so it slides in without its content reflowing
private fun Modifier.revealWidth(full: Dp, shown: Dp): Modifier = clipToBounds().layout { measurable, constraints ->
    val placeable = measurable.measure(Constraints.fixed(full.roundToPx(), constraints.maxHeight))
    layout(shown.coerceIn(0.dp, full).roundToPx(), placeable.height) { placeable.place(0, 0) }
}

@Composable
private fun SlidePaneLayout(
    primary: String,
    detail: List<String>,
    paneWidth: Dp,
    totalWidth: Dp,
    onDetailNavigate: (String) -> Unit,
    primaryContent: @Composable () -> Unit
) {
    val open = detail.isNotEmpty()
    val lastDetail = remember { arrayOf(detail) }
    if (open) lastDetail[0] = detail
    val fullPane = paneWidth + 1.dp
    val shown by animateDpAsState(
        targetValue = if (open) fullPane else 0.dp,
        animationSpec = spring(stiffness = 520f, dampingRatio = 0.96f),
        label = "DetailPaneWidth"
    )

    Row(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).fillMaxHeight().clipToBounds()) {
            CompositionLocalProvider(LocalRadarTargetWidth provides (totalWidth - if (open) fullPane else 0.dp)) {
                primaryContent()
            }
        }
        if (shown > 0.dp) {
            Row(modifier = Modifier.fillMaxHeight().revealWidth(fullPane, shown)) {
                PaneDivider()
                DetailPane(lastDetail[0], primary, onDetailNavigate, Modifier.width(paneWidth))
            }
        }
    }
}

// Middle column on LARGE: only its content swaps when a sphere or event is picked from the list beside it
@Composable
private fun SphereColumn(selected: String, onNavigate: (String) -> Unit) {
    AnimatedContent(
        targetState = selected,
        transitionSpec = {
            (fadeIn(tween(180)) + slideInHorizontally(tween(200)) { it / 24 })
                .togetherWith(fadeOut(tween(100)))
        },
        modifier = Modifier.fillMaxSize(),
        label = "SphereColumn"
    ) { path ->
        Box(modifier = Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.TopCenter) {
            if (path == "/spheres") {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        icon = IconName.USERS,
                        title = "Pick a sphere",
                        text = "Open a sphere or event to see its people here."
                    )
                }
            } else {
                CompositionLocalProvider(LocalDetailPaneDepth provides 1) {
                    Box(modifier = Modifier.widthIn(max = SphereContentMaxWidth).fillMaxHeight()) {
                        RouteContent(path, onNavigate)
                    }
                }
            }
        }
    }
}

@Composable
private fun PaneLayout(
    primary: String,
    detail: List<String>,
    windowClass: WindowClass,
    onNavigate: (String) -> Unit,
    onDetailNavigate: (String) -> Unit,
    onSelectSphere: (String) -> Unit
) {
    val selectedPerson = detail.lastOrNull()
        ?.takeIf { it.startsWith("/people/") }
        ?.removePrefix("/people/")
    val large = windowClass == WindowClass.LARGE

    CompositionLocalProvider(LocalSelectedPersonId provides selectedPerson) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val sidePane = if (large) (maxWidth * 0.4f).coerceIn(420.dp, 560.dp) else 400.dp
            val listWidth = minOf(480.dp, maxWidth * 0.45f)
            val open = detail.isNotEmpty()

            when {
                primary == "/" || primary == "/map" -> {
                    SlidePaneLayout(
                        primary = primary,
                        detail = detail,
                        paneWidth = sidePane,
                        totalWidth = maxWidth,
                        onDetailNavigate = onDetailNavigate
                    ) { RouteContent(primary, onNavigate) }
                }
                large && isSpheresFlow(primary) -> {
                    val listNavigate: (String) -> Unit = { target ->
                        if (target.startsWith("/spheres/") || target.startsWith("/events/")) onSelectSphere(target)
                        else onNavigate(target)
                    }
                    SlidePaneLayout(
                        primary = primary,
                        detail = detail,
                        paneWidth = 420.dp,
                        totalWidth = maxWidth,
                        onDetailNavigate = onDetailNavigate
                    ) {
                        Row(modifier = Modifier.fillMaxSize()) {
                            Box(modifier = Modifier.width(SpheresListWidth).fillMaxHeight()) {
                                CompositionLocalProvider(
                                    LocalSelectedRoute provides primary.takeIf { it != "/spheres" }
                                ) { RouteContent("/spheres", listNavigate) }
                            }
                            PaneDivider()
                            SphereColumn(primary, onNavigate)
                        }
                    }
                }
                isSpheresFlow(primary) -> {
                    SlidePaneLayout(
                        primary = primary,
                        detail = detail,
                        paneWidth = sidePane,
                        totalWidth = maxWidth,
                        onDetailNavigate = onDetailNavigate
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                            Box(modifier = Modifier.widthIn(max = SphereContentMaxWidth).fillMaxHeight()) {
                                RouteContent(primary, onNavigate)
                            }
                        }
                    }
                }
                else -> {
                    Row(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.width(listWidth).fillMaxHeight()) {
                            RouteContent(primary, onNavigate)
                        }
                        PaneDivider()
                        DetailPane(detail, primary, onDetailNavigate, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
