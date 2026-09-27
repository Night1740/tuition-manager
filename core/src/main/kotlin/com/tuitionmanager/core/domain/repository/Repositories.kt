package com.tuitionmanager.core.domain.repository

import com.tuitionmanager.core.domain.error.DataResult
import com.tuitionmanager.core.domain.model.Batch
import com.tuitionmanager.core.domain.model.Enrollment
import com.tuitionmanager.core.domain.model.Institute
import com.tuitionmanager.core.domain.model.NewBatch
import com.tuitionmanager.core.domain.model.NewInstitute
import com.tuitionmanager.core.domain.model.NewStudent
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

    suspend fun countActive(instituteId: String): DataResult<Int>

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

    suspend fun countActive(instituteId: String): DataResult<Int>

    suspend fun get(id: String): DataResult<Batch>

    suspend fun create(draft: NewBatch): DataResult<Batch>

    suspend fun update(batch: Batch): DataResult<Batch>

    suspend fun archive(id: String): DataResult<Batch>

    suspend fun restore(id: String): DataResult<Batch>
}

interface StudentBatchRepository {
    fun observeActiveForBatch(batchId: String): Flow<DataResult<List<StudentBatch>>>

    fun observeActiveEnrollments(batchId: String): Flow<DataResult<List<Enrollment>>>

    fun observeHistory(studentId: String): Flow<DataResult<List<StudentBatch>>>

    suspend fun assign(
        studentId: String,
        batchId: String,
        startedOn: LocalDate,
    ): DataResult<StudentBatch>

    suspend fun end(assignmentId: String, endedOn: LocalDate): DataResult<StudentBatch>

    suspend fun move(
        studentId: String,
        fromBatchId: String,
        toBatchId: String,
        on: LocalDate,
    ): DataResult<StudentBatch>
}
