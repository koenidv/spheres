package de.koenidv.spheres.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import de.koenidv.spheres.platform.currentTimeMillis
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.koenidv.spheres.domain.Quality
import de.koenidv.spheres.domain.formatDay
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import de.koenidv.spheres.ui.theme.CardBg
import de.koenidv.spheres.ui.theme.DangerFill
import de.koenidv.spheres.ui.theme.DangerShadow
import de.koenidv.spheres.ui.theme.DangerText
import de.koenidv.spheres.ui.theme.FieldBg
import de.koenidv.spheres.ui.theme.Ink
import de.koenidv.spheres.ui.theme.Muted
import de.koenidv.spheres.ui.theme.PixelFontFamily
import de.koenidv.spheres.ui.theme.SansFontFamily
import de.koenidv.spheres.ui.theme.tintColor

enum class ButtonVariant {
    PRIMARY, SECONDARY, GHOST, DANGER
}

enum class ButtonSize {
    SM, MD, LG
}

enum class PanelTone {
    TINT, WHITE
}



@Composable
fun PixelShell(
    modifier: Modifier = Modifier,
    fill: Color? = null,
    shadow: Color? = null,
    lift: Dp = 0.dp,
    isPressed: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val currentLift = if (isPressed) lift / 2f else lift
    val faceOffset = if (isPressed) lift / 2f else 0.dp

    Box(
        modifier = modifier,
        propagateMinConstraints = true
    ) {
        if (lift > 0.dp && shadow != null) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(top = currentLift)
                    .clip(PxClipShape)
                    .background(shadow)
            )
        }
        Box(
            modifier = Modifier
                .padding(bottom = lift)
                .offset(y = faceOffset)
                .clip(PxClipShape)
                .then(
                    if (fill != null) Modifier.background(fill)
                    else Modifier.background(if (isPressed) Color.Black.copy(alpha = 0.06f) else Color.Transparent)
                ),
            contentAlignment = Alignment.Center,
            content = content
        )
    }
}

@Composable
fun PixelButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.PRIMARY,
    size: ButtonSize = ButtonSize.MD,
    icon: IconName? = null,
    fullWidth: Boolean = false,
    enabled: Boolean = true,
    text: String? = null,
    content: (@Composable () -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val fill = when (variant) {
        ButtonVariant.PRIMARY -> Ink
        ButtonVariant.SECONDARY -> FieldBg
        ButtonVariant.GHOST -> null
        ButtonVariant.DANGER -> DangerFill
    }
    val shadow = when (variant) {
        ButtonVariant.PRIMARY -> Color(0xFF8A8A8A)
        ButtonVariant.SECONDARY -> Color(0xFFD9D9D9)
        ButtonVariant.GHOST -> null
        ButtonVariant.DANGER -> DangerShadow
    }
    val textColor = when (variant) {
        ButtonVariant.PRIMARY -> Color.White
        ButtonVariant.SECONDARY -> Ink
        ButtonVariant.GHOST -> Ink
        ButtonVariant.DANGER -> DangerText
    }
    val height = when (size) {
        ButtonSize.SM -> 36.dp
        ButtonSize.MD -> 48.dp
        ButtonSize.LG -> 56.dp
    }
    val fontSize = when (size) {
        ButtonSize.SM -> 16.sp
        ButtonSize.MD -> 18.sp
        ButtonSize.LG -> 20.sp
    }
    val iconSize = if (size == ButtonSize.SM) 16.dp else 20.dp
    val hPad = when (size) {
        ButtonSize.SM -> 12.dp
        ButtonSize.MD -> 20.dp
        ButtonSize.LG -> 24.dp
    }
    val gap = if (size == ButtonSize.SM) 6.dp else 8.dp
    val lift = if (variant == ButtonVariant.GHOST) 0.dp else 4.dp

    val buttonMod = modifier
        .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
        .height(height)
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onClick = onClick
        )

    PixelShell(
        modifier = buttonMod,
        fill = fill,
        shadow = shadow,
        lift = lift,
        isPressed = isPressed && enabled
    ) {
        CompositionLocalProvider(LocalContentColor provides textColor) {
            Row(
                modifier = Modifier.padding(horizontal = hPad),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(gap)
            ) {
                if (icon != null) {
                    PixelIcon(name = icon, size = iconSize, tint = textColor)
                }
                if (text != null) {
                    Text(
                        text = text,
                        style = TextStyle(
                            fontFamily = PixelFontFamily(),
                            fontWeight = FontWeight.Medium,
                            fontSize = fontSize,
                            color = textColor
                        )
                    )
                }
                content?.invoke()
            }
        }
    }
}

