package de.koenidv.spheres.domain

import de.koenidv.spheres.ui.components.monthGrid
import de.koenidv.spheres.ui.components.monthPage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DatePickerGridTest {
    @Test
    fun gridAlwaysHasSixWeeks() {
        for (month in 1..12) assertEquals(42, monthGrid(2026, month).size)
    }

    @Test
    fun novemberStartsOnSundayWithMondayFirstWeeks() {
        // 1 Nov 2026 is a Sunday → six leading blanks.
        val cells = monthGrid(2026, 11)
        for (i in 0 until 6) assertNull(cells[i])
        assertEquals(1, cells[6]?.dayOfMonth)
        assertEquals(30, cells.filterNotNull().last().dayOfMonth)
    }

    @Test
    fun leapFebruaryHas29Days() {
        assertEquals(29, monthGrid(2024, 2).filterNotNull().size)
        assertEquals(28, monthGrid(2025, 2).filterNotNull().size)
    }

    @Test
    fun monthPageIsContiguous() {
        assertEquals(0, monthPage(1900, 1))
        assertEquals(12, monthPage(1901, 1))
        assertEquals(monthPage(2026, 12) + 1, monthPage(2027, 1))
    }
}
