package de.koenidv.spheres.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.koenidv.spheres.data.Geocoder
import de.koenidv.spheres.data.PlaceKind
import de.koenidv.spheres.data.PlaceResult
import de.koenidv.spheres.data.SheetState
import de.koenidv.spheres.data.SpheresStore
import de.koenidv.spheres.domain.MapGeo
import de.koenidv.spheres.domain.Person
import de.koenidv.spheres.domain.hasCoordinates
import de.koenidv.spheres.domain.intervalInfo
import de.koenidv.spheres.ui.components.ButtonSize
import de.koenidv.spheres.ui.components.ButtonVariant
import de.koenidv.spheres.ui.components.IconName
import de.koenidv.spheres.ui.components.MapCamera
import de.koenidv.spheres.ui.components.MapPin
import de.koenidv.spheres.ui.components.MapPinBoxHeight
import de.koenidv.spheres.ui.components.MapWater
import de.koenidv.spheres.ui.components.PersonAvatar
import de.koenidv.spheres.ui.components.PixelButton
import de.koenidv.spheres.ui.components.PixelIcon
import de.koenidv.spheres.ui.components.PixelTerrain
import de.koenidv.spheres.ui.components.PixelTextInput
import de.koenidv.spheres.ui.components.PxClipShape
import de.koenidv.spheres.ui.components.Segmented
import de.koenidv.spheres.ui.components.SegmentedOption
import de.koenidv.spheres.ui.components.SteppedPolygonShape
import de.koenidv.spheres.ui.components.autoFocus
import de.koenidv.spheres.ui.theme.FieldBg
import de.koenidv.spheres.ui.theme.Ink
import de.koenidv.spheres.ui.theme.Muted
import de.koenidv.spheres.ui.theme.PixelFontFamily
import de.koenidv.spheres.ui.theme.SansFontFamily
import kotlinx.coroutines.delay

private fun Person.currentPlace(): PlaceResult? {
    val la = lat ?: return null
    val ln = lng ?: return null
    val c = city?.takeIf { it.isNotBlank() } ?: address ?: return null
    return PlaceResult(
        title = address ?: c,
        subtitle = if (address != null) listOfNotNull(c, country).joinToString(", ") else country.orEmpty(),
        city = city,
        address = address,
        country = country,
        lat = la,
        lng = ln,
        kind = if (address != null) PlaceKind.ADDRESS else PlaceKind.CITY,
    )
}

@Composable
fun LocationSheet(sheet: SheetState.LocationEdit) {
    val state by SpheresStore.state.collectAsState()
    val person = state.people.firstOrNull { it.id == sheet.personId } ?: return
    val hasLocation = !person.city.isNullOrBlank() || !person.address.isNullOrBlank()
    val existingLabel = listOfNotNull(person.address, person.city).joinToString(", ").ifBlank { null }

    LocationEditor(
        stateKey = person.id,
        personPreview = person,
        initialPlace = remember(person.id) { person.currentPlace() },
        initialPrecision = if (person.address != null) PlaceKind.ADDRESS else PlaceKind.CITY,
        hasExistingLocation = hasLocation,
        existingLocationLabel = existingLabel,
        saveLabel = "Save location",
        onSavePlace = { city, address, country, lat, lng ->
            SpheresStore.updatePerson(person.id) {
                it.copy(
                    city = city,
                    address = address,
                    country = country,
                    lat = lat,
                    lng = lng
                )
            }
            SpheresStore.closeSheet()
        },
        onClear = {
            SpheresStore.updatePerson(person.id) {
                it.copy(city = null, address = null, country = null, lat = null, lng = null)
            }
            SpheresStore.closeSheet()
        }
    )
}

@Composable
fun LocationEditor(
    stateKey: Any,
    personPreview: Person,
    initialPlace: PlaceResult? = null,
    initialPrecision: PlaceKind = PlaceKind.CITY,
    hasExistingLocation: Boolean = false,
    existingLocationLabel: String? = null,
    saveLabel: String = "Save location",
    onSavePlace: (
        city: String?,
        address: String?,
        country: String?,
        lat: Double?,
        lng: Double?,
    ) -> Unit,
    onClear: () -> Unit,
) {
    val focus = LocalFocusManager.current

    var query by remember(stateKey) { mutableStateOf("") }
    var results by remember(stateKey) { mutableStateOf<List<PlaceResult>>(emptyList()) }
    var loading by remember(stateKey) { mutableStateOf(false) }
    var picked by remember(stateKey) { mutableStateOf(initialPlace) }
    var precision by remember(stateKey) { mutableStateOf(initialPrecision) }

    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 2) {
            results = emptyList()
            loading = false
            return@LaunchedEffect
        }
        loading = true
        delay(350)
        results = Geocoder.search(q)
        loading = false
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(Modifier.weight(1f)) {
                PixelTextInput(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "Search a city or address",
                    modifier = Modifier.autoFocus(enabled = picked == null, delayMs = 250L),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() })
                )
            }
        }

        val q = query.trim()
        if (q.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                for (r in results) {
                    ResultRow(
                        icon = if (r.kind == PlaceKind.ADDRESS) IconName.PIN else IconName.MAP,
                        title = r.title,
                        subtitle = r.subtitle,
                        onClick = {
                            picked = r
                            precision = r.kind
                            query = ""
                            focus.clearFocus()
                        }
                    )
                }
                if (loading && results.isEmpty()) {
                    Hint("Searching…")
                } else if (!loading && results.isEmpty() && q.length >= 2) {
                    Hint("No matches. You can still save it as typed.")
                }
                ResultRow(
                    icon = IconName.EDIT,
                    title = "Use “$q” as typed",
                    subtitle = "Saved as the city, shown on the map once it's found",
                    onClick = { onSavePlace(q, null, null, null, null) }
                )
            }
        } else if (picked != null) {
            val place = picked!!
            LocationPreview(person = personPreview, place = place)

            if (place.kind == PlaceKind.ADDRESS && place.address != null) {
                Segmented(
                    options = listOf(
                        SegmentedOption(PlaceKind.CITY, "City only"),
                        SegmentedOption(PlaceKind.ADDRESS, "Full address"),
                    ),
                    value = precision,
                    onChange = { precision = it }
                )
            }

            PixelButton(
                text = saveLabel,
                size = ButtonSize.LG,
                fullWidth = true,
                onClick = {
                    val withAddress = precision == PlaceKind.ADDRESS && place.kind == PlaceKind.ADDRESS
                    onSavePlace(
                        place.city,
                        if (withAddress) place.address else null,
                        place.country,
                        place.lat,
                        place.lng
                    )
                }
            )
            if (hasExistingLocation) {
                PixelButton(
                    text = "Remove location",
                    variant = ButtonVariant.GHOST,
                    fullWidth = true,
                    onClick = onClear
                )
            }
        } else {
            if (hasExistingLocation && !existingLocationLabel.isNullOrBlank()) {
                Hint("“$existingLocationLabel” isn't on the map yet. Search to pin it down.")
            } else {
                Hint("A city is enough. Add a street too if you want the pin to be exact.")
            }
            if (hasExistingLocation) {
                PixelButton(
                    text = "Remove location",
                    variant = ButtonVariant.GHOST,
                    fullWidth = true,
                    onClick = onClear
                )
            }
        }
        Spacer(Modifier.height(2.dp))
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        style = TextStyle(fontFamily = SansFontFamily(), fontSize = 14.sp, lineHeight = 20.sp, color = Muted),
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
    )
}