@Composable
fun PixelIconButton(
    icon: IconName,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    tint: Color = Ink
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    PixelShell(
        modifier = modifier
            .size(size)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        lift = 0.dp,
        isPressed = isPressed
    ) {
        PixelIcon(name = icon, size = 24.dp, tint = tint)
    }
}

@Composable
fun Chip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    color: Color = Ink,
    icon: IconName? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val isNeutral = color == Ink
    val fill = if (selected) color else if (isNeutral) FieldBg else tintColor(color, 22)
    val shadow = if (isNeutral) {
        if (selected) Color(0xFF8A8A8A) else Color(0xFFD9D9D9)
    } else {
        tintColor(color, 45)
    }
    val textColor = if (selected && isNeutral) Color.White else if (selected) Ink else Ink.copy(alpha = 0.7f)

    PixelShell(
        modifier = modifier
            .height(32.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        fill = fill,
        shadow = shadow,
        lift = 2.dp,
        isPressed = isPressed
    ) {
        CompositionLocalProvider(LocalContentColor provides textColor) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (icon != null) {
                    PixelIcon(name = icon, size = 14.dp, tint = textColor)
                }
                Text(
                    text = label,
                    style = TextStyle(
                        fontFamily = PixelFontFamily(),
                        fontSize = 14.sp,
                        color = textColor
                    )
                )
            }
        }
    }
}

@Composable
fun Field(
    modifier: Modifier = Modifier,
    label: String? = null,
    hint: String? = null,
    content: @Composable () -> Unit
) {
    Column(modifier = modifier) {
        if (label != null) {
            Text(
                text = label.uppercase(),
                style = TextStyle(
                    fontFamily = PixelFontFamily(),
                    fontSize = 14.sp,
                    letterSpacing = 0.05.sp,
                    color = Muted
                ),
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
        content()
        if (hint != null) {
            Text(
                text = hint,
                style = TextStyle(
                    fontFamily = SansFontFamily(),
                    fontSize = 13.sp,
                    color = Muted
                ),
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

fun Modifier.autoFocus(enabled: Boolean = true, delayMs: Long = 100L): Modifier = composed {
    if (!enabled) return@composed this
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(enabled) {
        if (delayMs > 0L) delay(delayMs)
        focusRequester.requestFocus()
        keyboard?.show()
    }
    this.focusRequester(focusRequester)
}

@Composable
fun rememberLiveNow(intervalMs: Long = 60_000L): Long {
    var now by remember { mutableLongStateOf(currentTimeMillis()) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                now = currentTimeMillis()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(intervalMs) {
        while (true) {
            delay(intervalMs)
            now = currentTimeMillis()
        }
    }

    return now
}

@Composable
fun DatePickerField(
    dateMillis: Long,
    onDateSelected: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val openPicker = rememberDatePicker(
        initialDateMillis = dateMillis,
        onDateSelected = onDateSelected
    )
    val year = Instant.fromEpochMilliseconds(dateMillis)
        .toLocalDateTime(TimeZone.currentSystemDefault()).year

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(PxClipShape)
            .background(FieldBg)
            .clickable { openPicker() }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "${formatDay(dateMillis)} $year",
            style = TextStyle(
                fontFamily = SansFontFamily(),
                fontSize = 16.sp,
                color = Ink
            )
        )
        PixelIcon(
            name = IconName.CALENDAR,
            size = 18.dp,
            tint = Ink.copy(alpha = 0.55f)
        )
    }
}

@Composable
fun PixelTextInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(PxClipShape)
            .background(FieldBg)
            .padding(horizontal = 16.dp),
        singleLine = singleLine,
        enabled = enabled,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        textStyle = TextStyle(
            fontFamily = SansFontFamily(),
            fontSize = 16.sp,
            color = Ink
        ),
        cursorBrush = SolidColor(Ink),
        decorationBox = { innerTextField ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty() && placeholder.isNotEmpty()) {
                    Text(
                        text = placeholder,
                        style = TextStyle(
                            fontFamily = SansFontFamily(),
                            fontSize = 16.sp,
                            color = Ink.copy(alpha = 0.35f)
                        )
                    )
                }
                innerTextField()
            }
        }
    )
}

@Composable
fun PixelTextArea(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    minLines: Int = 3,
    maxLines: Int = 6,
    enabled: Boolean = true
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .clip(PxClipShape)
            .background(FieldBg)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        minLines = minLines,
        maxLines = maxLines,
        enabled = enabled,
        textStyle = TextStyle(
            fontFamily = SansFontFamily(),
            fontSize = 16.sp,
            lineHeight = 24.sp,
            color = Ink
        ),
        cursorBrush = SolidColor(Ink),
        decorationBox = { innerTextField ->
            Box(contentAlignment = Alignment.TopStart) {
                if (value.isEmpty() && placeholder.isNotEmpty()) {
                    Text(
                        text = placeholder,
                        style = TextStyle(
                            fontFamily = SansFontFamily(),
                            fontSize = 16.sp,
                            lineHeight = 24.sp,
                            color = Ink.copy(alpha = 0.35f)
                        )
                    )
                }
                innerTextField()
            }
        }
    )
}

