package com.tuitionmanager.core.domain.time

import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** The teacher's local calendar date. Instants stay on [Clock.instant], which is a UTC moment. */
fun interface LocalCalendar {
    fun today(): LocalDate
}

class ClockLocalCalendar @Inject constructor(
    private val clock: Clock,
) : LocalCalendar {
    override fun today(): LocalDate = LocalDate.now(clock)
}
