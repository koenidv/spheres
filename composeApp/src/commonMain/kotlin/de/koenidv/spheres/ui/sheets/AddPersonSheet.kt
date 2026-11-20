package de.koenidv.spheres.ui.sheets

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.koenidv.spheres.data.PaywallReason
import de.koenidv.spheres.data.PlaceKind
import de.koenidv.spheres.data.PlaceResult
import de.koenidv.spheres.data.SheetState
import de.koenidv.spheres.data.SpheresStore
import de.koenidv.spheres.domain.AvatarConfig
import de.koenidv.spheres.domain.Birthday
import de.koenidv.spheres.domain.FREE_CONNECTIONS_LIMIT
import de.koenidv.spheres.domain.INTERVALS
import de.koenidv.spheres.domain.Person
import de.koenidv.spheres.domain.UNTRACKED_COLOR
import de.koenidv.spheres.domain.birthdayToEpochMillis
import de.koenidv.spheres.domain.canAddPerson
import de.koenidv.spheres.domain.epochMillisToBirthday
import de.koenidv.spheres.domain.formatBirthday
import de.koenidv.spheres.domain.intervalInfo
import de.koenidv.spheres.platform.PickedContact
import de.koenidv.spheres.platform.haptic
import de.koenidv.spheres.platform.rememberContactPickerLauncher
import de.koenidv.spheres.ui.components.AvatarRender
import de.koenidv.spheres.ui.components.rememberDatePicker
import de.koenidv.spheres.ui.components.ButtonSize
import de.koenidv.spheres.ui.components.Field
import de.koenidv.spheres.ui.components.IconName
import de.koenidv.spheres.ui.components.PixelAvatar
import de.koenidv.spheres.ui.components.PixelButton
import de.koenidv.spheres.ui.components.PixelIcon
import de.koenidv.spheres.ui.components.PixelIconButton
import de.koenidv.spheres.ui.components.PixelPanel
import de.koenidv.spheres.ui.components.PixelTextArea
import de.koenidv.spheres.ui.components.PixelTextInput
import de.koenidv.spheres.ui.components.autoFocus
import de.koenidv.spheres.ui.components.PxClipShape
import de.koenidv.spheres.ui.theme.Amber
import de.koenidv.spheres.ui.theme.FieldBg
import de.koenidv.spheres.ui.theme.Ink
import de.koenidv.spheres.ui.theme.Muted
import de.koenidv.spheres.ui.theme.PixelFontFamily
import de.koenidv.spheres.ui.theme.SansFontFamily
import de.koenidv.spheres.ui.theme.parseHexColor
import de.koenidv.spheres.ui.theme.tintColor

private enum class AddPersonSubView { FORM, AVATAR, LOCATION }

