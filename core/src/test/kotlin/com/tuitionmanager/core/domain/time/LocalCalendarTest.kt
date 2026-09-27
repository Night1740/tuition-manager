package com.tuitionmanager.core.domain.time

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalCalendarTest {
    @Test
    fun todayFollowsTheClockZoneRatherThanUtc() {
        val instant = Instant.parse("2026-09-15T20:00:00Z")
        val kolkata = Clock.fixed(instant, ZoneId.of("Asia/Kolkata"))
        val utc = Clock.fixed(instant, ZoneOffset.UTC)
        assertEquals(LocalDate.of(2026, 9, 16), ClockLocalCalendar(kolkata).today())
        assertEquals(LocalDate.of(2026, 9, 15), ClockLocalCalendar(utc).today())
    }
}
