package com.tuitionmanager.core.domain.model

import java.time.Instant
import java.time.LocalDate

data class Institute(
    val id: String,
    val name: String,
    val ownerName: String,
    val phone: String?,
    val address: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class NewInstitute(
    val name: String,
    val ownerName: String,
    val phone: String?,
    val address: String?,
)

data class Student(
    val id: String,
    val instituteId: String,
    val name: String,
    val studentCode: String,
    val guardianName: String,
    val guardianPhone: String,
    val phone: String?,
    val photoUri: String?,
    val admissionDate: LocalDate,
    val notes: String?,
    val archivedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    val isArchived: Boolean get() = archivedAt != null
}

data class NewStudent(
    val instituteId: String,
    val name: String,
    val studentCode: String,
    val guardianName: String,
    val guardianPhone: String,
    val phone: String?,
    val photoUri: String?,
    val admissionDate: LocalDate,
    val notes: String?,
)

data class Batch(
    val id: String,
    val instituteId: String,
    val name: String,
    val subject: String,
    val daysOfWeek: Set<java.time.DayOfWeek>,
    val startMinute: Int,
    val endMinute: Int,
    val room: String?,
    val capacity: Int,
    val archivedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    val isArchived: Boolean get() = archivedAt != null
}

data class NewBatch(
    val instituteId: String,
    val name: String,
    val subject: String,
    val daysOfWeek: Set<java.time.DayOfWeek>,
    val startMinute: Int,
    val endMinute: Int,
    val room: String?,
    val capacity: Int,
)

/**
 * One stretch of membership in a batch.
 *
 * [endedOn] is exclusive: the student is in the batch on dates `startedOn <= date < endedOn`.
 * A null [endedOn] means the assignment is still open. Moving from batch A to batch B on date D
 * sets the old row's [endedOn] to D and inserts a new open row starting on D. A student may hold
 * several open assignments at once (different batches). Two open rows for the same student and
 * batch are rejected.
 */
data class StudentBatch(
    val id: String,
    val studentId: String,
    val batchId: String,
    val startedOn: LocalDate,
    val endedOn: LocalDate?,
    val createdAt: Instant,
) {
    val isActive: Boolean get() = endedOn == null
}

data class Enrollment(
    val assignment: StudentBatch,
    val student: Student,
)