@Composable
fun PixelPanel(
    modifier: Modifier = Modifier,
    color: Color? = null,
    tone: PanelTone = PanelTone.TINT,
    raised: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isHovered by interactionSource.collectIsHoveredAsState()

    val edge = color ?: Color(0xFFE6E6E6)
    val baseFill = if (color == null) {
        if (tone == PanelTone.WHITE) Color.White else Color(0xFFFAFAFA)
    } else {
        if (tone == PanelTone.WHITE) Color.White else tintColor(color, 20)
    }
    val fill = if (isHovered && onClick != null) Ink.copy(alpha = 0.04f).compositeOver(baseFill) else baseFill
    val shadow = if (color != null) tintColor(color, 45) else Color(0xFFD9D9D9)

    val press = onClick != null
    val currentLift = if (press && isPressed) 2.dp else 4.dp
    val contentOffset = if (press && isPressed) 2.dp else 0.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(if (raised) Modifier.padding(bottom = 4.dp) else Modifier)
            .then(
                if (onClick != null) {
                    Modifier
                        .hoverable(interactionSource)
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = onClick
                        )
                } else Modifier
            )
    ) {
        if (raised) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(top = currentLift)
                    .clip(PanelClipShape)
                    .background(shadow)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = contentOffset)
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(PanelClipShape)
                    .background(edge)
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(PanelClipInnerShape)
                    .background(fill)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                content = content
            )
        }
    }
}

@Composable
fun Card(
    modifier: Modifier = Modifier,
    color: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    PixelPanel(
        modifier = modifier,
        color = color,
        raised = false,
        onClick = onClick,
        content = content
    )
}

@Composable
fun CardLabel(
    title: String,
    modifier: Modifier = Modifier,
    icon: IconName? = null,
    color: Color? = null,
    right: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (icon != null) {
            PixelIcon(
                name = icon,
                size = 18.dp,
                tint = color ?: Ink.copy(alpha = 0.6f)
            )
        }
        Text(
            text = title.uppercase(),
            style = TextStyle(
                fontFamily = PixelFontFamily(),
                fontSize = 14.sp,
                letterSpacing = 0.05.sp,
                color = Ink.copy(alpha = 0.6f)
            ),
            modifier = Modifier.weight(1f)
        )
        right?.invoke()
    }
}

@Composable
fun SpherePill(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val pillMod = modifier
        .height(24.dp)
        .clip(PxClipShape)
        .background(color)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(horizontal = 6.dp)

    Box(
        modifier = pillMod,
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontFamily = PixelFontFamily(),
                fontSize = 16.sp,
                lineHeight = 24.sp,
                color = Ink
            )
        )
    }
}

@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onSubtitleClick: (() -> Unit)? = null,
    right: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title.uppercase(),
                style = TextStyle(
                    fontFamily = PixelFontFamily(),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 32.sp,
                    lineHeight = 36.sp,
                    letterSpacing = 1.92.sp,
                    color = Ink
                ),
                modifier = Modifier.weight(1f, fill = false)
            )
            right?.invoke()
        }
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = TextStyle(
                    fontFamily = SansFontFamily(),
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    color = Ink
                ),
                modifier = Modifier
                    .padding(top = 8.dp)
                    .then(
                        if (onSubtitleClick != null) {
                            Modifier
                                .clip(PxClipShape)
                                .clickable { onSubtitleClick() }
                        } else {
                            Modifier
                        }
                    )
            )
        }
    }
}

