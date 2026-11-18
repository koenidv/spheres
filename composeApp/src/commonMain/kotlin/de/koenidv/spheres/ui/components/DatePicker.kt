package de.koenidv.spheres.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.koenidv.spheres.domain.formatDay
import de.koenidv.spheres.platform.currentTimeMillis
import de.koenidv.spheres.ui.theme.Coral
import de.koenidv.spheres.ui.theme.FieldBg
import de.koenidv.spheres.ui.theme.Ink
import de.koenidv.spheres.ui.theme.Muted
import de.koenidv.spheres.ui.theme.PixelFontFamily
import de.koenidv.spheres.ui.theme.SansFontFamily
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

private const val MinYear = 1900
private const val MaxYear = 2100
private const val PageCount = (MaxYear - MinYear + 1) * 12

private val DayCellHeight = 44.dp
private val DayFaceSize = 40.dp
private val DayLift = 3.dp
private val WeekdayRowHeight = 28.dp
private val CalendarHeight = WeekdayRowHeight + DayCellHeight * 6
private val DayShadow = Color(0xFF8A8A8A)

private val MonthNames = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December"
)
private val WeekdayLabels = listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su")

/** Pager page for a month, counted from January of [MinYear]. */
internal fun monthPage(year: Int, month: Int): Int = (year - MinYear) * 12 + (month - 1)

/**
 * The 6×7 cells of a month, Monday first. Cells outside the month are null, so the grid always
 * has the same height no matter how many weeks the month spans.
 */
internal fun monthGrid(year: Int, month: Int): List<LocalDate?> {
    val first = LocalDate(year, month, 1)
    val leading = first.dayOfWeek.isoDayNumber - 1
    val length = first.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY).dayOfMonth
    return List(42) { i ->
        val day = i - leading + 1
        if (day in 1..length) LocalDate(year, month, day) else null
    }
}

private fun LocalDate.toNoonMillis(tz: TimeZone): Long =
    LocalDateTime(year, monthNumber, dayOfMonth, 12, 0, 0, 0).toInstant(tz).toEpochMilliseconds()

/**
 * Fonts and text styles are resolved once per picker session. Resolving a FontFamily inside every
 * cell is what made paging and selecting stutter.
 */
@Immutable
private class PickerStyles(pixel: FontFamily, sans: FontFamily) {
    val title = TextStyle(fontFamily = pixel, fontSize = 20.sp, lineHeight = 24.sp, color = Ink)
    val day = TextStyle(fontFamily = pixel, fontSize = 18.sp)
    val year = TextStyle(fontFamily = pixel, fontSize = 17.sp)
    val weekday = TextStyle(fontFamily = sans, fontWeight = FontWeight.Medium, fontSize = 12.sp)
    val summary = TextStyle(
        fontFamily = sans,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        color = Muted,
        textAlign = TextAlign.Center
    )
}

private val LocalPickerStyles = staticCompositionLocalOf<PickerStyles> {
    error("DatePickerHost is missing")
}

private val WeekendMuted = Muted.copy(alpha = 0.7f)
private val DisabledInk = Ink.copy(alpha = 0.25f)

private class DatePickerRequest(
    val id: Int,
    val initialDateMillis: Long,
    val onDateSelected: (Long) -> Unit,
)

private object DatePickerController {
    var request by mutableStateOf<DatePickerRequest?>(null)
    private var nextId = 0

    fun show(initialDateMillis: Long, onDateSelected: (Long) -> Unit) {
        request = DatePickerRequest(nextId++, initialDateMillis, onDateSelected)
    }

    fun dismiss() {
        request = null
    }
}

/**
 * Returns a function that opens the date picker sheet. The result is delivered as noon of the
 * picked day in the system time zone. Requires [DatePickerHost] to be part of the composition.
 */
@Composable
fun rememberDatePicker(
    initialDateMillis: Long,
    onDateSelected: (Long) -> Unit,
): () -> Unit {
    val focusManager: FocusManager = LocalFocusManager.current
    val currentInitial by rememberUpdatedState(initialDateMillis)
    val currentCallback by rememberUpdatedState(onDateSelected)
    return remember(focusManager) {
        {
            focusManager.clearFocus()
            DatePickerController.show(currentInitial) { currentCallback(it) }
        }
    }
}

