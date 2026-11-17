package de.koenidv.spheres.domain

import de.koenidv.spheres.platform.currentTimeMillis
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

const val DAY = 86_400_000L

const val FREE_CONNECTIONS_LIMIT = 25
const val FREE_SPHERES_LIMIT = 3
const val EVENT_GRACE_DURATION_MS = DAY
const val UNLIMITED_PRODUCT_ID = "unlimited"
const val UNLIMITED_PURCHASE_OPTION_ID = "unlimited-4"
const val DEFAULT_UNLIMITED_PRICE = "3,99 €"

val SPHERE_COLORS = listOf(
    "#eb78b0",
    "#e36f73",
    "#9d7ce7",
    "#5b9cf0",
    "#3fbf9f",
    "#f0a83c",
    "#8bc34a",
    "#8a8a8a",
)

data class IntervalSpec(val days: Int, val label: String, val color: String)

val INTERVALS: List<IntervalSpec> = listOf(
    IntervalSpec(7, "Weekly", "#eb78b0"),
    IntervalSpec(14, "2 Weekly", "#e36f73"),
    IntervalSpec(30, "Monthly", "#9d7ce7"),
    IntervalSpec(90, "Quarterly", "#5b9cf0"),
    IntervalSpec(180, "Half-yearly", "#3fbf9f"),
    IntervalSpec(365, "Yearly", "#f0a83c"),
)

const val UNTRACKED_COLOR = "#8a8a8a"

data class IntervalInfo(val days: Int?, val label: String, val color: String)

fun intervalInfo(days: Int?): IntervalInfo {
    if (days == null) return IntervalInfo(null, "Not tracked", UNTRACKED_COLOR)
    val hit = INTERVALS.firstOrNull { it.days == days }
    if (hit != null) return IntervalInfo(hit.days, hit.label, hit.color)
    return IntervalInfo(days, "Every $days days", UNTRACKED_COLOR)
}

private val SHORT_MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
private val MONTHS = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December"
)

fun formatDay(ts: Long): String {
    val localDateTime = Instant.fromEpochMilliseconds(ts).toLocalDateTime(TimeZone.currentSystemDefault())
    val weekday = when (localDateTime.dayOfWeek) {
        DayOfWeek.MONDAY -> "Mon"
        DayOfWeek.TUESDAY -> "Tue"
        DayOfWeek.WEDNESDAY -> "Wed"
        DayOfWeek.THURSDAY -> "Thu"
        DayOfWeek.FRIDAY -> "Fri"
        DayOfWeek.SATURDAY -> "Sat"
        DayOfWeek.SUNDAY -> "Sun"
    }
    val month = SHORT_MONTHS.getOrElse(localDateTime.monthNumber - 1) { "" }
    return "$weekday ${localDateTime.dayOfMonth} $month"
}

fun sameDay(a: Long, b: Long): Boolean {
    val tz = TimeZone.currentSystemDefault()
    val dateA = Instant.fromEpochMilliseconds(a).toLocalDateTime(tz).date
    val dateB = Instant.fromEpochMilliseconds(b).toLocalDateTime(tz).date
    return dateA == dateB
}

fun sphereIdsViaEvents(p: Person, events: List<SocialEvent>): List<String> {
    val ids = mutableSetOf<String>()
    for (e in events) {
        if (p.eventIds.contains(e.id)) {
            for (s in e.sphereIds) {
                if (!p.sphereIds.contains(s)) ids.add(s)
            }
        }
    }
    return ids.toList()
}

fun inSphere(p: Person, sphereId: String, events: List<SocialEvent>): Boolean {
    return p.sphereIds.contains(sphereId) || events.any { e ->
        e.sphereIds.contains(sphereId) && p.eventIds.contains(e.id)
    }
}

fun entriesByPerson(entries: List<Entry>): Map<String, List<Entry>> {
    val map = mutableMapOf<String, MutableList<Entry>>()
    for (e in entries) {
        map.getOrPut(e.personId) { mutableListOf() }.add(e)
    }
    return map.mapValues { (_, list) -> list.sortedByDescending { it.at } }
}

fun lastContact(p: Person, entries: List<Entry>?): Long? {
    var last = p.baseContactAt
    for (e in entries ?: emptyList()) {
        if (!e.kind.isNote && (last == null || e.at > last)) {
            last = e.at
        }
    }
    return last
}

enum class DueStatus { OVERDUE, SOON, OK }

data class DueInfo(val last: Long?, val dueAt: Long, val daysLeft: Int, val status: DueStatus)

