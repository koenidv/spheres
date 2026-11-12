package de.koenidv.spheres.domain

import de.koenidv.spheres.platform.PlatformNotifications
import de.koenidv.spheres.platform.PlatformReminderItem
import de.koenidv.spheres.platform.currentTimeMillis
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

const val HORIZON = 30 * DAY

fun at9(ts: Long, tz: TimeZone = TimeZone.currentSystemDefault()): Long {
    val localDate = Instant.fromEpochMilliseconds(ts).toLocalDateTime(tz).date
    val localDateTime = LocalDateTime(localDate.year, localDate.monthNumber, localDate.dayOfMonth, 9, 0, 0, 0)
    return localDateTime.toInstant(tz).toEpochMilliseconds()
}

fun computeSchedule(
    people: List<Person>,
    entries: List<Entry>,
    now: Long = currentTimeMillis(),
): List<PlatformReminderItem> {
    val byPerson = entriesByPerson(entries)
    val items = mutableListOf<PlatformReminderItem>()

    val priority = people.associate { p ->
        p.id to (contactScore(p, byPerson[p.id], now)?.priority ?: 0.0)
    }
    val ranked = people.sortedByDescending { priority[it.id] ?: 0.0 }

    for (p in ranked) {
        val name = firstName(p.name)
        val info = dueInfo(p, byPerson[p.id], now)
        val whenAt = if (info != null) {
            if (info.dueAt > now) at9(info.dueAt) else info.dueAt
        } else 0L

        if (info != null && whenAt > now - 2 * DAY && whenAt < now + HORIZON) {
            items.add(
                PlatformReminderItem(
                    id = "due:${p.id}:${info.dueAt}",
                    title = "Time to catch up with $name",
                    body = "Send a message, call, or plan a meetup.",
                    at = whenAt,
                    personId = p.id,
                )
            )
        }
        if (p.followUpAt != null && p.followUpAt > now - 2 * DAY && p.followUpAt < now + HORIZON) {
            items.add(
                PlatformReminderItem(
                    id = "followup:${p.id}:${p.followUpAt}",
                    title = "Follow up with $name",
                    body = "You asked to be reminded.",
                    at = p.followUpAt,
                    personId = p.id,
                )
            )
        }
        if (p.birthday != null) {
            val b = nextBirthday(p.birthday, now)
            val year = Instant.fromEpochMilliseconds(b.date).toLocalDateTime(TimeZone.currentSystemDefault()).year
            val bAt = at9(b.date)
            if (b.inDays <= 30) {
                items.add(
                    PlatformReminderItem(
                        id = "bday:${p.id}:$year",
                        title = "${name}'s birthday is ${if (b.inDays == 0) "today" else "coming up"}",
                        body = if (b.turns != null) "Turning ${b.turns}." else "Send your wishes.",
                        at = bAt,
                        personId = p.id,
                    )
                )
            }
            val gifts = p.gifts.filter { !it.done }
            val gAt = at9(b.date - 7 * DAY)
            if (gifts.isNotEmpty() && gAt > now - DAY && gAt < now + HORIZON) {
                items.add(
                    PlatformReminderItem(
                        id = "gift:${p.id}:$year",
                        title = "${name}'s birthday is in a week",
                        body = "Gift ideas: ${gifts.joinToString(", ") { it.text }}",
                        at = gAt,
                        personId = p.id,
                    )
                )
            }
        }
    }
    return items.sortedBy { it.at }
}

fun syncReminderSchedule(
    people: List<Person>,
    entries: List<Entry>,
    now: Long = currentTimeMillis(),
) {
    val schedule = computeSchedule(people, entries, now)
    val byPerson = entriesByPerson(entries)
    val overdue = people.count { p ->
        dueInfo(p, byPerson[p.id], now)?.status == DueStatus.OVERDUE
    }
    PlatformNotifications.syncSchedule(schedule, overdue)
}