/** Renders the date picker above everything else, including other sheets. */
@Composable
fun DatePickerHost() {
    val request = DatePickerController.request
    var lastRequest by remember { mutableStateOf(request) }
    if (request != null) lastRequest = request
    val active = lastRequest

    PixelSheet(
        open = request != null,
        onClose = { DatePickerController.dismiss() },
        scrollable = false
    ) {
        if (active != null) {
            key(active.id) {
                val pixel = PixelFontFamily()
                val sans = SansFontFamily()
                val styles = remember(pixel, sans) { PickerStyles(pixel, sans) }
                CompositionLocalProvider(LocalPickerStyles provides styles) {
                    DatePickerContent(
                        initialDateMillis = active.initialDateMillis,
                        onConfirm = { millis ->
                            DatePickerController.dismiss()
                            active.onDateSelected(millis)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DatePickerContent(
    initialDateMillis: Long,
    onConfirm: (Long) -> Unit,
) {
    val tz = remember { TimeZone.currentSystemDefault() }
    val today = remember {
        Instant.fromEpochMilliseconds(currentTimeMillis()).toLocalDateTime(tz).date
    }
    val initial = remember {
        val date = Instant.fromEpochMilliseconds(initialDateMillis).toLocalDateTime(tz).date
        if (date.year in MinYear..MaxYear) date else today
    }

    var selected by remember { mutableStateOf(initial) }
    var yearMode by remember { mutableStateOf(false) }
    val pagerState = rememberPagerState(
        initialPage = monthPage(initial.year, initial.monthNumber)
    ) { PageCount }
    val scope = rememberCoroutineScope()
    val onSelect = remember { { date: LocalDate -> selected = date } }
    val styles = LocalPickerStyles.current

    val shownYear = MinYear + pagerState.currentPage / 12
    val shownMonth = pagerState.currentPage % 12 + 1

    Column(modifier = Modifier.fillMaxWidth()) {
        PickerHeader(
            label = "${MonthNames[shownMonth - 1]} $shownYear",
            yearMode = yearMode,
            canGoBack = pagerState.currentPage > 0,
            canGoForward = pagerState.currentPage < PageCount - 1,
            onToggleYearMode = { yearMode = !yearMode },
            onPrevious = {
                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
            },
            onNext = {
                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
            }
        )

        Box(modifier = Modifier.fillMaxWidth().height(CalendarHeight)) {
            AnimatedContent(
                targetState = yearMode,
                transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) },
                label = "calendarMode"
            ) { showYears ->
                if (showYears) {
                    YearGrid(
                        shownYear = shownYear,
                        currentYear = today.year,
                        onYearPicked = { year ->
                            scope.launch {
                                pagerState.scrollToPage(monthPage(year, shownMonth))
                                yearMode = false
                            }
                        }
                    )
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        WeekdayRow()
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            key = { it }
                        ) { page ->
                            val year = MinYear + page / 12
                            val month = page % 12 + 1
                            // Only pages that contain the selection / today see a changed argument,
                            // so picking a day recomposes one page instead of all of them.
                            MonthGrid(
                                year = year,
                                month = month,
                                selectedDay = if (selected.year == year && selected.monthNumber == month) selected.dayOfMonth else 0,
                                todayDay = if (today.year == year && today.monthNumber == month) today.dayOfMonth else 0,
                                onSelect = onSelect
                            )
                        }
                    }
                }
            }
        }

        Text(
            text = "${formatDay(selected.toNoonMillis(tz))} ${selected.year}",
            style = styles.summary,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PixelButton(
                text = "Today",
                variant = ButtonVariant.SECONDARY,
                onClick = {
                    selected = today
                    yearMode = false
                    scope.launch {
                        pagerState.animateScrollToPage(monthPage(today.year, today.monthNumber))
                    }
                }
            )
            PixelButton(
                text = "Done",
                fullWidth = true,
                modifier = Modifier.weight(1f),
                onClick = { onConfirm(selected.toNoonMillis(tz)) }
            )
        }
    }
}

@Composable
private fun PickerHeader(
    label: String,
    yearMode: Boolean,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onToggleYearMode: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val chevronAngle by animateFloatAsState(
        targetValue = if (yearMode) -90f else 90f,
        animationSpec = tween(180),
        label = "chevron"
    )

    Row(
        modifier = Modifier.fillMaxWidth().height(48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(PxClipShape)
                .clickable(role = Role.Button, onClick = onToggleYearMode)
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Crossfade(targetState = label, animationSpec = tween(140), label = "month") { text ->
                Text(
                    text = text,
                    style = LocalPickerStyles.current.title,
                    modifier = Modifier.semantics {
                        contentDescription = "$text, choose year"
                    }
                )
            }
            Spacer(Modifier.width(8.dp))
            PixelIcon(
                name = IconName.CHEVRON_RIGHT,
                size = 16.dp,
                tint = Ink,
                modifier = Modifier.graphicsLayer { rotationZ = chevronAngle }
            )
        }

        AnimatedVisibility(
            visible = !yearMode,
            enter = fadeIn(tween(140)),
            exit = fadeOut(tween(100))
        ) {
            Row {
                PixelIconButton(
                    icon = IconName.CHEVRON_RIGHT,
                    onClick = { if (canGoBack) onPrevious() },
                    tint = if (canGoBack) Ink else DisabledInk,
                    modifier = Modifier.graphicsLayer { scaleX = -1f }
                )
                PixelIconButton(
                    icon = IconName.CHEVRON_RIGHT,
                    onClick = { if (canGoForward) onNext() },
                    tint = if (canGoForward) Ink else DisabledInk
                )
            }
        }
    }
}

@Composable
private fun WeekdayRow() {
    val style = LocalPickerStyles.current.weekday
    Row(modifier = Modifier.fillMaxWidth().height(WeekdayRowHeight)) {
        WeekdayLabels.forEachIndexed { index, label ->
            Box(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (index >= 5) WeekendMuted else Muted,
                    style = style
                )
            }
        }
    }
}

@Composable
private fun MonthGrid(
    year: Int,
    month: Int,
    selectedDay: Int,
    todayDay: Int,
    onSelect: (LocalDate) -> Unit,
) {
    val cells = remember(year, month) { monthGrid(year, month) }
    Column(modifier = Modifier.fillMaxSize()) {
        for (row in 0 until 6) {
            Row(modifier = Modifier.fillMaxWidth().height(DayCellHeight)) {
                for (col in 0 until 7) {
                    val date = cells[row * 7 + col]
                    Box(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (date != null) {
                            DayCell(
                                date = date,
                                selected = date.dayOfMonth == selectedDay,
                                isToday = date.dayOfMonth == todayDay,
                                weekend = col >= 5,
                                onSelect = onSelect
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    selected: Boolean,
    isToday: Boolean,
    weekend: Boolean,
    onSelect: (LocalDate) -> Unit,
) {
    Box(
        modifier = Modifier
            .size(DayFaceSize)
            .semantics {
                this.selected = selected
                contentDescription = "${date.dayOfMonth} ${MonthNames[date.monthNumber - 1]} ${date.year}"
            }
            .clickable(
                interactionSource = null,
                indication = null,
                role = Role.Button,
                onClick = { onSelect(date) }
            )
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = DayLift)
                    .clip(PxClipShape)
                    .background(DayShadow)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = if (selected) DayLift else 0.dp)
                .clip(PxClipShape)
                .then(if (selected) Modifier.background(Ink) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                color = when {
                    selected -> Color.White
                    weekend -> Muted
                    else -> Ink
                },
                style = LocalPickerStyles.current.day
            )
            if (isToday) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 3.dp)
                        .size(4.dp)
                        .background(if (selected) Color.White else Coral)
                )
            }
        }
    }
}

@Composable
private fun YearGrid(
    shownYear: Int,
    currentYear: Int,
    onYearPicked: (Int) -> Unit,
) {
    val columns = 4
    val gridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = (((shownYear - MinYear) / columns) - 2).coerceAtLeast(0) * columns
    )
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(MaxYear - MinYear + 1) { index ->
            val year = MinYear + index
            YearCell(
                year = year,
                selected = year == shownYear,
                isCurrent = year == currentYear,
                onClick = { onYearPicked(year) }
            )
        }
    }
}

@Composable
private fun YearCell(
    year: Int,
    selected: Boolean,
    isCurrent: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(PxClipShape)
            .background(if (selected) Ink else FieldBg)
            .semantics { this.selected = selected }
            .clickable(
                interactionSource = null,
                indication = null,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = year.toString(),
            color = when {
                selected -> Color.White
                isCurrent -> Coral
                else -> Ink
            },
            style = LocalPickerStyles.current.year
        )
    }
}
