package com.tuitionmanager.core.domain.validation

import com.tuitionmanager.core.domain.error.InvalidCode
import java.time.DayOfWeek

enum class BatchField {
    Name,
    Subject,
    Days,
    Time,
    Room,
    Capacity,
}

data class BatchFieldError(
    val field: BatchField,
    val code: InvalidCode,
)

data class AcceptedBatch(
    val name: String,
    val subject: String,
    val daysOfWeek: Set<DayOfWeek>,
    val startMinute: Int,
    val endMinute: Int,
    val room: String?,
    val capacity: Int,
)

sealed interface BatchFormValidation {
    data class Accepted(val batch: AcceptedBatch) : BatchFormValidation

    data class Rejected(val errors: List<BatchFieldError>) : BatchFormValidation
}

/**
 * Field checks for the batch form. [capacity] is null when the teacher left it blank
 * or typed a value that is not an integer. Capacity is required: the column is not nullable.
 */
fun validateBatchForm(
    name: String,
    subject: String,
    days: Set<DayOfWeek>,
    startMinute: Int,
    endMinute: Int,
    room: String?,
    capacity: Int?,
): BatchFormValidation {
    val errors = mutableListOf<BatchFieldError>()
    val normalizedName = when (val parsed = requiredText(name, MAX_NAME_LENGTH)) {
        is RequiredText.Blank -> {
            errors += BatchFieldError(BatchField.Name, InvalidCode.BlankBatchName)
            null
        }
        RequiredText.TooLong -> {
            errors += BatchFieldError(BatchField.Name, InvalidCode.NameTooLong)
            null
        }
        is RequiredText.Value -> parsed.text
    }
    val normalizedSubject = when (val parsed = requiredText(subject, MAX_SUBJECT_LENGTH)) {
        is RequiredText.Blank -> {
            errors += BatchFieldError(BatchField.Subject, InvalidCode.BlankSubject)
            null
        }
        RequiredText.TooLong -> {
            errors += BatchFieldError(BatchField.Subject, InvalidCode.SubjectTooLong)
            null
        }
        is RequiredText.Value -> parsed.text
    }
    if (days.isEmpty()) {
        errors += BatchFieldError(BatchField.Days, InvalidCode.NoDaysSelected)
    }
    if (!isValidClockMinutes(startMinute, endMinute)) {
        errors += BatchFieldError(BatchField.Time, InvalidCode.EndNotAfterStart)
    }
    val parsedRoom = parseOptionalText(room, MAX_ROOM_LENGTH)
    val roomText = when (parsedRoom) {
        OptionalText.TooLong -> {
            errors += BatchFieldError(BatchField.Room, InvalidCode.RoomTooLong)
            null
        }
        is OptionalText.Value -> parsedRoom.text
    }
    if (capacity == null || capacity !in MIN_BATCH_CAPACITY..MAX_BATCH_CAPACITY) {
        errors += BatchFieldError(BatchField.Capacity, InvalidCode.InvalidCapacity)
    }
    if (
        errors.isNotEmpty() ||
        normalizedName == null ||
        normalizedSubject == null ||
        days.isEmpty() ||
        capacity == null ||
        parsedRoom is OptionalText.TooLong
    ) {
        return BatchFormValidation.Rejected(errors)
    }
    return BatchFormValidation.Accepted(
        AcceptedBatch(
            name = normalizedName,
            subject = normalizedSubject,
            daysOfWeek = days.toSet(),
            startMinute = startMinute,
            endMinute = endMinute,
            room = roomText,
            capacity = capacity,
        ),
    )
}

private sealed interface RequiredText {
    data class Value(val text: String) : RequiredText

    data object Blank : RequiredText

    data object TooLong : RequiredText
}

private fun requiredText(raw: String, maxLength: Int): RequiredText {
    val trimmed = raw.trim().replace(Regex("\\s+"), " ")
    if (trimmed.isEmpty()) return RequiredText.Blank
    if (trimmed.length > maxLength) return RequiredText.TooLong
    return RequiredText.Value(trimmed)
}
