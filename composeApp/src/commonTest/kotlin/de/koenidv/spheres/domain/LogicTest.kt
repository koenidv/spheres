package de.koenidv.spheres.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LogicTest {

    @Test
    fun testContactScoreQualityDecay() {
        val now = 1_700_000_000_000L
        val intervalDays = 30

        val person = Person(
            id = "p1",
            name = "Jordan",
            intervalDays = intervalDays,
            createdAt = now - 60 * DAY,
        )

        // 1. Untracked person returns null
        val untracked = person.copy(intervalDays = null)
        assertNull(contactScore(untracked, emptyList(), now))

        // 2. Bad quality latest interaction starts at 50
        val badEntry = Entry(
            id = "e_bad",
            personId = "p1",
            kind = EntryKind.MEET,
            quality = Quality.BAD,
            at = now,
            createdAt = now,
        )
        val badScoreNow = contactScore(person, listOf(badEntry), now)
        assertNotNull(badScoreNow)
        assertEquals(50, badScoreNow.score)

        // Half interval elapsed (15 days): loses 25 points -> score 25
        val badScore15d = contactScore(person, listOf(badEntry), now + 15 * DAY)
        assertNotNull(badScore15d)
        assertEquals(25, badScore15d.score)

        // 3. Regular quality latest interaction starts at 100, loses 100 over 2x interval (60 days)
        val regularEntry = Entry(
            id = "e_reg",
            personId = "p1",
            kind = EntryKind.CALL,
            quality = Quality.REGULAR,
            at = now,
            createdAt = now,
        )
        val regScoreNow = contactScore(person, listOf(regularEntry), now)
        assertNotNull(regScoreNow)
        assertEquals(100, regScoreNow.score)

        val regScore30d = contactScore(person, listOf(regularEntry), now + 30 * DAY)
        assertNotNull(regScore30d)
        assertEquals(50, regScore30d.score)

        val regScore60d = contactScore(person, listOf(regularEntry), now + 60 * DAY)
        assertNotNull(regScore60d)
        assertEquals(0, regScore60d.score)

        // 4. Good quality stretches interval by 1.25x (loses 100 over 75 days)
        val goodEntry = Entry(
            id = "e_good",
            personId = "p1",
            kind = EntryKind.CHECKIN,
            quality = Quality.GOOD,
            at = now,
            createdAt = now,
        )
        val goodScore30d = contactScore(person, listOf(goodEntry), now + 30 * DAY)
        assertNotNull(goodScore30d)
        // 100 - (100 * 30) / (2 * 30 * 1.25) = 100 - 40 = 60
        assertEquals(60, goodScore30d.score)

        // 5. Amazing quality stretches interval by 1.5x (loses 100 over 90 days)
        val amazingEntry = Entry(
            id = "e_amazing",
            personId = "p1",
            kind = EntryKind.MEET,
            quality = Quality.AMAZING,
            at = now,
            createdAt = now,
        )
        val amazingScore30d = contactScore(person, listOf(amazingEntry), now + 30 * DAY)
        assertNotNull(amazingScore30d)
        // 100 - (100 * 30) / (2 * 30 * 1.5) = 100 - 33.33 -> 67
        assertEquals(67, amazingScore30d.score)

        // 6. Snoozed connections have 0 priority
        val snoozedPerson = person.copy(snoozedUntil = now + 5 * DAY)
        val snoozedScore = contactScore(snoozedPerson, listOf(regularEntry), now)
        assertNotNull(snoozedScore)
        assertEquals(0.0, snoozedScore.priority)
    }

    @Test
    fun testDueInfo() {
        val now = 1_700_000_000_000L
        val intervalDays = 14

        val person = Person(
            id = "p1",
            name = "Sam",
            intervalDays = intervalDays,
            createdAt = now - 20 * DAY,
        )

        // Contacted today -> OK
        val todayEntry = Entry(
            id = "e1",
            personId = "p1",
            kind = EntryKind.MESSAGE,
            at = now,
            createdAt = now,
        )
        val infoToday = dueInfo(person, listOf(todayEntry), now)
        assertNotNull(infoToday)
        assertEquals(DueStatus.OK, infoToday.status)
        assertEquals(14, infoToday.daysLeft)

        // Contacted 12 days ago (2 days left) -> SOON
        val soonEntry = Entry(
            id = "e2",
            personId = "p1",
            kind = EntryKind.MESSAGE,
            at = now - 12 * DAY,
            createdAt = now - 12 * DAY,
        )
        val infoSoon = dueInfo(person, listOf(soonEntry), now)
        assertNotNull(infoSoon)
        assertEquals(DueStatus.SOON, infoSoon.status)
        assertEquals(2, infoSoon.daysLeft)

        // Contacted 15 days ago -> OVERDUE
        val overdueEntry = Entry(
            id = "e3",
            personId = "p1",
            kind = EntryKind.MESSAGE,
            at = now - 15 * DAY,
            createdAt = now - 15 * DAY,
        )
        val infoOverdue = dueInfo(person, listOf(overdueEntry), now)
        assertNotNull(infoOverdue)
        assertEquals(DueStatus.OVERDUE, infoOverdue.status)
    }

    @Test
    fun testKeepInTouchSuggestions() {
        val now = 1_700_000_000_000L

        val pTracked = Person(id = "p_tr", name = "Tracked", intervalDays = 7, createdAt = now - 10 * DAY)
        val pDismissed = Person(id = "p_dis", name = "Dismissed", intervalDays = null, dismissedAt = now - 2 * DAY, createdAt = now - 10 * DAY)
        val pNew = Person(id = "p_new", name = "New Friend", intervalDays = null, createdAt = now - 5 * DAY)
        val pLong = Person(id = "p_long", name = "Old Pal", intervalDays = null, createdAt = now - 200 * DAY)

        val longEntry = Entry(
            id = "e_long",
            personId = "p_long",
            kind = EntryKind.MEET,
            at = now - 100 * DAY,
            createdAt = now - 100 * DAY,
        )

        val byPerson = mapOf("p_long" to listOf(longEntry))
        val suggestions = keepInTouchSuggestions(listOf(pTracked, pDismissed, pNew, pLong), byPerson, now)

        assertEquals(2, suggestions.size)
        assertEquals("p_new", suggestions[0].person.id)
        assertEquals(SuggestionReason.NEW, suggestions[0].reason)
        assertEquals("p_long", suggestions[1].person.id)
        assertEquals(SuggestionReason.LONG, suggestions[1].reason)
    }

    @Test
    fun testParseQuickAdd() {
        val r1 = parseQuickAdd("Alex Smith, met at tech summit")
        assertEquals("Alex Smith", r1.name)
        assertEquals("met at tech summit", r1.note)

        val r2 = parseQuickAdd("Sarah - designer friend")
        assertEquals("Sarah", r2.name)
        assertEquals("designer friend", r2.note)

        val r3 = parseQuickAdd("David Miller: colleague")
        assertEquals("David Miller", r3.name)
        assertEquals("colleague", r3.note)

        val r4 = parseQuickAdd("Emma Watson.")
        assertEquals("Emma Watson", r4.name)
        assertEquals("", r4.note)
    }

    @Test
    fun testBirthdays() {
        // May 10, 2026 UTC timestamp: 1778371200000L
        val now = 1_778_371_200_000L

        // Upcoming birthday in 5 days (May 15)
        val upcoming = Birthday(month = 5, day = 15, year = 1990)
        val next = nextBirthday(upcoming, now)
        assertEquals(5, next.inDays)
        assertEquals(36, next.turns)

        // Recent birthday 2 days ago (May 8)
        val past = Birthday(month = 5, day = 8, year = 1990)
        val recent = recentBirthday(past, now)
        assertNotNull(recent)
        assertEquals(2, recent.daysAgo)
        assertEquals(36, recent.turned)

        // Birthday 20 days ago -> recentBirthday is null (exceeds 7 days)
        val oldPast = Birthday(month = 4, day = 20, year = 1990)
        assertNull(recentBirthday(oldPast, now))

        // Formatting
        assertEquals("15 May 1990", formatBirthday(upcoming))
    }

    @Test
    fun testHalfYearInterval() {
        val info = intervalInfo(180)
        assertEquals(180, info.days)
        assertEquals("Half-yearly", info.label)
        assertTrue(INTERVALS.any { it.days == 180 })
    }

    @Test
    fun testCountdownWithoutInteractions() {
        val createdAt = 1_700_000_000_000L
        val person = Person(
            id = "p_new",
            name = "Alex",
            intervalDays = 14,
            createdAt = createdAt,
        )

        val day0Due = dueInfo(person, emptyList(), createdAt)
        assertNotNull(day0Due)
        assertEquals(14, day0Due.daysLeft)
        assertEquals(DueStatus.OK, day0Due.status)

        val day10Due = dueInfo(person, emptyList(), createdAt + 10 * DAY)
        assertNotNull(day10Due)
        assertEquals(4, day10Due.daysLeft)
        assertEquals(DueStatus.OK, day10Due.status)

        val day15Due = dueInfo(person, emptyList(), createdAt + 15 * DAY)
        assertNotNull(day15Due)
        assertEquals(-1, day15Due.daysLeft)
        assertEquals(DueStatus.OVERDUE, day15Due.status)

        val day14Score = contactScore(person, emptyList(), createdAt + 14 * DAY)
        assertNotNull(day14Score)
        assertEquals(50, day14Score.score)
    }

    @Test
    fun testFreeTierConnectionAndSphereLimits() {
        assertTrue(canAddPerson(peopleCount = FREE_CONNECTIONS_LIMIT - 1, isUnlimited = false))
        assertFalse(canAddPerson(peopleCount = FREE_CONNECTIONS_LIMIT, isUnlimited = false))
        assertFalse(canAddPerson(peopleCount = FREE_CONNECTIONS_LIMIT - 1, isUnlimited = false, countToAdd = 2))
        assertTrue(canAddPerson(peopleCount = FREE_CONNECTIONS_LIMIT - 2, isUnlimited = false, countToAdd = 2))
        assertTrue(canAddPerson(peopleCount = 100, isUnlimited = true, countToAdd = 50))

        assertTrue(canCreateSphere(spheresCount = FREE_SPHERES_LIMIT - 1, isUnlimited = false))
        assertFalse(canCreateSphere(spheresCount = FREE_SPHERES_LIMIT, isUnlimited = false))
        assertTrue(canCreateSphere(spheresCount = 20, isUnlimited = true))
    }

    @Test
    fun testEvent24HourGracePeriodWhenCreatedBeforeLimit() {
        val createdAt = 1_700_000_000_000L
        val event = SocialEvent(
            id = "ev1",
            name = "Summer Dinner",
            date = createdAt,
            color = "#9d7ce7",
            createdAt = createdAt,
            createdBeforeLimit = true,
        )
        val peopleUnder = (1 until FREE_CONNECTIONS_LIMIT).map { idx ->
            Person(id = "p$idx", name = "Person $idx", createdAt = createdAt - 1_000L)
        }
        val peopleAtLimit = peopleUnder + Person(
            id = "p_last",
            name = "Person $FREE_CONNECTIONS_LIMIT",
            createdAt = createdAt + 60_000L,
        )

        assertEquals(
            EventLimitStatus.OK,
            evaluateEventLimitStatus(event, peopleUnder, isUnlimited = false, now = createdAt + 48 * 3_600_000L)
        )

        val oneHourLater = createdAt + 3_600_000L
        assertEquals(
            EventLimitStatus.GRACE_WARNING,
            evaluateEventLimitStatus(event, peopleAtLimit, isUnlimited = false, now = oneHourLater)
        )
        assertTrue(isEventInGracePeriod(event, peopleAtLimit, now = oneHourLater))
        assertEquals(23 * 3_600_000L, eventGraceRemainingMs(event, peopleAtLimit, now = oneHourLater))
        assertEquals("23h left", formatGraceRemaining(eventGraceRemainingMs(event, peopleAtLimit, now = oneHourLater)))
        assertEquals("30m left", formatGraceRemaining(30 * 60_000L))

        val after24h = createdAt + DAY
        assertEquals(
            EventLimitStatus.HARD_LIMIT,
            evaluateEventLimitStatus(event, peopleAtLimit, isUnlimited = false, now = after24h)
        )
        assertFalse(isEventInGracePeriod(event, peopleAtLimit, now = after24h))
        assertEquals(0L, eventGraceRemainingMs(event, peopleAtLimit, now = after24h))
        assertEquals("0m left", formatGraceRemaining(0L))
    }

    @Test
    fun testEventCreatedAfterLimitHasImmediateHardLimit() {
        val createdAt = 1_700_000_000_000L
        val peopleAtLimit = (1..FREE_CONNECTIONS_LIMIT).map { idx ->
            Person(id = "p$idx", name = "Person $idx", createdAt = createdAt - 10_000L)
        }
        val explicitAfter = SocialEvent(
            id = "ev_after",
            name = "Late Mixer",
            date = createdAt,
            color = "#eb78b0",
            createdAt = createdAt,
            createdBeforeLimit = false,
        )
        assertEquals(
            EventLimitStatus.HARD_LIMIT,
            evaluateEventLimitStatus(explicitAfter, peopleAtLimit, isUnlimited = false, now = createdAt + 60_000L)
        )
        assertEquals(0L, eventGraceRemainingMs(explicitAfter, peopleAtLimit, now = createdAt + 60_000L))

        val legacyAfter = explicitAfter.copy(createdBeforeLimit = null)
        assertFalse(wasEventCreatedBeforeLimit(legacyAfter, peopleAtLimit))
        assertEquals(
            EventLimitStatus.HARD_LIMIT,
            evaluateEventLimitStatus(legacyAfter, peopleAtLimit, isUnlimited = false, now = createdAt + 60_000L)
        )
    }

    @Test
    fun testUnlimitedUnlocksEventsAfterGracePeriod() {
        val createdAt = 1_700_000_000_000L
        val event = SocialEvent(
            id = "ev_unlim",
            name = "Festival",
            date = createdAt,
            color = "#5b9cf0",
            createdAt = createdAt,
            createdBeforeLimit = false,
        )
        val people = (1..FREE_CONNECTIONS_LIMIT + 5).map { idx ->
            Person(id = "p$idx", name = "Person $idx", createdAt = createdAt - 1_000L)
        }
        assertEquals(
            EventLimitStatus.OK,
            evaluateEventLimitStatus(event, people, isUnlimited = true, now = createdAt + 7 * DAY)
        )
    }
}
