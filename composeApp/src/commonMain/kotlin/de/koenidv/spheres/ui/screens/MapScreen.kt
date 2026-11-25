package de.koenidv.spheres.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import de.koenidv.spheres.ui.components.LocalBottomChromeInset
import de.koenidv.spheres.ui.components.LocalSelectedPersonId
import de.koenidv.spheres.ui.components.LocalWindowClass
import androidx.compose.foundation.layout.widthIn
import de.koenidv.spheres.data.SheetState
import de.koenidv.spheres.data.SpheresStore
import de.koenidv.spheres.domain.MapGeo
import de.koenidv.spheres.domain.MapCluster
import de.koenidv.spheres.domain.MapPoint
import de.koenidv.spheres.domain.clusterPoints
import de.koenidv.spheres.domain.frameBounds
import de.koenidv.spheres.domain.hasCoordinates
import de.koenidv.spheres.domain.intervalInfo
import de.koenidv.spheres.domain.locationLabel
import de.koenidv.spheres.platform.PlatformLocation
import de.koenidv.spheres.ui.components.IconName
import de.koenidv.spheres.ui.components.MapCamera
import de.koenidv.spheres.ui.components.MapPin
import de.koenidv.spheres.ui.components.MapPinBoxHeight
import de.koenidv.spheres.ui.components.MapPinBoxWidth
import de.koenidv.spheres.ui.components.MapWater
import de.koenidv.spheres.ui.components.PersonAvatar
import de.koenidv.spheres.ui.components.PixelIcon
import de.koenidv.spheres.ui.components.PixelShell
import de.koenidv.spheres.ui.components.PixelTerrain
import de.koenidv.spheres.ui.components.PxClipShape
import de.koenidv.spheres.ui.components.ScreenHeader
import de.koenidv.spheres.ui.components.SteppedPolygonShape
import de.koenidv.spheres.ui.components.UserLocationBlue
import de.koenidv.spheres.ui.components.UserLocationBoxSize
import de.koenidv.spheres.ui.components.UserLocationMarker
import de.koenidv.spheres.ui.theme.CardBg
import de.koenidv.spheres.ui.theme.Ink
import de.koenidv.spheres.ui.theme.Muted
import de.koenidv.spheres.ui.theme.PixelFontFamily
import de.koenidv.spheres.ui.theme.SansFontFamily
import kotlinx.coroutines.launch
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val MAX_SCALE_DP_PER_CELL = 120f
private const val CLUSTER_RADIUS_DP = 38f
private const val SCALE_BUCKETS_PER_E = 5f