fun dueInfo(p: Person, entries: List<Entry>?, now: Long = currentTimeMillis()): DueInfo? {
    val interval = p.intervalDays ?: return null
    val last = lastContact(p, entries)
    val anchor = last ?: p.createdAt.takeIf { it > 0L } ?: now
    val dueAt = maxOf(anchor + interval * DAY, p.snoozedUntil ?: 0L)
    val daysLeft = ceil((dueAt - now).toDouble() / DAY).toInt()
    val soonThreshold = maxOf(1, round(interval * 0.2).toInt())
    val status = when {
        now >= dueAt -> DueStatus.OVERDUE
        daysLeft <= soonThreshold -> DueStatus.SOON
        else -> DueStatus.OK
    }
    return DueInfo(last = last, dueAt = dueAt, daysLeft = daysLeft, status = status)
}

data class RelationshipQualityResult(val weight: Double, val quality: Quality)

fun relationshipQuality(entries: List<Entry>?): RelationshipQualityResult? {
    val recent = (entries ?: emptyList())
        .filter { !it.kind.isNote }
        .sortedByDescending { it.at }
        .take(3)
    if (recent.none { it.quality != null }) return null
    var sum = 0.0
    var total = 0
    recent.forEachIndexed { i, e ->
        val weight = e.quality?.weight ?: Quality.REGULAR.weight
        val factor = 3 - i
        sum += factor * weight
        total += factor
    }
    val weight = sum / total
    val quality = Quality.entries.minByOrNull { abs(it.weight - weight) } ?: Quality.REGULAR
    return RelationshipQualityResult(weight = weight, quality = quality)
}

data class ContactScore(val score: Int, val priority: Double)

private data class QualityScoreSpec(val start: Double, val stretch: Double)

private val QUALITY_SCORE = mapOf(
    Quality.BAD to QualityScoreSpec(50.0, 1.0),
    Quality.REGULAR to QualityScoreSpec(100.0, 1.0),
    Quality.GOOD to QualityScoreSpec(100.0, 1.25),
    Quality.AMAZING to QualityScoreSpec(100.0, 1.5),
)

fun contactScore(
    p: Person,
    entries: List<Entry>?,
    now: Long = currentTimeMillis(),
): ContactScore? {
    val intervalDays = p.intervalDays ?: return null
    val last = lastContact(p, entries)
    val latest = entries?.firstOrNull { !it.kind.isNote && it.at == last }
    val q = QUALITY_SCORE[latest?.quality ?: Quality.REGULAR] ?: QualityScoreSpec(100.0, 1.0)
    val anchor = last ?: p.createdAt.takeIf { it > 0L } ?: now
    val elapsed = now - anchor
    val score = round(min(100.0, max(0.0, q.start - (100.0 * elapsed) / (2.0 * intervalDays * q.stretch * DAY)))).toInt()
    val weight = relationshipQuality(entries)?.weight ?: 1.0
    val snoozed = (p.snoozedUntil ?: 0L) > now
    val priority = if (snoozed) 0.0 else weight * (100 - score)
    return ContactScore(score = score, priority = priority)
}

private fun startOfDayEpochMillis(ts: Long, tz: TimeZone = TimeZone.currentSystemDefault()): Long {
    val instant = Instant.fromEpochMilliseconds(ts)
    val localDate = instant.toLocalDateTime(tz).date
    return localDate.atStartOfDayIn(tz).toEpochMilliseconds()
}

private fun dayDiff(ts: Long, now: Long, tz: TimeZone = TimeZone.currentSystemDefault()): Int {
    val startTs = startOfDayEpochMillis(ts, tz)
    val startNow = startOfDayEpochMillis(now, tz)
    return round((startTs - startNow).toDouble() / DAY).toInt()
}

private fun birthdayEpochMillis(year: Int, month: Int, day: Int, tz: TimeZone): Long {
    val maxDays = when (month) {
        1, 3, 5, 7, 8, 10, 12 -> 31
        4, 6, 9, 11 -> 30
        2 -> if ((year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)) 29 else 28
        else -> 31
    }
    val clampedDay = day.coerceIn(1, maxDays)
    val localDate = LocalDate(year, month, clampedDay)
    return localDate.atStartOfDayIn(tz).toEpochMilliseconds()
}

data class NextBirthdayResult(val date: Long, val inDays: Int, val turns: Int?)