@Composable
fun Strong(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = TextStyle(
            fontFamily = PixelFontFamily(),
            fontWeight = FontWeight.Bold,
            color = Ink
        ),
        modifier = modifier
    )
}

@Composable
fun EmptyState(
    icon: IconName,
    text: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    action: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PixelIcon(
            name = icon,
            size = 48.dp,
            tint = Ink.copy(alpha = 0.25f)
        )
        if (title != null) {
            Text(
                text = title,
                style = TextStyle(
                    fontFamily = PixelFontFamily(),
                    fontSize = 20.sp,
                    color = Ink
                ),
                modifier = Modifier.padding(top = 16.dp)
            )
        }
        Text(
            text = text,
            style = TextStyle(
                fontFamily = SansFontFamily(),
                fontSize = 15.sp,
                lineHeight = 24.sp,
                color = Muted
            ),
            modifier = Modifier.padding(top = 8.dp)
        )
        if (action != null) {
            Box(modifier = Modifier.padding(top = 20.dp)) {
                action()
            }
        }
    }
}

data class ToastMessage(
    val id: String,
    val text: String,
    val undo: (() -> Unit)? = null
)

@Composable
fun ToastHost(
    toast: ToastMessage?,
    onDismiss: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var activeToast by remember { mutableStateOf(toast) }
    if (toast != null) activeToast = toast
    val currentToast = activeToast

    AnimatedVisibility(
        visible = toast != null,
        enter = fadeIn(tween(150)) + slideInVertically { it / 2 },
        exit = fadeOut(tween(150)) + slideOutVertically { it / 2 },
        modifier = modifier
    ) {
        if (currentToast != null) {
            Row(
                modifier = Modifier
                    .clip(PxClipShape)
                    .background(Ink)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = currentToast.text,
                    style = TextStyle(
                        fontFamily = PixelFontFamily(),
                        fontSize = 16.sp,
                        color = Color.White
                    )
                )
                if (currentToast.undo != null) {
                    Text(
                        text = "UNDO",
                        style = TextStyle(
                            fontFamily = PixelFontFamily(),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            letterSpacing = 0.05.sp,
                            color = Color(0xFFF6B4D4)
                        ),
                        modifier = Modifier.clickable {
                            currentToast.undo.invoke()
                            onDismiss()
                        }
                    )
                }
            }
        }
    }
}

data class SegmentedOption<T>(val value: T, val label: String)

@Composable
fun <T> Segmented(
    options: List<SegmentedOption<T>>,
    value: T,
    onChange: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(PxClipShape)
            .background(FieldBg)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { opt ->
            val on = opt.value == value
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(PxClipShape)
                    .background(if (on) Color(0xFFE2E2E2) else FieldBg)
                    .clickable { onChange(opt.value) }
            ) {
                if (on) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clip(PxClipInShape)
                            .background(Color.White)
                    )
                }
                Box(
                    modifier = Modifier.matchParentSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = opt.label,
                        style = TextStyle(
                            fontFamily = PixelFontFamily(),
                            fontSize = 15.sp,
                            color = if (on) Ink else Ink.copy(alpha = 0.55f)
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun QualityPicker(
    value: Quality?,
    onChange: (Quality?) -> Unit,
    modifier: Modifier = Modifier
) {
    val qualities = Quality.entries
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        qualities.forEach { q ->
            val on = value == q
            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = { onChange(if (on) null else q) }
                    ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                PixelShell(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    fill = if (on) Ink else FieldBg,
                    shadow = if (on) Color(0xFF8A8A8A) else Color(0xFFD9D9D9),
                    lift = 4.dp,
                    isPressed = isPressed
                ) {
                    Text(
                        text = q.symbol,
                        style = TextStyle(
                            fontFamily = PixelFontFamily(),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 20.sp,
                            color = if (on) Color.White else Ink.copy(alpha = 0.7f)
                        )
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = q.label,
                    style = TextStyle(
                        fontFamily = PixelFontFamily(),
                        fontSize = 12.sp,
                        color = if (on) Ink else Muted
                    )
                )
            }
        }
    }
}