@Composable
fun MapScreen(
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by SpheresStore.state.collectAsState()
    val people = state.people
    val located = remember(people) { people.filter { it.hasCoordinates } }
    val missingCount = remember(people) { people.count { it.intervalDays != null && !it.hasCoordinates } }
    val byId = remember(located) { located.associateBy { it.id } }
    val density = LocalDensity.current.density
    val scope = rememberCoroutineScope()

    val camera = remember { MapCamera(MAX_SCALE_DP_PER_CELL * density) }
    var userMoved by remember { mutableStateOf(false) }
    var framed by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<MapCluster?>(null) }
    var selectedUserLocation by remember { mutableStateOf(false) }
    var centerOnNextLocation by remember { mutableStateOf(false) }
    val locationSupported = remember { PlatformLocation.isSupported() }
    val compact = LocalWindowClass.current.isCompact
    val paneMode = LocalWindowClass.current.hasSidebar
    val openId = if (paneMode) LocalSelectedPersonId.current else null

    // The overlay only lives while the pane shows someone from that location
    LaunchedEffect(openId) {
        val sel = selected
        if (paneMode && sel != null && (openId == null || openId !in sel.ids)) selected = null
    }

    // Location is only refreshed when the user opens the map tab (and has opted in).
    LaunchedEffect(Unit) {
        if (state.showUserLocation) {
            SpheresStore.refreshUserLocation()
        }
    }

    // People who only have a typed city get their coordinates resolved in the background.
    LaunchedEffect(people.map { Triple(it.id, it.city, it.lat) }) {
        SpheresStore.geocodeMissingCities()
    }

    val points = remember(located) {
        located.map { MapPoint(it.id, MapGeo.lngToU(it.lng!!), MapGeo.latToV(it.lat!!)) }
    }

    fun fitAll(animate: Boolean) {
        if (camera.viewW <= 0f) return
        if (points.isEmpty()) {
            val loc = if (state.showUserLocation) state.userLocation else null
            if (loc != null) {
                val u = MapGeo.lngToU(loc.lng)
                val v = MapGeo.latToV(loc.lat)
                val s = max(camera.minScale, 10f * density)
                if (animate) camera.animateTo(scope, u, v, s)
                else camera.jumpTo(u, v, s)
            } else {
                camera.jumpTo(MapGeo.WORLD_W / 2f, MapGeo.WORLD_H / 2f, camera.minScale)
            }
            return
        }
        val frame = frameBounds(
            minU = points.minOf { it.u }, maxU = points.maxOf { it.u },
            minV = points.minOf { it.v }, maxV = points.maxOf { it.v },
            viewW = camera.viewW, viewH = camera.viewH,
            paddingPx = 80f * density,
            minScale = camera.minScale,
            maxScale = min(camera.maxScale, 14f * density),
        )
        if (animate) camera.animateTo(scope, frame.u, frame.v, frame.scale)
        else camera.jumpTo(frame.u, frame.v, frame.scale)
    }

    val viewReady = camera.viewW > 0f
    LaunchedEffect(viewReady, points) {
        if (!viewReady || userMoved) return@LaunchedEffect
        fitAll(animate = framed)
        framed = true
    }

    LaunchedEffect(viewReady, state.userLocation, centerOnNextLocation) {
        val loc = state.userLocation
        if (viewReady && centerOnNextLocation && loc != null) {
            centerOnNextLocation = false
            camera.animateTo(
                scope,
                MapGeo.lngToU(loc.lng),
                MapGeo.latToV(loc.lat),
                max(camera.scale, 14f * density)
            )
        }
    }

    // Clusters are recomputed only when the zoom moves a notch, not on every pinch frame.
    val bucket by remember {
        derivedStateOf { (ln(camera.scale) * SCALE_BUCKETS_PER_E).roundToInt() }
    }
    val clusters = remember(points, bucket) {
        val scalePx = exp(bucket / SCALE_BUCKETS_PER_E)
        clusterPoints(points, CLUSTER_RADIUS_DP * density / scalePx)
    }

    fun onClusterTap(c: MapCluster) {
        userMoved = true
        val canSeparate = c.size > 1 &&
            c.spread * camera.scale > 6f * density &&
            camera.scale < camera.maxScale * 0.85f
        if (canSeparate) {
            val frame = frameBounds(
                c.minU, c.maxU, c.minV, c.maxV,
                camera.viewW, camera.viewH, 90f * density, camera.minScale, camera.maxScale
            )
            if (frame.scale > camera.scale * 1.25f) {
                selected = null
                camera.animateTo(scope, frame.u, frame.v, frame.scale)
                return
            }
        }
        selectedUserLocation = false
        if (paneMode) {
            // Opens in the detail pane; the pane shrinks the map, so centring on the pin now lands it in the final area
            val toggledOff = c.size > 1 && selected?.key == c.key
            selected = if (c.size > 1 && !toggledOff) c else null
            camera.animateTo(scope, c.u, c.v, camera.scale)
            if (!toggledOff) onNavigate("/people/${c.ids.first()}")
            return
        }
        selected = if (selected?.key == c.key) null else c
    }

    val subtitle = when {
        people.isEmpty() -> "Add people to see where they live."
        located.isEmpty() -> "Add a location to a profile to put someone on the map."
        else -> {
            val base = "${located.size} ${if (located.size == 1) "person" else "people"} on the map"
            if (missingCount > 0) "$base · $missingCount without a location" else base
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        val onSubtitleClick = if (missingCount > 0) {
            { SpheresStore.openSheet(SheetState.MissingLocations) }
        } else null
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (compact) Modifier.statusBarsPadding().navigationBarsPadding() else Modifier)
        ) {
            if (compact) {
                ScreenHeader(title = "Map", subtitle = subtitle, onSubtitleClick = onSubtitleClick)
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .then(
                        if (compact) {
                            Modifier
                                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = maxOf(LocalBottomChromeInset.current - 4.dp, 16.dp))
                                .clip(SteppedPolygonShape(steps = 3))
                        } else Modifier
                    )
                    .background(MapWater)
            ) {
                PixelTerrain(
                    camera = camera,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { centroid, pan, zoom, _ ->
                                userMoved = true
                                camera.cancelAnimation()
                                camera.zoomAt(centroid.x, centroid.y, zoom)
                                camera.panBy(pan.x, pan.y)
                            }
                        }
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = {
                                    selected = null
                                    selectedUserLocation = false
                                },
                                onDoubleTap = { at ->
                                    userMoved = true
                                    camera.animateZoomAt(scope, at.x, at.y, 2.2f)
                                }
                            )
                        }
                )

                val userLoc = if (state.showUserLocation) state.userLocation else null
                if (userLoc != null) {
                    val userU = MapGeo.lngToU(userLoc.lng)
                    val userV = MapGeo.latToV(userLoc.lat)
                    Box(
                        modifier = Modifier.offset {
                            IntOffset(
                                (camera.xOf(userU) - UserLocationBoxSize.toPx() / 2f).roundToInt(),
                                (camera.yOf(userV) - UserLocationBoxSize.toPx() / 2f).roundToInt()
                            )
                        }
                    ) {
                        UserLocationMarker(
                            selected = selectedUserLocation,
                            onClick = {
                                userMoved = true
                                selected = null
                                selectedUserLocation = !selectedUserLocation
                            }
                        )
                    }
                }

                for (c in clusters.sortedBy { it.v }) {
                    key(c.key) {
                        val members = remember(c, byId) { c.ids.mapNotNull { byId[it] } }
                        Box(
                            modifier = Modifier.offset {
                                IntOffset(
                                    (camera.xOf(c.u) - MapPinBoxWidth.toPx() / 2f).roundToInt(),
                                    (camera.yOf(c.v) - MapPinBoxHeight.toPx()).roundToInt()
                                )
                            }
                        ) {
                            MapPin(
                                people = members,
                                selected = selected?.key == c.key,
                                open = openId != null && openId in c.ids,
                                onClick = { onClusterTap(c) }
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .then(if (compact) Modifier else Modifier.statusBarsPadding())
                        .padding(if (compact) 12.dp else 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MapControl(IconName.PLUS) {
                        userMoved = true
                        camera.animateZoomAt(scope, camera.viewW / 2f, camera.viewH / 2f, 2f)
                    }
                    MapControl(IconName.MINUS) {
                        userMoved = true
                        camera.animateZoomAt(scope, camera.viewW / 2f, camera.viewH / 2f, 0.5f)
                    }
                    if (points.isNotEmpty()) {
                        MapControl(IconName.USERS) {
                            userMoved = true
                            selected = null
                            selectedUserLocation = false
                            fitAll(animate = true)
                        }
                    }
                    if (locationSupported) {
                        MapControl(
                            icon = IconName.LOCATE,
                            active = state.showUserLocation
                        ) {
                            if (!state.showUserLocation || !PlatformLocation.isGranted()) {
                                scope.launch {
                                    val granted = PlatformLocation.requestPermission()
                                    if (granted) {
                                        userMoved = true
                                        selected = null
                                        selectedUserLocation = false
                                        centerOnNextLocation = true
                                        SpheresStore.setShowUserLocation(true, refreshImmediately = true)
                                    } else {
                                        SpheresStore.showToast("Location permission was not allowed")
                                    }
                                }
                            } else {
                                userMoved = true
                                selected = null
                                selectedUserLocation = false
                                val loc = state.userLocation
                                if (loc != null) {
                                    camera.animateTo(
                                        scope,
                                        MapGeo.lngToU(loc.lng),
                                        MapGeo.latToV(loc.lat),
                                        max(camera.scale, 14f * density)
                                    )
                                    SpheresStore.refreshUserLocation()
                                } else {
                                    centerOnNextLocation = true
                                    SpheresStore.refreshUserLocation()
                                }
                            }
                        }
                    }
                }

                if (!compact) {
                    PixelShell(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .statusBarsPadding()
                            .padding(14.dp)
                            .widthIn(max = 440.dp),
                        fill = Color.White,
                        shadow = Color(0xFFB4BCE0),
                        lift = 3.dp
                    ) {
                        ScreenHeader(title = "Map", subtitle = subtitle, onSubtitleClick = onSubtitleClick)
                    }
                }

                SelectionOverlay(
                    selected = selected,
                    byId = byId,
                    openId = openId,
                    onOpen = { id -> if (id != openId) onNavigate("/people/$id") }
                )

                UserLocationOverlay(
                    visible = selectedUserLocation && state.showUserLocation && state.userLocation != null,
                    onHideLocation = {
                        selectedUserLocation = false
                        SpheresStore.setShowUserLocation(false)
                    }
                )

                if (located.isEmpty() && !selectedUserLocation) {
                    EmptyMapCard(
                        hasPeople = people.isNotEmpty(),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .mapOverlayFrame()
                    )
                }
            }
        }
    }
}

