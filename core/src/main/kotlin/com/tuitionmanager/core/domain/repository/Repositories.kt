package com.tuitionmanager.core.domain.repository

import com.tuitionmanager.core.domain.error.DataResult
import com.tuitionmanager.core.domain.model.Batch
import com.tuitionmanager.core.domain.model.BatchRoster
import com.tuitionmanager.core.domain.model.Enrollment
import com.tuitionmanager.core.domain.model.Institute
import com.tuitionmanager.core.domain.model.NewBatch
import com.tuitionmanager.core.domain.model.NewInstitute
import com.tuitionmanager.core.domain.model.NewStudent
import com.tuitionmanager.core.domain.model.OpenAssignment
import com.tuitionmanager.core.domain.model.Student
import com.tuitionmanager.core.domain.model.StudentBatch
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

interface InstituteRepository {
    fun observe(): Flow<DataResult<Institute?>>

    suspend fun get(): DataResult<Institute?>

    suspend fun create(draft: NewInstitute): DataResult<Institute>

    suspend fun update(institute: Institute): DataResult<Institute>
}

interface StudentRepository {
    fun observe(instituteId: String, includeArchived: Boolean): Flow<DataResult<List<Student>>>

    fun observeActive(instituteId: String, query: String = ""): Flow<DataResult<List<Student>>>

    /**
     * SQL search over name, student code, and phone digits.
     * [archivedOnly] selects one list: archived students, or active students. The two are never mixed.
     * A blank [query] returns that whole list.
     */
    fun observeList(
        instituteId: String,
        archivedOnly: Boolean,
        query: String,
    ): Flow<DataResult<List<Student>>>

    fun observeOne(id: String): Flow<DataResult<Student>>

    suspend fun suggestCode(instituteId: String): DataResult<String>

    suspend fun countActive(instituteId: String): DataResult<Int>

    /** Active students only, as a count. Does not load student rows. */
    fun observeActiveCount(instituteId: String): Flow<DataResult<Int>>

    suspend fun get(id: String): DataResult<Student>

    suspend fun create(draft: NewStudent): DataResult<Student>

    suspend fun update(student: Student): DataResult<Student>

    /**
     * Soft-archives the student and ends every open batch assignment on [on].
     * [on] is an exclusive end: the student is not a member of those batches on that date.
     * Restoring the student does not reopen the assignments.
     */
    suspend fun archive(id: String, on: LocalDate): DataResult<Student>

    suspend fun restore(id: String): DataResult<Student>
}

interface BatchRepository {
    fun observe(instituteId: String, includeArchived: Boolean): Flow<DataResult<List<Batch>>>

    fun observeActive(instituteId: String): Flow<DataResult<List<Batch>>>

    /**
     * Active batches, or archived batches, with the count of active students on an open assignment.
     * The two lists are never mixed.
     */
    fun observeRoster(instituteId: String, archivedOnly: Boolean): Flow<DataResult<List<BatchRoster>>>

    fun observeOne(id: String): Flow<DataResult<Batch>>

    suspend fun countActive(instituteId: String): DataResult<Int>

    /** Active batches only, as a count. Does not load batch rows. */
    fun observeActiveCount(instituteId: String): Flow<DataResult<Int>>

    suspend fun get(id: String): DataResult<Batch>

    suspend fun create(draft: NewBatch): DataResult<Batch>

    suspend fun update(batch: Batch): DataResult<Batch>

    /**
     * Soft-archives the batch and ends every open assignment on [on].
     * [on] is an exclusive end. Restoring the batch does not reopen the assignments.
     */
    suspend fun archive(id: String, on: LocalDate): DataResult<Batch>

    suspend fun restore(id: String): DataResult<Batch>
}

interface StudentBatchRepository {
    fun observeActiveForBatch(batchId: String): Flow<DataResult<List<StudentBatch>>>

    fun observeActiveEnrollments(batchId: String): Flow<DataResult<List<Enrollment>>>

    fun observeHistory(studentId: String): Flow<DataResult<List<StudentBatch>>>

    /** Open assignments for this student, with the batch on each row. */
    fun observeOpenAssignments(studentId: String): Flow<DataResult<List<OpenAssignment>>>

    /** Active batches this student is not already in. */
    fun observeAvailableBatches(studentId: String): Flow<DataResult<List<Batch>>>

    /**
     * Active students who are not already in [batchId].
     * Blank [query] returns that whole list. A non-blank query uses the same SQL search as the student list.
     */
    fun observeAssignableStudents(batchId: String, query: String): Flow<DataResult<List<Student>>>

    suspend fun assign(
        studentId: String,
        batchId: String,
        startedOn: LocalDate,
        allowOverCapacity: Boolean = false,
    ): DataResult<StudentBatch>

    /**
     * Assigns every student in one transaction. A failure writes nothing.
     * When the batch would go past capacity and [allowOverCapacity] is false, the result is
     * [com.tuitionmanager.core.domain.error.DataError.OverCapacity] and no row is inserted.
     */
    suspend fun assignMany(
        studentIds: List<String>,
        batchId: String,
        startedOn: LocalDate,
        allowOverCapacity: Boolean = false,
    ): DataResult<List<StudentBatch>>

    suspend fun end(assignmentId: String, endedOn: LocalDate): DataResult<StudentBatch>

    suspend fun move(
        studentId: String,
        fromBatchId: String,
        toBatchId: String,
        on: LocalDate,
        allowOverCapacity: Boolean = false,
    ): DataResult<StudentBatch>
}
