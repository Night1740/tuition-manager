package com.tuitionmanager.feature.batches

import java.time.DayOfWeek
import java.util.Locale

internal fun dayLabel(day: DayOfWeek): String = when (day) {
    DayOfWeek.MONDAY -> "Mon"
    DayOfWeek.TUESDAY -> "Tue"
    DayOfWeek.WEDNESDAY -> "Wed"
    DayOfWeek.THURSDAY -> "Thu"
    DayOfWeek.FRIDAY -> "Fri"
    DayOfWeek.SATURDAY -> "Sat"
    DayOfWeek.SUNDAY -> "Sun"
}

internal fun formatClock(minuteOfDay: Int): String {
    val hour24 = minuteOfDay / 60
    val minute = minuteOfDay % 60
    val suffix = if (hour24 < 12) "AM" else "PM"
    val hour12 = when (val hour = hour24 % 12) {
        0 -> 12
        else -> hour
    }
    return "%d:%02d %s".format(Locale.ENGLISH, hour12, minute, suffix)
}

internal fun formatSchedule(days: Set<DayOfWeek>, startMinute: Int, endMinute: Int): String {
    val labels = DayOfWeek.entries.filter { it in days }.joinToString(", ") { dayLabel(it) }
    return "$labels · ${formatClock(startMinute)} - ${formatClock(endMinute)}"
}

internal fun formatOccupancy(studentCount: Int, capacity: Int): String = "$studentCount / $capacity"
