package de.koenidv.spheres.ui.sheets

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.koenidv.spheres.data.PaywallReason
import de.koenidv.spheres.data.SheetState
import de.koenidv.spheres.data.SpheresStore
import de.koenidv.spheres.domain.FREE_CONNECTIONS_LIMIT
import de.koenidv.spheres.domain.FREE_SPHERES_LIMIT
import de.koenidv.spheres.domain.Person
import de.koenidv.spheres.domain.intervalInfo
import de.koenidv.spheres.ui.components.ButtonSize
import de.koenidv.spheres.ui.components.ButtonVariant
import de.koenidv.spheres.ui.components.IconName
import de.koenidv.spheres.ui.components.PanelClipShape
import de.koenidv.spheres.ui.components.PersonAvatar
import de.koenidv.spheres.ui.components.PixelAvatar
import de.koenidv.spheres.ui.components.PixelButton
import de.koenidv.spheres.ui.components.PixelIcon
import de.koenidv.spheres.ui.components.PixelPanel
import de.koenidv.spheres.ui.components.PxClipShape
import de.koenidv.spheres.ui.components.SpherePill
import de.koenidv.spheres.ui.theme.Amber
import de.koenidv.spheres.ui.theme.Ink
import de.koenidv.spheres.ui.theme.MonthlyPurple
import de.koenidv.spheres.ui.theme.Muted
import de.koenidv.spheres.ui.theme.PixelFontFamily
import de.koenidv.spheres.ui.theme.SansFontFamily
import de.koenidv.spheres.ui.theme.WeeklyPink
import de.koenidv.spheres.ui.theme.parseHexColor
import de.koenidv.spheres.ui.theme.tintColor
import kotlin.math.abs
import kotlin.math.hypot

