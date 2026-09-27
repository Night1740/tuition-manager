package com.tuitionmanager.core.domain.error

sealed interface DataResult<out T> {
    data class Success<T>(val value: T) : DataResult<T>

    data class Failure(val error: DataError) : DataResult<Nothing>
}

sealed interface DataError {
    data class Invalid(val code: InvalidCode) : DataError

    data class NotFound(val entity: EntityKind) : DataError

    data class Conflict(val code: ConflictCode) : DataError

    /** [operation] is a stable token. It never includes field values. */
    data class Storage(val operation: String) : DataError

    /**
     * Adding [adding] students would put a batch past [capacity].
     * [enrolled] is the current count of active students with an open assignment.
     * Nothing was written. A later call may allow the over-enrollment.
     */
    data class OverCapacity(
        val enrolled: Int,
        val adding: Int,
        val capacity: Int,
    ) : DataError
}

enum class InvalidCode {
    BlankName,
    BlankOwnerName,
    BlankStudentCode,
    InvalidStudentCode,
    NameTooLong,
    InvalidPhone,
    MissingContactPhone,
    InvalidCapacity,
    InvalidSchedule,
    InvalidDateRange,
    NotesTooLong,
    PhotoUriTooLong,
    InvalidPhotoUri,
    AddressTooLong,
    StudentArchived,
    BatchArchived,
    BlankSubject,
    SubjectTooLong,
    BlankBatchName,
    NoDaysSelected,
    EndNotAfterStart,
    RoomTooLong,
    CrossInstitute,
    SameBatch,
}

enum class ConflictCode {
    InstituteAlreadyExists,
    DuplicateStudentCode,
    DuplicateActiveAssignment,
    BatchFull,
    Constraint,
}

enum class EntityKind {
    Institute,
    Student,
    Batch,
    StudentBatch,
}
