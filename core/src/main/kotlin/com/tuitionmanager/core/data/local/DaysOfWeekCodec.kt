package com.tuitionmanager.core.data.local

import java.time.DayOfWeek

internal object DaysOfWeekCodec {
    private val tokens = DayOfWeek.entries.associateBy { token(it) }

    fun encode(days: Set<DayOfWeek>): String =
        DayOfWeek.entries.filter { it in days }.joinToString(",") { token(it) }

    fun decode(stored: String): Set<DayOfWeek> {
        if (stored.isEmpty()) return emptySet()
        return stored.split(",").map { token ->
            tokens[token] ?: error("Unknown day token")
        }.toSet()
    }

    private fun token(day: DayOfWeek): String = day.name.take(3)
}