fun nextBirthday(b: Birthday, now: Long = currentTimeMillis()): NextBirthdayResult {
    val tz = TimeZone.currentSystemDefault()
    val todayDate = Instant.fromEpochMilliseconds(now).toLocalDateTime(tz).date
    val todayStartMillis = todayDate.atStartOfDayIn(tz).toEpochMilliseconds()

    var year = todayDate.year
    var dateMillis = birthdayEpochMillis(year, b.month, b.day, tz)
    if (dateMillis < todayStartMillis) {
        year += 1
        dateMillis = birthdayEpochMillis(year, b.month, b.day, tz)
    }
    val inDays = round((dateMillis - todayStartMillis).toDouble() / DAY).toInt()
    val turns = b.year?.let { year - it }
    return NextBirthdayResult(date = dateMillis, inDays = inDays, turns = turns)
}

data class RecentBirthdayResult(val date: Long, val daysAgo: Int, val turned: Int?)

fun recentBirthday(b: Birthday, now: Long = currentTimeMillis()): RecentBirthdayResult? {
    val tz = TimeZone.currentSystemDefault()
    val todayDate = Instant.fromEpochMilliseconds(now).toLocalDateTime(tz).date
    val todayStartMillis = todayDate.atStartOfDayIn(tz).toEpochMilliseconds()

    var year = todayDate.year
    var dateMillis = birthdayEpochMillis(year, b.month, b.day, tz)
    if (dateMillis >= todayStartMillis) {
        year -= 1
        dateMillis = birthdayEpochMillis(year, b.month, b.day, tz)
    }
    val daysAgo = round((todayStartMillis - dateMillis).toDouble() / DAY).toInt()
    if (daysAgo !in 1..7) return null
    val turned = b.year?.let { year - it }
    return RecentBirthdayResult(date = dateMillis, daysAgo = daysAgo, turned = turned)
}

fun formatBirthday(b: Birthday): String {
    val monthName = MONTHS.getOrElse(b.month - 1) { "" }
    val base = "${b.day} $monthName"
    return if (b.year != null) "$base ${b.year}" else base
}

private fun plural(n: Int, unit: String): String = "$n $unit${if (n == 1) "" else "s"}"

fun relativeDays(ts: Long, now: Long = currentTimeMillis()): String {
    val d = dayDiff(ts, now)
    if (d == 0) return "today"
    if (d == -1) return "yesterday"
    if (d == 1) return "tomorrow"
    val abs = abs(d)
    val text = when {
        abs < 14 -> plural(abs, "day")
        abs < 45 -> plural(abs / 7, "week")
        abs < 365 -> plural(max(2, round(abs / 30.4).toInt()), "month")
        else -> plural(max(1, round(abs / 365.0).toInt()), "year")
    }
    return if (d < 0) "$text ago" else "in $text"
}

fun firstName(name: String): String = name.trim().split(Regex("\\s+")).firstOrNull().orEmpty()

data class FunFact(val label: String, val value: String)

fun funFact(people: List<Person>): FunFact? {
    val counts = mutableMapOf<String, Pair<String, Int>>()
    for (p in people) {
        val name = firstName(p.name)
        if (name.isEmpty()) continue
        val key = name.lowercase()
        val current = counts[key]
        if (current != null) {
            counts[key] = Pair(current.first, current.second + 1)
        } else {
            counts[key] = Pair(name, 1)
        }
    }
    var top: Pair<String, Int>? = null
    for (c in counts.values) {
        if (c.second >= 2 && (top == null || c.second > top.second)) {
            top = c
        }
    }
    if (top != null) {
        return FunFact("Most common name", "${top.first} (${top.second} people)")
    }

    var bestPerson: Person? = null
    var bestInDays = Int.MAX_VALUE
    for (p in people) {
        val b = p.birthday ?: continue
        val next = nextBirthday(b)
        if (next.inDays < bestInDays) {
            bestInDays = next.inDays
            bestPerson = p
        }
    }
    if (bestPerson == null) return null
    val whenText = when (bestInDays) {
        0 -> "today"
        1 -> "tomorrow"
        else -> "in $bestInDays days"
    }
    return FunFact("Next birthday", "${firstName(bestPerson.name)} $whenText")
}

data class ParsedQuickAdd(val name: String, val note: String)

fun parseQuickAdd(input: String): ParsedQuickAdd {
    val text = input.trim()
    var cut = -1
    var len = 1
    val separators = listOf(",", ". ", " - ", " – ", ":")
    for (sep in separators) {
        val i = text.indexOf(sep)
        if (i > 0 && (cut == -1 || i < cut)) {
            cut = i
            len = sep.length
        }
    }
    if (cut == -1) {
        return ParsedQuickAdd(name = text.removeSuffix("."), note = "")
    }
    return ParsedQuickAdd(
        name = text.substring(0, cut).trim(),
        note = text.substring(cut + len).trim()
    )
}

