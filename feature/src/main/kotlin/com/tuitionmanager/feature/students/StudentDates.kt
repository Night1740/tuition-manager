package com.tuitionmanager.feature.students

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val studentDateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

fun formatStudentDate(date: LocalDate): String = date.format(studentDateFormatter)

/**
 * DatePicker uses UTC start-of-day millis. The [LocalDate] itself comes from the teacher's
 * calendar, not from converting "now" in UTC inside the screen.
 */
fun localDateToUtcMillis(date: LocalDate): Long =
    date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

fun utcMillisToLocalDate(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
