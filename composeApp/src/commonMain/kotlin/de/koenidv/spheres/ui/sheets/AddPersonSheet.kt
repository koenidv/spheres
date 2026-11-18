package de.koenidv.spheres.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.koenidv.spheres.data.PaywallReason
import de.koenidv.spheres.data.SheetState
import de.koenidv.spheres.data.SpheresStore
import de.koenidv.spheres.domain.Birthday
import de.koenidv.spheres.domain.FREE_CONNECTIONS_LIMIT
import de.koenidv.spheres.domain.INTERVALS
import de.koenidv.spheres.domain.UNTRACKED_COLOR
import de.koenidv.spheres.domain.canAddPerson
import de.koenidv.spheres.domain.intervalInfo
import de.koenidv.spheres.platform.PickedContact
import de.koenidv.spheres.platform.currentTimeMillis
import de.koenidv.spheres.platform.rememberContactPickerLauncher
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
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

@Composable
fun AddPersonSheet(
    sheet: SheetState.AddPerson
) {
    val state by SpheresStore.state.collectAsState()
    val spheres = state.spheres
    val people = state.people

    var batch by remember { mutableStateOf<List<PickedContact>?>(null) }
    var skipped by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf(sheet.name ?: "") }
    var interval by remember { mutableStateOf<Int?>(sheet.intervalDays ?: 30) }
    var sphereIds by remember { mutableStateOf(sheet.sphereId?.let { listOf(it) } ?: emptyList()) }
    var bMonth by remember { mutableIntStateOf(0) }
    var bDay by remember { mutableIntStateOf(0) }
    var bYear by remember { mutableStateOf("") }
    var note by remember { mutableStateOf(sheet.note ?: "") }
    var city by remember { mutableStateOf("") }
    var address by remember { mutableStateOf<String?>(null) }
    var country by remember { mutableStateOf<String?>(null) }
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
                if (bMonth == 0 && bDay == 0) {
                    bMonth = b.month
                    bDay = b.day
                    if (b.year != null && bYear.isBlank()) {
                        bYear = b.year.toString()
                    }
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
                    val year = bYear.toIntOrNull()
                    val currentYear = Instant.fromEpochMilliseconds(currentTimeMillis())
                        .toLocalDateTime(TimeZone.currentSystemDefault()).year
                    val birthday = if (bMonth in 1..12 && bDay in 1..31) {
                        Birthday(
                            month = bMonth,
                            day = bDay,
                            year = if (year != null && year in 1901..currentYear) year else null
                        )
                    } else null

                    SpheresStore.addPerson(
                        name = n,
                        intervalDays = interval,
                        sphereIds = sphereIds,
                        birthday = birthday,
                        note = note.trim(),
                        city = city.trim().ifEmpty { null },
                        address = address,
                        country = country,
                        contactId = contactId,
                        contactName = contactName,
                        phoneNumbers = phoneNumbers,
                    )
                    if (city.isNotBlank()) {
                        SpheresStore.geocodeMissingCities()
                    }
                    SpheresStore.showToast("Added $n")
                    SpheresStore.closeSheet()
                }
            }
        }
    }

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
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(PxClipShape)
                        .background(FieldBg),
                    contentAlignment = Alignment.Center
                ) {
                    PixelAvatar(
                        name = name.trim().ifEmpty { "New" },
                        colorHex = intervalInfo(interval).color,
                        size = 52.dp
                    )
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

            Field(label = "City (optional)") {
                PixelTextInput(
                    value = city,
                    onValueChange = { city = it },
                    placeholder = "Where do they live?"
                )
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
            Field(label = "Birthday (optional)") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    var dayMenuOpen by remember { mutableStateOf(false) }
                    var monthMenuOpen by remember { mutableStateOf(false) }

                    Box(modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .clip(PxClipShape)
                                .background(FieldBg)
                                .clickable { dayMenuOpen = true }
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = if (bDay > 0) bDay.toString() else "Day",
                                style = TextStyle(
                                    fontFamily = SansFontFamily(),
                                    fontSize = 16.sp,
                                    color = if (bDay > 0) Ink else Ink.copy(alpha = 0.35f)
                                )
                            )
                        }
                        DropdownMenu(
                            expanded = dayMenuOpen,
                            onDismissRequest = { dayMenuOpen = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Day") },
                                onClick = {
                                    bDay = 0
                                    dayMenuOpen = false
                                }
                            )
                            for (d in 1..31) {
                                DropdownMenuItem(
                                    text = { Text(d.toString()) },
                                    onClick = {
                                        bDay = d
                                        dayMenuOpen = false
                                    }
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .clip(PxClipShape)
                                .background(FieldBg)
                                .clickable { monthMenuOpen = true }
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = if (bMonth in 1..12) MONTHS[bMonth - 1] else "Month",
                                style = TextStyle(
                                    fontFamily = SansFontFamily(),
                                    fontSize = 16.sp,
                                    color = if (bMonth in 1..12) Ink else Ink.copy(alpha = 0.35f)
                                )
                            )
                        }
                        DropdownMenu(
                            expanded = monthMenuOpen,
                            onDismissRequest = { monthMenuOpen = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Month") },
                                onClick = {
                                    bMonth = 0
                                    monthMenuOpen = false
                                }
                            )
                            MONTHS.forEachIndexed { idx, m ->
                                DropdownMenuItem(
                                    text = { Text(m) },
                                    onClick = {
                                        bMonth = idx + 1
                                        monthMenuOpen = false
                                    }
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        PixelTextInput(
                            value = bYear,
                            onValueChange = { bYear = it.filter { ch -> ch.isDigit() }.take(4) },
                            placeholder = "Year"
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