@Composable
private fun MapControl(
    icon: IconName,
    active: Boolean = false,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    PixelShell(
        modifier = Modifier
            .size(width = 40.dp, height = 43.dp)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        fill = if (active) Color(0xFFEEF3FF) else Color.White,
        shadow = if (active) Color(0xFFA6BEF8) else Color(0xFFB4BCE0),
        lift = 3.dp,
        isPressed = pressed
    ) {
        PixelIcon(name = icon, size = 20.dp, tint = if (active) UserLocationBlue else Ink)
    }
}

// Bottom panels float with even margins and a capped width on larger screens
@Composable
private fun Modifier.mapOverlayFrame(): Modifier =
    if (LocalWindowClass.current.isCompact) padding(10.dp)
    else navigationBarsPadding().padding(14.dp).widthIn(max = 480.dp)

@Composable
private fun SelectionCard(
    people: List<de.koenidv.spheres.domain.Person>,
    openId: String?,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(SteppedPolygonShape(steps = 2))
            .background(Color.White)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .padding(vertical = 6.dp)
            .heightIn(max = 232.dp)
            .verticalScroll(rememberScrollState())
    ) {
        for (p in people) {
            val isOpen = p.id == openId
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (isOpen) Modifier.background(Color(0xFFEEF3FF)) else Modifier)
                    .clickable { onOpen(p.id) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(Modifier.clip(PxClipShape)) {
                    PersonAvatar(person = p, colorHex = intervalInfo(p.intervalDays).color, size = 44.dp)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = p.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(fontFamily = PixelFontFamily(), fontSize = 18.sp, color = Ink)
                    )
                    p.locationLabel()?.let {
                        Text(
                            text = it,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(fontFamily = SansFontFamily(), fontSize = 14.sp, color = Muted)
                        )
                    }
                }
                PixelIcon(name = IconName.CHEVRON_RIGHT, size = 18.dp, tint = Ink.copy(alpha = if (isOpen) 1f else 0.4f))
            }
        }
    }
}