@Composable
fun UnlockSheet(
    sheet: SheetState.Paywall
) {
    val state by SpheresStore.state.collectAsState()
    val people = state.people
    val spheres = state.spheres
    val price = state.unlimitedPrice
    val isPurchasing = state.isPurchasing
    val pendingTransfer = state.pendingTransfer

    if (pendingTransfer != null) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SpherePill(
                label = "ALREADY LINKED",
                color = tintColor(Amber, 35)
            )

            Spacer(modifier = Modifier.height(14.dp))

            PixelPanel(
                color = Amber,
                raised = true
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(PxClipShape)
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        PixelIcon(name = IconName.LOCK, size = 20.dp, tint = Amber)
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Linked to another account",
                            style = TextStyle(
                                fontFamily = PixelFontFamily(),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 20.sp,
                                lineHeight = 26.sp,
                                color = Ink
                            )
                        )
                        Text(
                            text = "This Spheres Unlimited purchase is currently linked to another Spheres account. You can sign out and log in with that account instead, or move Unlimited to this account (your other account will return to the free tier).",
                            style = TextStyle(
                                fontFamily = SansFontFamily(),
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                color = Ink.copy(alpha = 0.78f)
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            PixelButton(
                text = if (isPurchasing) "Moving…" else "Move to this account",
                icon = IconName.SPARKLE,
                variant = ButtonVariant.PRIMARY,
                size = ButtonSize.LG,
                fullWidth = true,
                enabled = !isPurchasing,
                onClick = { SpheresStore.confirmTransferPurchase() }
            )

            Spacer(modifier = Modifier.height(10.dp))

            PixelButton(
                text = "Sign out & switch account",
                icon = IconName.LOGOUT,
                variant = ButtonVariant.SECONDARY,
                size = ButtonSize.MD,
                fullWidth = true,
                enabled = !isPurchasing,
                onClick = { SpheresStore.signOut() }
            )

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .height(40.dp)
                    .clip(PxClipShape)
                    .clickable(enabled = !isPurchasing) { SpheresStore.dismissTransferPrompt() }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Cancel",
                    style = TextStyle(
                        fontFamily = PixelFontFamily(),
                        fontSize = 15.sp,
                        color = Muted
                    )
                )
            }
        }
        return
    }

    val badgeInfo = remember(sheet.reason, people.size, spheres.size) {
        when (sheet.reason) {
            PaywallReason.ConnectionsLimit ->
                Pair("${people.size} / $FREE_CONNECTIONS_LIMIT CONNECTIONS", WeeklyPink)
            PaywallReason.SpheresLimit ->
                Pair("${spheres.size} / $FREE_SPHERES_LIMIT SPHERES", MonthlyPurple)
            is PaywallReason.EventGraceEnded ->
                Pair("EVENT FLOW COMPLETE", Amber)
            PaywallReason.Settings ->
                Pair("SPHERES UNLIMITED", parseHexColor("#3fbf9f"))
        }
    }

    val headline = when (sheet.reason) {
        PaywallReason.ConnectionsLimit -> "Your circle is growing"
        PaywallReason.SpheresLimit -> "More worlds to gather"
        is PaywallReason.EventGraceEnded -> "Keep the momentum going"
        PaywallReason.Settings -> "Keep everyone close"
    }

    val bodyText = when (val reason = sheet.reason) {
        PaywallReason.ConnectionsLimit ->
            "You're keeping ${people.size} people close. Unlock Spheres Unlimited once to make room for everyone who matters."
        PaywallReason.SpheresLimit ->
            "You've filled all $FREE_SPHERES_LIMIT free spheres. Unlock unlimited spheres once to organize every circle of your life."
        is PaywallReason.EventGraceEnded ->
            "The 24-hour flow window for ${reason.eventName ?: "this event"} has wrapped up. Unlock Unlimited once to keep adding everyone you meet."
        PaywallReason.Settings ->
            "One tiny purchase. No subscriptions, no recurring fees — just unlimited space for the people in your life."
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SpherePill(
            label = badgeInfo.first,
            color = tintColor(badgeInfo.second, 35)
        )

        Spacer(modifier = Modifier.height(14.dp))

        ConstellationHero(
            people = people,
            accent = badgeInfo.second
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = headline,
            style = TextStyle(
                fontFamily = PixelFontFamily(),
                fontWeight = FontWeight.SemiBold,
                fontSize = 26.sp,
                lineHeight = 32.sp,
                color = Ink,
                textAlign = TextAlign.Center
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = bodyText,
            style = TextStyle(
                fontFamily = SansFontFamily(),
                fontSize = 15.sp,
                lineHeight = 22.sp,
                color = Muted,
                textAlign = TextAlign.Center
            ),
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Spacer(modifier = Modifier.height(18.dp))

        PixelPanel(
            color = MonthlyPurple,
            raised = true
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                PerkRow(
                    icon = IconName.USERS,
                    tint = WeeklyPink,
                    title = "Unlimited connections",
                    subtitle = "No 25-person cap — keep track of every friend, family member & acquaintance"
                )
                PerkRow(
                    icon = IconName.RADAR,
                    tint = MonthlyPurple,
                    title = "Unlimited spheres & events",
                    subtitle = "Create as many circles, cities, clubs, and gatherings as life brings"
                )
                PerkRow(
                    icon = IconName.HEART,
                    tint = parseHexColor("#3fbf9f"),
                    title = "One-time purchase · Yours forever",
                    subtitle = "Pay $price once — no subscription, synced across all your devices"
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        PixelButton(
            text = if (isPurchasing) "Processing…" else "Unlock forever · $price",
            icon = IconName.SPARKLE,
            variant = ButtonVariant.PRIMARY,
            size = ButtonSize.LG,
            fullWidth = true,
            enabled = !isPurchasing,
            onClick = { SpheresStore.purchaseUnlimited() }
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .height(40.dp)
                    .clip(PxClipShape)
                    .clickable(enabled = !isPurchasing) { SpheresStore.restorePurchases() }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Restore purchase",
                    style = TextStyle(
                        fontFamily = PixelFontFamily(),
                        fontSize = 15.sp,
                        color = if (isPurchasing) Muted.copy(alpha = 0.4f) else Ink
                    )
                )
            }

            Box(
                modifier = Modifier
                    .height(40.dp)
                    .clip(PxClipShape)
                    .clickable(enabled = !isPurchasing) { SpheresStore.closeSheet() }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Not now",
                    style = TextStyle(
                        fontFamily = PixelFontFamily(),
                        fontSize = 15.sp,
                        color = Muted
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Pay once ($price). No subscription. Restore anytime.",
            style = TextStyle(
                fontFamily = SansFontFamily(),
                fontSize = 12.sp,
                color = Muted.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )
        )
    }
}

@Composable
private fun PerkRow(
    icon: IconName,
    tint: Color,
    title: String,
    subtitle: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(PxClipShape)
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            PixelIcon(name = icon, size = 20.dp, tint = tint)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = TextStyle(
                    fontFamily = PixelFontFamily(),
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp,
                    lineHeight = 20.sp,
                    color = Ink
                )
            )
            Text(
                text = subtitle,
                style = TextStyle(
                    fontFamily = SansFontFamily(),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = Ink.copy(alpha = 0.68f)
                )
            )
        }
    }
}

