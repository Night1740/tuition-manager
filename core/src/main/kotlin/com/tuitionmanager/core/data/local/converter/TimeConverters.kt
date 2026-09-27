package com.tuitionmanager.core.data.local.converter

import androidx.room3.ColumnTypeConverter
import java.time.Instant
import java.time.LocalDate

/**
 * Instants are UTC epoch millis. Date-only values are [LocalDate] epoch days.
 * Callers supply calendar dates; these converters do not apply a time zone.
 */
class TimeConverters {
    @ColumnTypeConverter
    fun instantToEpochMillis(value: Instant?): Long? = value?.toEpochMilli()

    @ColumnTypeConverter
    fun epochMillisToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @ColumnTypeConverter
    fun localDateToEpochDay(value: LocalDate?): Long? = value?.toEpochDay()

    @ColumnTypeConverter
    fun epochDayToLocalDate(value: Long?): LocalDate? = value?.let(LocalDate::ofEpochDay)
}
