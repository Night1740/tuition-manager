package com.tuitionmanager.core.data.repository

import com.tuitionmanager.core.data.local.TuitionDatabase
import com.tuitionmanager.core.data.local.entity.StudentBatchEntity
import com.tuitionmanager.core.data.local.entity.activeSlotOf
import com.tuitionmanager.core.data.mapper.toDomain
import com.tuitionmanager.core.domain.dispatch.DispatcherProvider
import com.tuitionmanager.core.domain.error.ConflictCode
import com.tuitionmanager.core.domain.error.DataError
import com.tuitionmanager.core.domain.error.DataResult
import com.tuitionmanager.core.domain.error.EntityKind
import com.tuitionmanager.core.domain.error.InvalidCode
import com.tuitionmanager.core.domain.model.Batch
import com.tuitionmanager.core.domain.model.Enrollment
import com.tuitionmanager.core.domain.model.StudentBatch
import com.tuitionmanager.core.domain.repository.StudentBatchRepository
import com.tuitionmanager.core.id.IdGenerator
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.withContext

@Singleton
class RoomStudentBatchRepository @Inject constructor(
    private val database: TuitionDatabase,
    private val ids: IdGenerator,
    private val clock: Clock,
    private val dispatchers: DispatcherProvider,
) : StudentBatchRepository {
    private val dao get() = database.studentBatchDao()

    override fun observeActiveForBatch(batchId: String): Flow<DataResult<List<StudentBatch>>> =
        dao.observeActiveByBatch(batchId)
            .map { rows -> DataResult.Success(rows.map { it.toDomain() }) }
            .storageFailures("observe_student_batch")
            .flowOn(dispatchers.io)

    override fun observeActiveEnrollments(batchId: String): Flow<DataResult<List<Enrollment>>> =
        dao.observeActiveByBatch(batchId)
            .transform { rows ->
                val students = if (rows.isEmpty()) {
                    emptyMap()
                } else {
                    database.studentDao().getByIds(rows.map { it.studentId }).associateBy { it.id }
                }
                val enrollments = rows.mapNotNull { row ->
                    val student = students[row.studentId] ?: return@mapNotNull null
                    if (student.archivedAt != null) return@mapNotNull null
                    Enrollment(assignment = row.toDomain(), student = student.toDomain())
                }
                emit(DataResult.Success(enrollments))
            }
            .storageFailures("observe_enrollments")
            .flowOn(dispatchers.io)

    override fun observeHistory(studentId: String): Flow<DataResult<List<StudentBatch>>> =
        dao.observeByStudent(studentId)
            .map { rows -> DataResult.Success(rows.map { it.toDomain() }) }
            .storageFailures("observe_student_batch_history")
            .flowOn(dispatchers.io)

    override fun observeOpenBatches(studentId: String): Flow<DataResult<List<Batch>>> =
        database.batchDao().observeOpenForStudent(studentId)
            .map { rows -> DataResult.Success(rows.map { it.toDomain() }) }
            .storageFailures("observe_open_batches")
            .flowOn(dispatchers.io)

    override suspend fun assign(
        studentId: String,
        batchId: String,
        startedOn: LocalDate,
    ): DataResult<StudentBatch> = runData("assign_student_batch") {
        withContext(dispatchers.io) {
            val student = database.studentDao().getById(studentId)
                ?: return@withContext DataResult.Failure(DataError.NotFound(EntityKind.Student))
            if (student.archivedAt != null) {
                return@withContext DataResult.Failure(DataError.Invalid(InvalidCode.StudentArchived))
            }
            val batch = database.batchDao().getById(batchId)
                ?: return@withContext DataResult.Failure(DataError.NotFound(EntityKind.Batch))
            if (batch.archivedAt != null) {
                return@withContext DataResult.Failure(DataError.Invalid(InvalidCode.BatchArchived))
            }
            if (student.instituteId != batch.instituteId) {
                return@withContext DataResult.Failure(DataError.Invalid(InvalidCode.CrossInstitute))
            }
            val entity = StudentBatchEntity(
                id = ids.newId(),
                studentId = studentId,
                batchId = batchId,
                startedOn = startedOn,
                endedOn = null,
                activeSlot = activeSlotOf(studentId, batchId),
                createdAt = clock.instant(),
            )
            when (dao.insertActive(entity, batch.capacity)) {
                1 -> DataResult.Success(entity.toDomain())
                -1 -> DataResult.Failure(DataError.Conflict(ConflictCode.BatchFull))
                else -> DataResult.Failure(DataError.Storage("assign_student_batch"))
            }
        }
    }

    override suspend fun end(assignmentId: String, endedOn: LocalDate): DataResult<StudentBatch> =
        runData("end_student_batch") {
            withContext(dispatchers.io) {
                val existing = dao.getById(assignmentId)
                    ?: return@withContext DataResult.Failure(DataError.NotFound(EntityKind.StudentBatch))
                if (existing.endedOn != null) {
                    return@withContext DataResult.Success(existing.toDomain())
                }
                if (endedOn < existing.startedOn) {
                    return@withContext DataResult.Failure(DataError.Invalid(InvalidCode.InvalidDateRange))
                }
                dao.end(assignmentId, endedOn)
                val updated = dao.getById(assignmentId)
                    ?: return@withContext DataResult.Failure(DataError.NotFound(EntityKind.StudentBatch))
                DataResult.Success(updated.toDomain())
            }
        }

    override suspend fun move(
        studentId: String,
        fromBatchId: String,
        toBatchId: String,
        on: LocalDate,
    ): DataResult<StudentBatch> = runData("move_student_batch") {
        if (fromBatchId == toBatchId) {
            return@runData DataResult.Failure(DataError.Invalid(InvalidCode.SameBatch))
        }
        withContext(dispatchers.io) {
            val student = database.studentDao().getById(studentId)
                ?: return@withContext DataResult.Failure(DataError.NotFound(EntityKind.Student))
            if (student.archivedAt != null) {
                return@withContext DataResult.Failure(DataError.Invalid(InvalidCode.StudentArchived))
            }
            val destination = database.batchDao().getById(toBatchId)
                ?: return@withContext DataResult.Failure(DataError.NotFound(EntityKind.Batch))
            if (destination.archivedAt != null) {
                return@withContext DataResult.Failure(DataError.Invalid(InvalidCode.BatchArchived))
            }
            if (database.batchDao().getById(fromBatchId) == null) {
                return@withContext DataResult.Failure(DataError.NotFound(EntityKind.Batch))
            }
            if (student.instituteId != destination.instituteId) {
                return@withContext DataResult.Failure(DataError.Invalid(InvalidCode.CrossInstitute))
            }
            val active = dao.findActive(studentId, fromBatchId)
                ?: return@withContext DataResult.Failure(DataError.NotFound(EntityKind.StudentBatch))
            if (on < active.startedOn) {
                return@withContext DataResult.Failure(DataError.Invalid(InvalidCode.InvalidDateRange))
            }
            val created = StudentBatchEntity(
                id = ids.newId(),
                studentId = studentId,
                batchId = toBatchId,
                startedOn = on,
                endedOn = null,
                activeSlot = activeSlotOf(studentId, toBatchId),
                createdAt = clock.instant(),
            )
            when (dao.move(active.id, on, created, destination.capacity)) {
                1 -> DataResult.Success(created.toDomain())
                -1 -> DataResult.Failure(DataError.Conflict(ConflictCode.BatchFull))
                0 -> DataResult.Failure(DataError.NotFound(EntityKind.StudentBatch))
                else -> DataResult.Failure(DataError.Storage("move_student_batch"))
            }
        }
    }
}