@Composable
fun AddPersonSheet(
    sheet: SheetState.AddPerson
) {
    val state by SpheresStore.state.collectAsState()
    val spheres = state.spheres
    val people = state.people

    var subView by remember { mutableStateOf(AddPersonSubView.FORM) }
    var batch by remember { mutableStateOf<List<PickedContact>?>(null) }
    var skipped by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf(sheet.name ?: "") }
    var interval by remember { mutableStateOf<Int?>(sheet.intervalDays ?: 30) }
    var sphereIds by remember { mutableStateOf(sheet.sphereId?.let { listOf(it) } ?: emptyList()) }
    var birthday by remember { mutableStateOf<Birthday?>(null) }
    var note by remember { mutableStateOf(sheet.note ?: "") }
    var city by remember { mutableStateOf("") }
    var address by remember { mutableStateOf<String?>(null) }
    var country by remember { mutableStateOf<String?>(null) }
    var lat by remember { mutableStateOf<Double?>(null) }
    var lng by remember { mutableStateOf<Double?>(null) }
    var avatar by remember { mutableStateOf<AvatarConfig?>(null) }
    var photoBytes by remember { mutableStateOf<ByteArray?>(null) }
    var photoBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var contactId by remember { mutableStateOf<String?>(null) }
    var contactName by remember { mutableStateOf<String?>(null) }
    var phoneNumbers by remember { mutableStateOf<List<String>>(emptyList()) }

    val pickContact = rememberContactPickerLauncher(allowMultiple = true) { picked ->
        val valid = picked.filter { it.name.isNotBlank() }
        if (valid.size == 1) {
            val c = valid[0]
            name = c.name.trim()
            contactId = c.contactId
            contactName = c.name.trim()
            phoneNumbers = c.phoneNumbers
            if (!c.city.isNullOrBlank() && city.isBlank()) {
                city = c.city.trim()
            }
            if (!c.address.isNullOrBlank() && address == null) {
                address = c.address.trim()
            }
            if (!c.country.isNullOrBlank() && country == null) {
                country = c.country.trim()
            }
            c.birthday?.let { b ->
                if (birthday == null) {
                    birthday = b
                }
            }
            return@rememberContactPickerLauncher
        }
        val knownNames = people.map { it.name.trim().lowercase() }.toMutableSet()
        val knownContactIds = SpheresStore.getLocalContactLinks().values.toMutableSet()
        val fresh = valid.filter { c ->
            val cid = c.contactId
            if (cid != null && cid in knownContactIds) return@filter false
            val key = c.name.trim().lowercase()
            if (knownNames.contains(key)) false else {
                knownNames.add(key)
                if (cid != null) knownContactIds.add(cid)
                true
            }
        }
        skipped = valid.size - fresh.size
        if (fresh.isNotEmpty()) {
            batch = fresh
        } else if (valid.isNotEmpty()) {
            SpheresStore.showToast(
                if (valid.size == 1) "Already a connection" else "All of them are already connections"
            )
        }
    }

    val toggleSphere = { id: String ->
        sphereIds = if (sphereIds.contains(id)) sphereIds - id else sphereIds + id
    }

    val submit = {
        val currentBatch = batch
        if (currentBatch != null) {
            if (!canAddPerson(people.size, state.isUnlimited, currentBatch.size)) {
                SpheresStore.openSheet(
                    SheetState.Paywall(reason = PaywallReason.ConnectionsLimit, returnTo = sheet)
                )
            } else {
                for (c in currentBatch) {
                    SpheresStore.addPerson(
                        name = c.name.trim(),
                        intervalDays = interval,
                        sphereIds = sphereIds,
                        birthday = c.birthday,
                        city = c.city?.trim()?.ifEmpty { null },
                        address = c.address?.trim()?.ifEmpty { null },
                        country = c.country?.trim()?.ifEmpty { null },
                        contactId = c.contactId,
                        contactName = c.name.trim(),
                        phoneNumbers = c.phoneNumbers,
                    )
                }
                SpheresStore.geocodeMissingCities()
                SpheresStore.showToast("Added ${currentBatch.size} ${if (currentBatch.size == 1) "connection" else "connections"}")
                SpheresStore.closeSheet()
            }
        } else {
            val n = name.trim()
            if (n.isNotEmpty()) {
                if (!canAddPerson(people.size, state.isUnlimited, 1)) {
                    SpheresStore.openSheet(
                        SheetState.Paywall(reason = PaywallReason.ConnectionsLimit, returnTo = sheet)
                    )
                } else {
                    SpheresStore.addPerson(
                        name = n,
                        intervalDays = interval,
                        sphereIds = sphereIds,
                        birthday = birthday,
                        note = note.trim(),
                        city = city.trim().ifEmpty { null },
                        address = address,
                        country = country,
                        lat = lat,
                        lng = lng,
                        avatar = avatar,
                        photoBytes = photoBytes,
                        contactId = contactId,
                        contactName = contactName,
                        phoneNumbers = phoneNumbers,
                    )
                    if (city.isNotBlank() && (lat == null || lng == null)) {
                        SpheresStore.geocodeMissingCities()
                    }
                    SpheresStore.showToast("Added $n")
                    SpheresStore.closeSheet()
                }
            }
        }
    }

    val accentHex = intervalInfo(interval).color
    val accentColor = parseHexColor(accentHex)

    AnimatedContent(
        targetState = subView,
        transitionSpec = {
            (fadeIn(tween(140)) togetherWith fadeOut(tween(110))).using(
                SizeTransform(clip = false)
            )
        },
        modifier = Modifier.fillMaxWidth()
    ) { currentView ->
        when (currentView) {
            AddPersonSubView.AVATAR -> {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SubViewHeader(
                        title = "Avatar",
                        onBack = { subView = AddPersonSubView.FORM }
                    )
                    AvatarEditor(
                        stateKey = "add_person_avatar",
                        name = name.trim().ifEmpty { "New" },
                        accentHex = accentHex,
                        initialAvatar = avatar,
                        initialPhotoBytes = photoBytes,
                        initialPhotoBitmap = photoBitmap,
                        saveLabel = "Done",
                        onSave = { normalizedAvatar, newBytes, newBitmap, willRemovePhoto ->
                            avatar = normalizedAvatar
                            if (willRemovePhoto) {
                                photoBytes = null
                                photoBitmap = null
                            } else if (newBytes != null) {
                                photoBytes = newBytes
                                photoBitmap = newBitmap
                            }
                            subView = AddPersonSubView.FORM
                        }
                    )
                }
            }

            AddPersonSubView.LOCATION -> {
                val cleanCity = city.trim().ifEmpty { null }
                val hasLoc = cleanCity != null || !address.isNullOrBlank()
                val existingLabel = listOfNotNull(address, cleanCity).joinToString(", ").ifBlank { null }
                val initialPlace = remember(lat, lng, cleanCity, address, country) {
                    val la = lat
                    val ln = lng
                    val title = address ?: cleanCity
                    if (la != null && ln != null && title != null) {
                        PlaceResult(
                            title = title,
                            subtitle = if (address != null) listOfNotNull(cleanCity, country).joinToString(", ") else country.orEmpty(),
                            city = cleanCity,
                            address = address,
                            country = country,
                            lat = la,
                            lng = ln,
                            kind = if (address != null) PlaceKind.ADDRESS else PlaceKind.CITY,
                        )
                    } else null
                }
                val personPreview = remember(name, interval, avatar, cleanCity, address, country, lat, lng) {
                    Person(
                        id = "__draft__",
                        name = name.trim().ifEmpty { "New" },
                        intervalDays = interval,
                        avatar = avatar,
                        city = cleanCity,
                        address = address,
                        country = country,
                        lat = lat,
                        lng = lng,
                        createdAt = 0L,
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SubViewHeader(
                        title = "Location",
                        onBack = { subView = AddPersonSubView.FORM }
                    )
                    LocationEditor(
                        stateKey = "add_person_location",
                        personPreview = personPreview,
                        initialPlace = initialPlace,
                        initialPrecision = if (address != null) PlaceKind.ADDRESS else PlaceKind.CITY,
                        hasExistingLocation = hasLoc,
                        existingLocationLabel = existingLabel,
                        saveLabel = "Done",
                        onSavePlace = { newCity, newAddress, newCountry, newLat, newLng ->
                            city = newCity.orEmpty()
                            address = newAddress
                            country = newCountry
                            lat = newLat
                            lng = newLng
                            subView = AddPersonSubView.FORM
                        },
                        onClear = {
                            city = ""
                            address = null
                            country = null
                            lat = null
                            lng = null
                            subView = AddPersonSubView.FORM
                        }
                    )
                }
            }

            AddPersonSubView.FORM -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
        val currentBatch = batch
        val batchExceedsLimit = currentBatch != null && !canAddPerson(people.size, state.isUnlimited, currentBatch.size)
        if (currentBatch != null) {
            Field(label = "${currentBatch.size} from contacts") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    currentBatch.forEach { c ->
                        Row(
                            modifier = Modifier
                                .height(40.dp)
                                .clip(PxClipShape)
                                .background(FieldBg)
                                .clickable {
                                    val rest = currentBatch.filter { it != c }
                                    batch = rest.ifEmpty { null }
                                }
                                .padding(start = 4.dp, end = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PixelAvatar(
                                name = c.name,
                                colorHex = intervalInfo(interval).color,
                                size = 32.dp
                            )
                            Text(
                                text = c.name,
                                style = TextStyle(
                                    fontFamily = PixelFontFamily(),
                                    fontSize = 15.sp,
                                    color = Ink
                                )
                            )
                            PixelIcon(
                                name = IconName.CLOSE,
                                size = 14.dp,
                                tint = Ink.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
                if (skipped > 0) {
                    Text(
                        text = "$skipped already ${if (skipped == 1) "is a connection" else "are connections"} and ${if (skipped == 1) "was" else "were"} skipped.",
                        style = TextStyle(
                            fontFamily = SansFontFamily(),
                            fontSize = 13.sp,
                            color = Muted
                        ),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                if (batchExceedsLimit) {
                    val over = (people.size + currentBatch.size) - FREE_CONNECTIONS_LIMIT
                    Spacer(modifier = Modifier.height(10.dp))
                    PixelPanel(color = Amber) {
                        Text(
                            text = "Adding ${currentBatch.size} puts you $over over the $FREE_CONNECTIONS_LIMIT free connections limit. Tap any chip above to trim the list, or unlock Unlimited.",
                            style = TextStyle(
                                fontFamily = SansFontFamily(),
                                fontSize = 13.sp,
                                lineHeight = 19.sp,
                                color = Ink
                            )
                        )
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box {
                    Box(
                        modifier = Modifier
                            .background(Color.White)
                            .border(2.dp, accentColor)
                            .clickable {
                                haptic()
                                subView = AddPersonSubView.AVATAR
                            }
                            .padding(4.dp)
                    ) {
                        AvatarRender(
                            name = name.trim().ifEmpty { "New" },
                            colorHex = accentHex,
                            config = avatar,
                            photoOverride = photoBitmap,
                            size = 56.dp
                        )
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = 5.dp, y = 5.dp)
                            .size(20.dp)
                            .background(Color.White)
                            .border(1.5.dp, accentColor)
                            .clickable {
                                haptic()
                                subView = AddPersonSubView.AVATAR
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        PixelIcon(
                            name = IconName.EDIT,
                            size = 11.dp,
                            tint = accentColor
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                ) {
                    PixelTextInput(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = "Name",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .autoFocus(enabled = sheet.name.isNullOrBlank())
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 6.dp)
                    ) {
                        PixelIconButton(
                            icon = IconName.USERS,
                            onClick = pickContact,
                            size = 40.dp,
                            tint = Ink.copy(alpha = 0.35f)
                        )
                    }
                }
            }

            val cleanCity = city.trim()
            val hasLocation = cleanCity.isNotEmpty() || !address.isNullOrBlank()
            val primaryLocation = address ?: cleanCity
            val secondaryLocation = when {
                address != null -> listOfNotNull(cleanCity.ifEmpty { null }, country).joinToString(", ")
                cleanCity.isNotEmpty() -> country.orEmpty()
                else -> ""
            }
            Field(label = "Location (optional)") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(PxClipShape)
                        .background(FieldBg)
                        .clickable {
                            haptic()
                            subView = AddPersonSubView.LOCATION
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PixelIcon(
                        name = if (address != null) IconName.PIN else IconName.MAP,
                        size = 18.dp,
                        tint = if (hasLocation) accentColor else Ink.copy(alpha = 0.45f)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (hasLocation) primaryLocation else "Add city or address",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(
                                fontFamily = SansFontFamily(),
                                fontSize = 16.sp,
                                color = if (hasLocation) Ink else Ink.copy(alpha = 0.35f)
                            )
                        )
                        if (hasLocation && secondaryLocation.isNotBlank()) {
                            Text(
                                text = secondaryLocation,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = TextStyle(
                                    fontFamily = SansFontFamily(),
                                    fontSize = 13.sp,
                                    color = Muted
                                )
                            )
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (hasLocation) {
                            Box(
                                modifier = Modifier.clickable {
                                    city = ""
                                    address = null
                                    country = null
                                    lat = null
                                    lng = null
                                }
                            ) {
                                PixelIcon(
                                    name = IconName.CLOSE,
                                    size = 16.dp,
                                    tint = Ink.copy(alpha = 0.45f)
                                )
                            }
                        }
                        PixelIcon(
                            name = IconName.CHEVRON_RIGHT,
                            size = 16.dp,
                            tint = Ink.copy(alpha = 0.4f)
                        )
                    }
                }
            }
        }

        Field(label = "Keep in touch") {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                INTERVALS.forEach { i ->
                    PixelChip(
                        label = i.label,
                        color = parseHexColor(i.color),
                        selected = interval == i.days,
                        onClick = { interval = i.days }
                    )
                }
                PixelChip(
                    label = "Not now",
                    color = parseHexColor(UNTRACKED_COLOR),
                    selected = interval == null,
                    onClick = { interval = null }
                )
            }
        }

        if (spheres.isNotEmpty()) {
            Field(label = "Spheres") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    spheres.forEach { s ->
                        PixelChip(
                            label = s.name,
                            color = parseHexColor(s.color),
                            selected = sphereIds.contains(s.id),
                            onClick = { toggleSphere(s.id) }
                        )
                    }
                }
            }
        }

        if (currentBatch == null) {
            val openBirthdayPicker = rememberDatePicker(
                initialDateMillis = birthdayToEpochMillis(birthday),
                onDateSelected = { pickedMillis ->
                    birthday = epochMillisToBirthday(pickedMillis)
                }
            )
            Field(label = "Birthday (optional)") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(PxClipShape)
                        .background(FieldBg)
                        .clickable { openBirthdayPicker() }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = birthday?.let { formatBirthday(it) } ?: "Add birthday",
                        style = TextStyle(
                            fontFamily = SansFontFamily(),
                            fontSize = 16.sp,
                            color = if (birthday != null) Ink else Ink.copy(alpha = 0.35f)
                        )
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (birthday != null) {
                            Box(
                                modifier = Modifier.clickable { birthday = null }
                            ) {
                                PixelIcon(
                                    name = IconName.CLOSE,
                                    size = 16.dp,
                                    tint = Ink.copy(alpha = 0.45f)
                                )
                            }
                        }
                        PixelIcon(
                            name = IconName.CALENDAR,
                            size = 18.dp,
                            tint = Ink.copy(alpha = 0.55f)
                        )
                    }
                }
            }

            Field(label = "Note (optional)") {
                PixelTextArea(
                    value = note,
                    onValueChange = { note = it },
                    placeholder = "How you met, what they like…",
                    minLines = 3
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        PixelButton(
            text = when {
                batchExceedsLimit -> "Unlock Unlimited · ${state.unlimitedPrice}"
                currentBatch != null -> "Add ${currentBatch.size} ${if (currentBatch.size == 1) "connection" else "connections"}"
                else -> "Add connection"
            },
            icon = if (batchExceedsLimit) IconName.SPARKLE else null,
            size = ButtonSize.LG,
            fullWidth = true,
            enabled = currentBatch != null || name.isNotBlank(),
            onClick = submit
        )
                }
            }
        }
    }
}

@Composable
private fun SubViewHeader(
    title: String,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PixelIconButton(
            icon = IconName.BACK,
            onClick = onBack,
            size = 36.dp
        )
        Text(
            text = title,
            style = TextStyle(
                fontFamily = PixelFontFamily(),
                fontSize = 20.sp,
                color = Ink
            )
        )
    }
}

@Composable
private fun PixelChip(
    label: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (selected) color else tintColor(color, 22)
    Box(
        modifier = Modifier
            .height(36.dp)
            .clip(PxClipShape)
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontFamily = PixelFontFamily(),
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                color = Ink
            )
        )
    }
}
