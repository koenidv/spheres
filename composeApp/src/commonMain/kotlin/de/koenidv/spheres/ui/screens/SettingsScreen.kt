package de.koenidv.spheres.ui.screens

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.koenidv.spheres.data.SpheresStore
import de.koenidv.spheres.domain.INTERVALS
import de.koenidv.spheres.domain.UNTRACKED_COLOR
import de.koenidv.spheres.platform.currentTimeMillis
import de.koenidv.spheres.platform.PlatformNotifications
import de.koenidv.spheres.platform.shareJsonFile
import de.koenidv.spheres.ui.components.ButtonSize
import de.koenidv.spheres.ui.components.ButtonVariant
import de.koenidv.spheres.ui.components.Card
import de.koenidv.spheres.ui.components.CardLabel
import de.koenidv.spheres.ui.components.IconName
import de.koenidv.spheres.ui.components.PixelAvatar
import de.koenidv.spheres.ui.components.PixelButton
import de.koenidv.spheres.ui.components.PixelIcon
import de.koenidv.spheres.ui.components.PxClipShape
import de.koenidv.spheres.ui.components.ScreenHeader
import de.koenidv.spheres.ui.theme.Ink
import de.koenidv.spheres.ui.theme.Muted
import de.koenidv.spheres.ui.theme.PixelFontFamily
import de.koenidv.spheres.ui.theme.SansFontFamily
import de.koenidv.spheres.ui.theme.parseHexColor
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun SettingsScreen(
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by SpheresStore.state.collectAsState()
    val user = state.user
    val scope = rememberCoroutineScope()

    var notificationsGranted by remember {
        mutableStateOf(PlatformNotifications.isGranted())
    }
    val notificationsSupported = remember {
        PlatformNotifications.isSupported()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 96.dp)
        ) {
            ScreenHeader(title = "Settings")

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
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
                                    "Get a nudge when a connection is due and on birthdays."
                                } else {
                                    "Notifications are not supported on this device."
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
                        CardLabel(title = "Data", icon = IconName.DOWNLOAD)
                        PixelButton(
                            text = "Export JSON",
                            icon = IconName.DOWNLOAD,
                            variant = ButtonVariant.SECONDARY,
                            size = ButtonSize.MD,
                            fullWidth = true,
                            onClick = {
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
                        text = "Spheres v0.1.0",
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
