package de.koenidv.spheres.ui.screens

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import de.koenidv.spheres.ui.components.SteppedPolygonShape
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.koenidv.spheres.data.PaywallReason
import de.koenidv.spheres.data.SheetState
import de.koenidv.spheres.data.SpheresStore
import de.koenidv.spheres.domain.FREE_CONNECTIONS_LIMIT
import de.koenidv.spheres.domain.FREE_SPHERES_LIMIT
import de.koenidv.spheres.domain.INTERVALS
import de.koenidv.spheres.domain.UNTRACKED_COLOR
import de.koenidv.spheres.platform.currentTimeMillis
import de.koenidv.spheres.platform.PlatformNotifications
import de.koenidv.spheres.platform.PlatformPhoneIntegration
import de.koenidv.spheres.platform.shareJsonFile
import de.koenidv.spheres.ui.components.ButtonSize
import de.koenidv.spheres.ui.components.ButtonVariant
import de.koenidv.spheres.ui.components.Card
import de.koenidv.spheres.ui.components.CardLabel
import de.koenidv.spheres.ui.components.IconName
import de.koenidv.spheres.ui.components.PixelIconButton
import de.koenidv.spheres.ui.components.PixelAvatar
import de.koenidv.spheres.ui.components.PixelButton
import de.koenidv.spheres.ui.components.PixelIcon
import de.koenidv.spheres.ui.components.PixelPanel
import de.koenidv.spheres.ui.components.PxClipShape
import de.koenidv.spheres.ui.components.ScreenHeader
import de.koenidv.spheres.ui.components.SpherePill
import de.koenidv.spheres.ui.theme.DangerText
import de.koenidv.spheres.ui.theme.Ink
import de.koenidv.spheres.ui.theme.MonthlyPurple
import de.koenidv.spheres.ui.theme.Muted
import de.koenidv.spheres.ui.theme.PixelFontFamily
import de.koenidv.spheres.ui.theme.SansFontFamily
import de.koenidv.spheres.ui.theme.WeeklyPink
import de.koenidv.spheres.ui.theme.parseHexColor
import de.koenidv.spheres.ui.theme.tintColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
private fun UsageBarRow(
    label: String,
    count: Int,
    limit: Int,
    fillColor: Color,
) {
    val fraction = (count.toFloat() / limit.toFloat()).coerceIn(0f, 1f)
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = TextStyle(
                    fontFamily = SansFontFamily(),
                    fontSize = 13.sp,
                    color = Ink.copy(alpha = 0.78f)
                )
            )
            Text(
                text = "$count / $limit",
                style = TextStyle(
                    fontFamily = PixelFontFamily(),
                    fontSize = 13.sp,
                    color = if (count >= limit) WeeklyPink else Ink
                )
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(PxClipShape)
                .background(Color.White)
        ) {
            if (fraction > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction = fraction)
                        .background(fillColor)
                )
            }
        }
    }
}