private data class HeroArcSpec(val r: Float, val colorHex: String, val alpha: Float)

private val HERO_ARCS = listOf(
    HeroArcSpec(26f, "#eb78b0", 0.35f),
    HeroArcSpec(19f, "#9d7ce7", 0.45f),
    HeroArcSpec(12f, "#5b9cf0", 0.55f),
)

@Composable
private fun ConstellationHero(
    people: List<Person>,
    accent: Color
) {
    val transition = rememberInfiniteTransition()
    val floatY by transition.animateFloat(
        initialValue = -2.5f,
        targetValue = 2.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    val twinkle by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    val featured = remember(people) {
        val tracked = people.filter { it.intervalDays != null }
        val pool = if (tracked.size >= 4) tracked else people
        pool.take(4)
    }
    val fallbackNames = remember { listOf("Maya", "Leo", "Sam", "Nora") }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(128.dp)
            .clip(PanelClipShape)
            .background(tintColor(accent, 14))
    ) {
        val w = maxWidth
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cols = 56
            val rows = 22
            val cellW = size.width / cols
            val cellH = size.height / rows
            val cx = cols / 2f
            val cy = rows.toFloat() + 3f
            for (arc in HERO_ARCS) {
                val c = parseHexColor(arc.colorHex).copy(alpha = arc.alpha)
                for (y in 0 until rows) {
                    for (x in 0 until cols) {
                        val d = hypot(x + 0.5f - cx, y + 0.5f - cy)
                        if (abs(d - arc.r) < 0.55f) {
                            drawRect(
                                color = c,
                                topLeft = Offset(x * cellW, y * cellH),
                                size = Size(cellW, cellH)
                            )
                        }
                    }
                }
            }
        }

        PixelIcon(
            name = IconName.SPARKLE,
            size = 18.dp,
            tint = Amber.copy(alpha = twinkle),
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 22.dp, y = 16.dp)
        )
        PixelIcon(
            name = IconName.SPARKLE,
            size = 14.dp,
            tint = parseHexColor("#5b9cf0").copy(alpha = 1.35f - twinkle),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-24).dp, y = 20.dp)
        )
        PixelIcon(
            name = IconName.HEART,
            size = 20.dp,
            tint = WeeklyPink,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (14f - floatY * 0.6f).dp)
        )

        val positions = remember(w) {
            listOf(
                Pair(w * 0.14f - 20.dp, 60.dp),
                Pair(w * 0.36f - 20.dp, 36.dp),
                Pair(w * 0.64f - 20.dp, 36.dp),
                Pair(w * 0.86f - 20.dp, 60.dp),
            )
        }

        positions.forEachIndexed { idx, (left, top) ->
            val dir = if (idx % 2 == 0) 1f else -1f
            Box(
                modifier = Modifier
                    .offset(x = left, y = top + (floatY * dir).dp)
                    .size(44.dp)
                    .clip(PxClipShape)
                    .background(Color.White)
                    .padding(3.dp)
            ) {
                val p = featured.getOrNull(idx)
                if (p != null) {
                    PersonAvatar(
                        person = p,
                        colorHex = intervalInfo(p.intervalDays).color,
                        size = 38.dp
                    )
                } else {
                    PixelAvatar(
                        name = fallbackNames[idx % fallbackNames.size],
                        colorHex = listOf("#eb78b0", "#9d7ce7", "#5b9cf0", "#3fbf9f")[idx % 4],
                        size = 38.dp
                    )
                }
            }
        }
    }
}