@Composable
private fun EmptyMapCard(hasPeople: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(SteppedPolygonShape(steps = 2))
            .background(Color.White)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PixelIcon(name = IconName.PIN, size = 18.dp, tint = Ink)
            Text(
                text = "Nobody on the map yet",
                style = TextStyle(fontFamily = PixelFontFamily(), fontSize = 18.sp, color = Ink)
            )
        }
        Text(
            text = if (hasPeople) {
                "Open a profile and tap “Add location” to pin someone here. A city is enough."
            } else {
                "Once you add people and tell Spheres where they live, they show up here."
            },
            style = TextStyle(fontFamily = SansFontFamily(), fontSize = 14.sp, lineHeight = 20.sp, color = Muted)
        )
    }
}

@Composable
private fun SelectionOverlay(
    selected: MapCluster?,
    byId: Map<String, de.koenidv.spheres.domain.Person>,
    openId: String?,
    onOpen: (String) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = selected != null,
            enter = slideInVertically(spring(stiffness = 500f, dampingRatio = 0.9f)) { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut()
        ) {
            var last by remember { mutableStateOf(selected) }
            if (selected != null) last = selected
            val sel = last
            if (sel != null) {
                SelectionCard(
                    people = sel.ids.mapNotNull { byId[it] },
                    openId = openId,
                    onOpen = onOpen,
                    modifier = Modifier.mapOverlayFrame()
                )
            }
        }
    }
}

@Composable
private fun UserLocationOverlay(
    visible: Boolean,
    onHideLocation: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(spring(stiffness = 500f, dampingRatio = 0.9f)) { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut()
        ) {
            Row(
                modifier = Modifier
                    .mapOverlayFrame()
                    .fillMaxWidth()
                    .clip(SteppedPolygonShape(steps = 2))
                    .background(Color.White)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(PxClipShape)
                        .background(UserLocationBlue.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    PixelIcon(name = IconName.LOCATE, size = 20.dp, tint = UserLocationBlue)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "You are here",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(fontFamily = PixelFontFamily(), fontSize = 18.sp, color = Ink)
                    )
                    Text(
                        text = "Updated when you open the map",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(fontFamily = SansFontFamily(), fontSize = 14.sp, color = Muted)
                    )
                }
                val hideInteraction = remember { MutableInteractionSource() }
                val hidePressed by hideInteraction.collectIsPressedAsState()
                PixelShell(
                    modifier = Modifier
                        .clickable(
                            interactionSource = hideInteraction,
                            indication = null,
                            onClick = onHideLocation
                        ),
                    fill = CardBg,
                    shadow = Color(0xFFD6D6D6),
                    lift = 2.dp,
                    isPressed = hidePressed
                ) {
                    Text(
                        text = "Hide",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = TextStyle(fontFamily = PixelFontFamily(), fontSize = 14.sp, color = Ink)
                    )
                }
            }
        }
    }
}