@Composable
fun SettingsScreen(
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by SpheresStore.state.collectAsState()
    val user = state.user
    val scope = rememberCoroutineScope()
    var deleteConfirm by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }

    var notificationsGranted by remember {
        mutableStateOf(PlatformNotifications.isGranted())
    }
    val notificationsSupported = remember {
        PlatformNotifications.isSupported()
    }
    val phoneSupported = remember {
        PlatformPhoneIntegration.isSupported()
    }

    if (deleteConfirm) {
        DeleteAccountDialog(
            onCancel = { deleteConfirm = false },
            onConfirm = {
                deleteConfirm = false
                deleting = true
                scope.launch {
                    try {
                        SpheresStore.deleteAccount()
                    } catch (e: Exception) {
                        val msg = e.message
                        if (msg == null || !msg.contains("cancel", ignoreCase = true)) {
                            deleteError = msg ?: "Could not delete account"
                        }
                    } finally {
                        deleting = false
                    }
                }
            }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(bottom = 96.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PixelIconButton(icon = IconName.BACK, onClick = { onNavigate("..") })
            }
            ScreenHeader(title = "Settings")

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (state.paywallEnabled) {
                    if (state.isUnlimited) {
                        PixelPanel(
                            color = parseHexColor("#3fbf9f"),
                            raised = true
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(PxClipShape)
                                        .background(Color.White),
                                    contentAlignment = Alignment.Center
                                ) {
                                    PixelIcon(
                                        name = IconName.SPARKLE,
                                        size = 22.dp,
                                        tint = parseHexColor("#3fbf9f")
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Spheres Unlimited · Active",
                                        style = TextStyle(
                                            fontFamily = PixelFontFamily(),
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 18.sp,
                                            lineHeight = 22.sp,
                                            color = Ink
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Thank you for supporting Spheres! Unlimited connections & spheres are unlocked forever.",
                                        style = TextStyle(
                                            fontFamily = SansFontFamily(),
                                            fontSize = 13.sp,
                                            lineHeight = 18.sp,
                                            color = Ink.copy(alpha = 0.72f)
                                        )
                                    )
                                }
                            }
                        }
                    } else {
                        PixelPanel(
                            color = MonthlyPurple,
                            raised = true
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    PixelIcon(
                                        name = IconName.SPARKLE,
                                        size = 20.dp,
                                        tint = MonthlyPurple
                                    )
                                    Text(
                                        text = "Spheres Unlimited",
                                        style = TextStyle(
                                            fontFamily = PixelFontFamily(),
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 19.sp,
                                            lineHeight = 24.sp,
                                            color = Ink
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                    SpherePill(
                                        label = "${state.unlimitedPrice} · ONCE",
                                        color = tintColor(WeeklyPink, 35)
                                    )
                                }

                                Text(
                                    text = "Keep everyone who matters close. Pay once for unlimited connections & spheres forever — no subscription.",
                                    style = TextStyle(
                                        fontFamily = SansFontFamily(),
                                        fontSize = 14.sp,
                                        lineHeight = 20.sp,
                                        color = Ink.copy(alpha = 0.72f)
                                    )
                                )

                                UsageBarRow(
                                    label = "Connections",
                                    count = state.people.size,
                                    limit = FREE_CONNECTIONS_LIMIT,
                                    fillColor = WeeklyPink
                                )

                                UsageBarRow(
                                    label = "Spheres",
                                    count = state.spheres.size,
                                    limit = FREE_SPHERES_LIMIT,
                                    fillColor = MonthlyPurple
                                )

                                PixelButton(
                                    text = if (state.isPurchasing) "Processing…" else "Unlock forever · ${state.unlimitedPrice}",
                                    icon = IconName.SPARKLE,
                                    variant = ButtonVariant.PRIMARY,
                                    size = ButtonSize.MD,
                                    fullWidth = true,
                                    enabled = !state.isPurchasing,
                                    onClick = {
                                        SpheresStore.openSheet(SheetState.Paywall(reason = PaywallReason.Settings))
                                    }
                                )

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(34.dp)
                                        .clip(PxClipShape)
                                        .clickable(enabled = !state.isPurchasing) { SpheresStore.restorePurchases() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Restore purchase",
                                        style = TextStyle(
                                            fontFamily = PixelFontFamily(),
                                            fontSize = 14.sp,
                                            color = if (state.isPurchasing) Muted.copy(alpha = 0.4f) else Ink.copy(alpha = 0.78f)
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                Card {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        CardLabel(title = "Account", icon = IconName.USER)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            PixelAvatar(
                                name = user?.name ?: "You",
                                colorHex = INTERVALS.getOrNull(2)?.color ?: "#9d7ce7",
                                size = 48.dp
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = user?.name ?: "You",
                                    style = TextStyle(
                                        fontFamily = PixelFontFamily(),
                                        fontSize = 20.sp,
                                        lineHeight = 26.sp,
                                        color = Ink
                                    )
                                )
                                if (!user?.email.isNullOrBlank()) {
                                    Text(
                                        text = user!!.email!!,
                                        style = TextStyle(
                                            fontFamily = SansFontFamily(),
                                            fontSize = 14.sp,
                                            color = Muted
                                        )
                                    )
                                }
                            }
                            var accountMenuOpen by remember { mutableStateOf(false) }
                            Box {
                                PixelIconButton(
                                    icon = IconName.MORE,
                                    tint = Muted,
                                    size = 36.dp,
                                    onClick = { accountMenuOpen = true }
                                )
                                DropdownMenu(
                                    expanded = accountMenuOpen,
                                    onDismissRequest = { accountMenuOpen = false }
                                ) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = "Export JSON",
                                                style = TextStyle(
                                                    fontFamily = SansFontFamily(),
                                                    fontSize = 14.sp,
                                                    color = Ink
                                                )
                                            )
                                        },
                                        onClick = {
                                            accountMenuOpen = false
                                            val now = currentTimeMillis()
                                            val dateStr = Instant.fromEpochMilliseconds(now)
                                                .toLocalDateTime(TimeZone.currentSystemDefault())
                                                .date.toString()
                                            val jsonString = SpheresStore.exportData()
                                            shareJsonFile("spheres-$dateStr.json", jsonString)
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        PixelButton(
                            text = "Sign out",
                            icon = IconName.LOGOUT,
                            variant = ButtonVariant.SECONDARY,
                            size = ButtonSize.MD,
                            fullWidth = true,
                            onClick = { SpheresStore.signOut() }
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (deleting) "Deleting account…" else "Delete account",
                                style = TextStyle(
                                    fontFamily = PixelFontFamily(),
                                    fontSize = 15.sp,
                                    color = DangerText.copy(alpha = if (deleting) 0.5f else 0.85f)
                                ),
                                modifier = Modifier
                                    .clip(PxClipShape)
                                    .clickable(enabled = !deleting) {
                                        deleteError = null
                                        deleteConfirm = true
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }

                        if (deleteError != null) {
                            Text(
                                text = deleteError!!,
                                style = TextStyle(
                                    fontFamily = SansFontFamily(),
                                    fontSize = 13.sp,
                                    color = DangerText
                                ),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }

                Card {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        CardLabel(title = "Reminders", icon = IconName.BELL)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = if (notificationsSupported) {
                                    "Get a nudge for due connections and on birthdays"
                                } else {
                                    "Notifications are not supported on this device"
                                },
                                style = TextStyle(
                                    fontFamily = SansFontFamily(),
                                    fontSize = 15.sp,
                                    lineHeight = 24.sp,
                                    color = Ink
                                ),
                                modifier = Modifier.weight(1f)
                            )

                            val knobOffset by animateDpAsState(
                                targetValue = if (notificationsGranted) 28.dp else 4.dp,
                                animationSpec = tween(150)
                            )

                            Box(
                                modifier = Modifier
                                    .size(width = 56.dp, height = 32.dp)
                                    .clip(PxClipShape)
                                    .background(if (notificationsGranted) Ink else Ink.copy(alpha = 0.15f))
                                    .clickable(enabled = notificationsSupported) {
                                        if (notificationsGranted) {
                                            SpheresStore.showToast("Turn notifications off in system settings.")
                                        } else {
                                            scope.launch {
                                                val granted = PlatformNotifications.requestPermission()
                                                notificationsGranted = granted
                                                SpheresStore.showToast(
                                                    if (granted) "Reminders enabled" else "Notifications were not allowed"
                                                )
                                            }
                                        }
                                    }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .offset(x = knobOffset, y = 4.dp)
                                        .size(24.dp)
                                        .clip(PxClipShape)
                                        .background(Color.White)
                                )
                            }
                        }

                        if (notificationsSupported) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Reminders arrive at 9:00 AM local time based on your scheduled check-in intervals.",
                                style = TextStyle(
                                    fontFamily = SansFontFamily(),
                                    fontSize = 13.sp,
                                    color = Muted
                                )
                            )
                        }
                    }
                }

                Card {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        CardLabel(title = "Integrations", icon = IconName.PHONE)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = if (phoneSupported) {
                                    "Automatically log phone check-ins"
                                } else {
                                    "Phone call integration is not supported on this device"
                                },
                                style = TextStyle(
                                    fontFamily = SansFontFamily(),
                                    fontSize = 15.sp,
                                    lineHeight = 24.sp,
                                    color = Ink
                                ),
                                modifier = Modifier.weight(1f)
                            )

                            val phoneEnabled = phoneSupported && state.phoneIntegrationEnabled
                            val phoneKnobOffset by animateDpAsState(
                                targetValue = if (phoneEnabled) 28.dp else 4.dp,
                                animationSpec = tween(150)
                            )

                            Box(
                                modifier = Modifier
                                    .size(width = 56.dp, height = 32.dp)
                                    .clip(PxClipShape)
                                    .background(if (phoneEnabled) Ink else Ink.copy(alpha = 0.15f))
                                    .clickable(enabled = phoneSupported) {
                                        if (phoneEnabled) {
                                            SpheresStore.setPhoneIntegrationEnabled(false)
                                        } else {
                                            scope.launch {
                                                val granted = PlatformPhoneIntegration.requestPermission()
                                                if (granted) {
                                                    SpheresStore.setPhoneIntegrationEnabled(true)
                                                } else {
                                                    SpheresStore.showToast("Call log permission was not allowed")
                                                }
                                            }
                                        }
                                    }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .offset(x = phoneKnobOffset, y = 4.dp)
                                        .size(24.dp)
                                        .clip(PxClipShape)
                                        .background(Color.White)
                                )
                            }
                        }

                        if (phoneSupported) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Adds check-ins for calls longer than 10 minutes.",
                                style = TextStyle(
                                    fontFamily = SansFontFamily(),
                                    fontSize = 13.sp,
                                    color = Muted
                                )
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        val previewColors = INTERVALS.take(3).map { it.color } + UNTRACKED_COLOR
                        for (c in previewColors) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(parseHexColor(c))
                            )
                        }
                    }
                    Text(
                        text = "Spheres by Florian",
                        style = TextStyle(
                            fontFamily = PixelFontFamily(),
                            fontSize = 14.sp,
                            color = Muted
                        )
                    )
                    PixelIcon(name = IconName.RADAR, size = 16.dp, tint = Muted)
                }
            }
        }
    }
}

@Composable
private fun DeleteAccountDialog(onCancel: () -> Unit, onConfirm: () -> Unit) {
    Dialog(onDismissRequest = onCancel) {
        Column(
            modifier = Modifier
                .widthIn(max = 360.dp)
                .fillMaxWidth()
                .clip(SteppedPolygonShape(steps = 3))
                .background(Color.White)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Delete account?",
                style = TextStyle(fontFamily = PixelFontFamily(), fontSize = 22.sp, lineHeight = 28.sp, color = Ink)
            )
            Text(
                text = "This permanently deletes your account, all connections, notes and photos. It can't be undone.",
                style = TextStyle(fontFamily = SansFontFamily(), fontSize = 15.sp, lineHeight = 22.sp, color = Muted)
            )
            Spacer(modifier = Modifier.height(4.dp))
            PixelButton(
                text = "Delete everything",
                icon = IconName.TRASH,
                variant = ButtonVariant.DANGER,
                fullWidth = true,
                onClick = onConfirm
            )
            PixelButton(
                text = "Cancel",
                variant = ButtonVariant.SECONDARY,
                fullWidth = true,
                onClick = onCancel
            )
        }
    }
}