enum class SuggestionReason { NEW, LONG }

data class Suggestion(
    val person: Person,
    val reason: SuggestionReason,
    val last: Long?,
)

private const val SUGGEST_NEW_MAX_AGE = 60 * DAY
private const val SUGGEST_LONG_FALLBACK = 90 * DAY
private const val SUGGEST_LONG_MIN = 45 * DAY

private fun median(xs: List<Long>): Double {
    val s = xs.sorted()
    val m = s.size / 2
    return if (s.size % 2 == 1) s[m].toDouble() else (s[m - 1] + s[m]) / 2.0
}

fun keepInTouchSuggestions(
    people: List<Person>,
    byPerson: Map<String, List<Entry>>,
    now: Long = currentTimeMillis(),
): List<Suggestion> {
    val fresh = mutableListOf<Suggestion>()
    data class StaleItem(val suggestion: Suggestion, val silence: Long)
    val stale = mutableListOf<StaleItem>()

    for (p in people) {
        if (p.intervalDays != null || p.dismissedAt != null) continue
        val interactions = (byPerson[p.id] ?: emptyList()).filter { !it.kind.isNote }
        val last = lastContact(p, interactions)
        val age = now - p.createdAt
        if (last == null) {
            if (age in DAY..SUGGEST_NEW_MAX_AGE) {
                fresh.add(Suggestion(person = p, reason = SuggestionReason.NEW, last = null))
            }
            continue
        }
        val times = interactions.map { it.at }.sorted()
        val gaps = if (times.size >= 2) {
            (1 until times.size).map { times[it] - times[it - 1] }
        } else emptyList()
        val threshold = if (gaps.size >= 2) {
            maxOf(SUGGEST_LONG_MIN.toDouble(), 2.0 * median(gaps)).toLong()
        } else {
            SUGGEST_LONG_FALLBACK
        }
        val silence = now - last
        if (silence > threshold) {
            stale.add(StaleItem(Suggestion(person = p, reason = SuggestionReason.LONG, last = last), silence))
        }
    }
    fresh.sortByDescending { it.person.createdAt }
    stale.sortByDescending { it.silence }
    return fresh + stale.map { it.suggestion }
}

enum class EventLimitStatus { OK, GRACE_WARNING, HARD_LIMIT }

fun canAddPerson(peopleCount: Int, isUnlimited: Boolean, countToAdd: Int = 1): Boolean =
    isUnlimited || (peopleCount + countToAdd <= FREE_CONNECTIONS_LIMIT)

fun canCreateSphere(spheresCount: Int, isUnlimited: Boolean): Boolean =
    isUnlimited || spheresCount < FREE_SPHERES_LIMIT

fun wasEventCreatedBeforeLimit(event: SocialEvent, people: List<Person>): Boolean =
    event.createdBeforeLimit ?: (people.count { it.createdAt < event.createdAt } < FREE_CONNECTIONS_LIMIT)

fun eventGraceRemainingMs(
    event: SocialEvent,
    people: List<Person>,
    now: Long = currentTimeMillis(),
): Long {
    if (!wasEventCreatedBeforeLimit(event, people)) return 0L
    val elapsed = (now - event.createdAt).coerceAtLeast(0L)
    return (EVENT_GRACE_DURATION_MS - elapsed).coerceAtLeast(0L)
}

fun isEventInGracePeriod(
    event: SocialEvent,
    people: List<Person>,
    now: Long = currentTimeMillis(),
): Boolean = eventGraceRemainingMs(event, people, now) > 0L

fun evaluateEventLimitStatus(
    event: SocialEvent,
    people: List<Person>,
    isUnlimited: Boolean,
    now: Long = currentTimeMillis(),
): EventLimitStatus {
    if (isUnlimited || people.size < FREE_CONNECTIONS_LIMIT) return EventLimitStatus.OK
    return if (isEventInGracePeriod(event, people, now)) {
        EventLimitStatus.GRACE_WARNING
    } else {
        EventLimitStatus.HARD_LIMIT
    }
}

fun formatGraceRemaining(remainingMs: Long): String {
    if (remainingMs <= 0L) return "0m left"
    val mins = ceil(remainingMs.toDouble() / 60_000.0).toInt()
    return if (mins < 60) {
        "${mins}m left"
    } else {
        "${ceil(remainingMs.toDouble() / 3_600_000.0).toInt().coerceAtMost(24)}h left"
    }
}
