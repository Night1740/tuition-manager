package com.tuitionmanager.core.domain.validation

import com.tuitionmanager.core.domain.error.InvalidCode

internal const val MAX_NAME_LENGTH = 120
internal const val MAX_STUDENT_CODE_LENGTH = 32
internal const val MAX_NOTES_LENGTH = 2_000
internal const val MAX_ADDRESS_LENGTH = 500
internal const val MAX_PHOTO_URI_LENGTH = 2_048
internal const val MAX_ROOM_LENGTH = 80
internal const val MAX_SUBJECT_LENGTH = 80
internal const val MIN_BATCH_CAPACITY = 1
internal const val MAX_BATCH_CAPACITY = 2_000

internal fun normalizeRequiredName(raw: String, maxLength: Int = MAX_NAME_LENGTH): String? {
    val trimmed = raw.trim().replace(Regex("\\s+"), " ")
    if (trimmed.isEmpty() || trimmed.length > maxLength) return null
    return trimmed
}

internal fun normalizeStudentCode(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty() || trimmed.length > MAX_STUDENT_CODE_LENGTH) return null
    val allowed = trimmed.all { it.isLetterOrDigit() || it == '-' || it == '/' }
    if (!allowed) return null
    return trimmed
}

internal sealed interface OptionalText {
    data class Value(val text: String?) : OptionalText

    data object TooLong : OptionalText
}

internal fun parseOptionalText(raw: String?, maxLength: Int): OptionalText {
    val trimmed = raw?.trim()?.replace(Regex("\\s+"), " ").orEmpty()
    if (trimmed.isEmpty()) return OptionalText.Value(null)
    if (trimmed.length > maxLength) return OptionalText.TooLong
    return OptionalText.Value(trimmed)
}

internal sealed interface PhoneParse {
    data class Value(val national: String?) : PhoneParse

    data object Invalid : PhoneParse
}

internal fun parsePhone(raw: String?, required: Boolean): PhoneParse {
    if (raw.isNullOrBlank()) {
        return if (required) PhoneParse.Invalid else PhoneParse.Value(null)
    }
    val national = nationalPhoneOrNull(raw) ?: return PhoneParse.Invalid
    return PhoneParse.Value(national)
}

/**
 * Accepts a 10-digit Indian national number. Optional +91 / 91 country prefix and a single
 * leading trunk 0 are stripped. Spaces, dashes, and parentheses are ignored. Landlines that
 * fit the same 10-digit national form are accepted. Short or non-numeric input is rejected.
 * Returns null when [raw] is null or blank (the field was omitted).
 */
internal fun nationalPhoneOrNull(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val compact = buildString(raw.length) {
        for (character in raw.trim()) {
            when (character) {
                ' ', '-', '(', ')' -> Unit
                else -> append(character)
            }
        }
    }
    if (compact.isEmpty()) return null
    var rest = compact
    if (rest.startsWith("+91")) {
        rest = rest.removePrefix("+91")
    } else if (rest.startsWith("91") && rest.length == 12) {
        rest = rest.drop(2)
    }
    if (rest.startsWith("0") && rest.length == 11) {
        rest = rest.drop(1)
    }
    if (rest.length != 10 || rest.any { !it.isDigit() } || rest.startsWith("0")) return null
    return rest
}

internal fun isValidClockMinutes(startMinute: Int, endMinute: Int): Boolean =
    startMinute in 0..1_439 && endMinute in 0..1_439 && endMinute > startMinute

data class InstituteDraft(
    val name: String,
    val ownerName: String,
    val phone: String?,
    val address: String?,
)

sealed interface InstituteValidation {
    data class Accepted(val draft: InstituteDraft) : InstituteValidation

    data class Rejected(val code: InvalidCode) : InstituteValidation
}

/** Shared institute-field rule used by onboarding and the institute repository. */
fun validateInstituteInput(
    name: String,
    ownerName: String,
    phone: String?,
    address: String?,
): InstituteValidation {
    val normalizedName = normalizeRequiredName(name)
        ?: return InstituteValidation.Rejected(InvalidCode.BlankName)
    val normalizedOwner = normalizeRequiredName(ownerName)
        ?: return InstituteValidation.Rejected(InvalidCode.BlankOwnerName)
    val parsedPhone = parsePhone(phone, required = false)
    if (parsedPhone is PhoneParse.Invalid) {
        return InstituteValidation.Rejected(InvalidCode.InvalidPhone)
    }
    val parsedAddress = parseOptionalText(address, MAX_ADDRESS_LENGTH)
    if (parsedAddress is OptionalText.TooLong) {
        return InstituteValidation.Rejected(InvalidCode.AddressTooLong)
    }
    return InstituteValidation.Accepted(
        InstituteDraft(
            name = normalizedName,
            ownerName = normalizedOwner,
            phone = (parsedPhone as PhoneParse.Value).national,
            address = (parsedAddress as OptionalText.Value).text,
        ),
    )
}

data class StudentContacts(
    val guardianName: String?,
    val guardianPhone: String?,
    val studentPhone: String?,
)

sealed interface ContactValidation {
    data class Accepted(val contacts: StudentContacts) : ContactValidation

    data class Rejected(val code: InvalidCode) : ContactValidation
}

/**
 * A student needs at least one contact number: guardian phone or student phone.
 * Either number may be omitted. A number that is present must be a 10-digit national number.
 * Guardian name is optional.
 */
fun validateStudentContacts(
    guardianName: String?,
    guardianPhone: String?,
    studentPhone: String?,
): ContactValidation {
    val name = when (val parsed = parseOptionalText(guardianName, MAX_NAME_LENGTH)) {
        OptionalText.TooLong ->
            return ContactValidation.Rejected(InvalidCode.NameTooLong)
        is OptionalText.Value -> parsed.text
    }
    val guardian = when (val parsed = parsePhone(guardianPhone, required = false)) {
        PhoneParse.Invalid ->
            return ContactValidation.Rejected(InvalidCode.InvalidPhone)
        is PhoneParse.Value -> parsed.national
    }
    val student = when (val parsed = parsePhone(studentPhone, required = false)) {
        PhoneParse.Invalid ->
            return ContactValidation.Rejected(InvalidCode.InvalidPhone)
        is PhoneParse.Value -> parsed.national
    }
    if (guardian == null && student == null) {
        return ContactValidation.Rejected(InvalidCode.MissingContactPhone)
    }
    return ContactValidation.Accepted(StudentContacts(name, guardian, student))
}
