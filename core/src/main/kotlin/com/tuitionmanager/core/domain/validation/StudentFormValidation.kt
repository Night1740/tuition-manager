package com.tuitionmanager.core.domain.validation

import com.tuitionmanager.core.domain.error.InvalidCode

enum class StudentField {
    Name,
    Code,
    GuardianName,
    GuardianPhone,
    StudentPhone,
    EitherPhone,
    Notes,
    Photo,
}

data class StudentFieldError(
    val field: StudentField,
    val code: InvalidCode,
)

data class AcceptedStudent(
    val name: String,
    val studentCode: String,
    val guardianName: String?,
    val guardianPhone: String?,
    val phone: String?,
    val notes: String?,
    val photoUri: String?,
)

sealed interface StudentFormValidation {
    data class Accepted(val student: AcceptedStudent) : StudentFormValidation

    data class Rejected(val errors: List<StudentFieldError>) : StudentFormValidation
}

/**
 * Field checks for the student form. Contact phones go through [validateStudentContacts]
 * so the "at least one phone" rule lives in one place.
 */
fun validateStudentForm(
    name: String,
    studentCode: String,
    guardianName: String?,
    guardianPhone: String?,
    studentPhone: String?,
    notes: String?,
    photoUri: String?,
): StudentFormValidation {
    val errors = mutableListOf<StudentFieldError>()
    val normalizedName = normalizeRequiredName(name)
    if (normalizedName == null) {
        val code = if (name.isBlank()) InvalidCode.BlankName else InvalidCode.NameTooLong
        errors += StudentFieldError(StudentField.Name, code)
    }
    val normalizedCode = when {
        studentCode.isBlank() -> {
            errors += StudentFieldError(StudentField.Code, InvalidCode.BlankStudentCode)
            null
        }
        else -> normalizeStudentCode(studentCode).also { code ->
            if (code == null) errors += StudentFieldError(StudentField.Code, InvalidCode.InvalidStudentCode)
        }
    }
    val contacts = when (val parsed = validateStudentContacts(guardianName, guardianPhone, studentPhone)) {
        is ContactValidation.Rejected -> {
            errors += StudentFieldError(parsed.field.toStudentField(), parsed.code)
            null
        }
        is ContactValidation.Accepted -> parsed.contacts
    }
    val parsedNotes = parseOptionalText(notes, MAX_NOTES_LENGTH)
    val noteText = when (parsedNotes) {
        OptionalText.TooLong -> {
            errors += StudentFieldError(StudentField.Notes, InvalidCode.NotesTooLong)
            null
        }
        is OptionalText.Value -> parsedNotes.text
    }
    val photo = normalizePhoto(photoUri)
    if (photo is PhotoParse.Invalid) {
        errors += StudentFieldError(StudentField.Photo, photo.code)
    }
    if (errors.isNotEmpty() || normalizedName == null || normalizedCode == null || contacts == null || photo !is PhotoParse.Value) {
        return StudentFormValidation.Rejected(errors)
    }
    return StudentFormValidation.Accepted(
        AcceptedStudent(
            name = normalizedName,
            studentCode = normalizedCode,
            guardianName = contacts.guardianName,
            guardianPhone = contacts.guardianPhone,
            phone = contacts.studentPhone,
            notes = noteText,
            photoUri = photo.uri,
        ),
    )
}

private fun ContactField.toStudentField(): StudentField = when (this) {
    ContactField.GuardianName -> StudentField.GuardianName
    ContactField.GuardianPhone -> StudentField.GuardianPhone
    ContactField.StudentPhone -> StudentField.StudentPhone
    ContactField.EitherPhone -> StudentField.EitherPhone
}

private sealed interface PhotoParse {
    data class Value(val uri: String?) : PhotoParse

    data class Invalid(val code: InvalidCode) : PhotoParse
}

private fun normalizePhoto(raw: String?): PhotoParse {
    if (raw.isNullOrBlank()) return PhotoParse.Value(null)
    val trimmed = raw.trim()
    if ('\n' in trimmed || '\r' in trimmed) return PhotoParse.Invalid(InvalidCode.InvalidPhotoUri)
    if (trimmed.length > MAX_PHOTO_URI_LENGTH) return PhotoParse.Invalid(InvalidCode.PhotoUriTooLong)
    return PhotoParse.Value(trimmed)
}
