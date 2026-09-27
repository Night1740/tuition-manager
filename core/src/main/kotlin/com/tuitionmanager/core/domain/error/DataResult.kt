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
}

enum class InvalidCode {
    BlankName,
    BlankOwnerName,
    BlankStudentCode,
    InvalidStudentCode,
    BlankGuardianName,
    InvalidPhone,
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
    BlankBatchName,
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