@Composable
private fun ResultRow(icon: IconName, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PxClipShape)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(PxClipShape)
                .background(FieldBg),
            contentAlignment = Alignment.Center
        ) {
            PixelIcon(name = icon, size = 18.dp, tint = Ink)
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontFamily = SansFontFamily(), fontSize = 16.sp, color = Ink)
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontFamily = SansFontFamily(), fontSize = 13.sp, color = Muted)
                )
            }
        }
    }
}

/** Small pixel map flying in on the chosen place, with the person's pin on it. */
@Composable
private fun LocationPreview(person: Person, place: PlaceResult) {
    val density = LocalDensity.current.density
    val scope = rememberCoroutineScope()
    val camera = remember { MapCamera(120f * density) }
    var placed by remember { mutableStateOf(false) }

    LaunchedEffect(camera.viewW, place.lat, place.lng) {
        if (camera.viewW <= 0f) return@LaunchedEffect
        val u = MapGeo.lngToU(place.lng)
        val v = MapGeo.latToV(place.lat)
        val target = camera.viewW / 60f
        if (!placed) {
            camera.jumpTo(u, v, camera.minScale)
            placed = true
        }
        camera.animateTo(scope, u, v, target, durationMs = 700)
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(168.dp)
                .clip(SteppedPolygonShape(steps = 3))
                .background(MapWater)
        ) {
            PixelTerrain(camera, Modifier.matchParentSize())
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = -(MapPinBoxHeight / 2))
            ) {
                MapPin(people = listOf(person))
            }
        }
        Column(Modifier.padding(horizontal = 4.dp)) {
            Text(
                text = place.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontFamily = PixelFontFamily(), fontSize = 22.sp, lineHeight = 28.sp, color = Ink)
            )
            val sub = listOfNotNull(
                place.city.takeIf { place.kind == PlaceKind.ADDRESS },
                place.country
            ).joinToString(", ").ifEmpty { place.subtitle }
            if (sub.isNotBlank()) {
                Text(
                    text = sub,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontFamily = SansFontFamily(), fontSize = 15.sp, color = Muted)
                )
            }
        }
    }
}

@Composable
fun MissingLocationsSheet() {
    val state by SpheresStore.state.collectAsState()
    val missing = remember(state.people) {
        state.people
            .filter { it.intervalDays != null && !it.hasCoordinates }
            .sortedBy { it.name.lowercase() }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
    ) {
        if (missing.isEmpty()) {
            Text(
                text = "Everyone has a location.",
                style = TextStyle(fontFamily = SansFontFamily(), fontSize = 15.sp, color = Muted),
                modifier = Modifier.padding(vertical = 24.dp)
            )
        } else {
            Text(
                text = "Tap a connection to set their location.",
                style = TextStyle(fontFamily = SansFontFamily(), fontSize = 13.sp, color = Muted),
                modifier = Modifier.padding(bottom = 10.dp)
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(missing, key = { it.id }) { person ->
                    val info = intervalInfo(person.intervalDays)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(PxClipShape)
                            .clickable {
                                SpheresStore.openSheet(SheetState.LocationEdit(person.id))
                            }
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        PersonAvatar(
                            person = person,
                            colorHex = info.color,
                            size = 40.dp
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = person.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = TextStyle(fontFamily = PixelFontFamily(), fontSize = 18.sp, color = Ink)
                            )
                            Text(
                                text = person.city?.takeIf { it.isNotBlank() } ?: info.label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = TextStyle(fontFamily = SansFontFamily(), fontSize = 13.sp, color = Muted)
                            )
                        }
                        PixelIcon(
                            name = IconName.PIN,
                            size = 18.dp,
                            tint = Muted
                        )
                    }
                }
            }
        }
    }
}
