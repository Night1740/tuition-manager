package com.tuitionmanager.core.data.repository

import com.tuitionmanager.core.data.local.TuitionDatabase
import com.tuitionmanager.core.data.local.dao.MembershipWrite
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
import com.tuitionmanager.core.domain.model.OpenAssignment
import com.tuitionmanager.core.domain.model.Student
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

    override fun observeOpenAssignments(studentId: String): Flow<DataResult<List<OpenAssignment>>> =
        database.batchDao().observeOpenAssignments(studentId)
            .map { rows ->
                DataResult.Success(
                    rows.map { row ->
                        OpenAssignment(assignmentId = row.assignmentId, batch = row.batch.toDomain())
                    },
                )
            }
            .storageFailures("observe_open_batches")
            .flowOn(dispatchers.io)

    override fun observeAvailableBatches(studentId: String): Flow<DataResult<List<Batch>>> =
        database.batchDao().observeAvailableForStudent(studentId)
            .map { rows -> DataResult.Success(rows.map { it.toDomain() }) }
            .storageFailures("observe_available_batches")
            .flowOn(dispatchers.io)

    override fun observeAssignableStudents(
        batchId: String,
        query: String,
    ): Flow<DataResult<List<Student>>> {
        val trimmed = query.trim()
        val digits = trimmed.filter { it.isDigit() }
        return database.studentDao().observeAssignable(
            batchId = batchId,
            pattern = if (trimmed.isEmpty()) "" else toContainsLikePattern(trimmed),
            digitPattern = if (digits.isEmpty()) "" else toContainsLikePattern(digits),
        )
            .map { rows -> DataResult.Success(rows.map { it.toDomain() }) }
            .storageFailures("observe_assignable_students")
            .flowOn(dispatchers.io)
    }

    override suspend fun assign(
        studentId: String,
        batchId: String,
        startedOn: LocalDate,
        allowOverCapacity: Boolean,
    ): DataResult<StudentBatch> {
        val many = assignMany(listOf(studentId), batchId, startedOn, allowOverCapacity)
        return when (many) {
            is DataResult.Success -> DataResult.Success(many.value.single())
            is DataResult.Failure -> many
        }
    }

    override suspend fun assignMany(
        studentIds: List<String>,
        batchId: String,
        startedOn: LocalDate,
        allowOverCapacity: Boolean,
    ): DataResult<List<StudentBatch>> = runData("assign_student_batch") {
        val distinctIds = studentIds.distinct()
        if (distinctIds.isEmpty()) return@runData DataResult.Success(emptyList())
        withContext(dispatchers.io) {
            val batch = database.batchDao().getById(batchId)
                ?: return@withContext DataResult.Failure(DataError.NotFound(EntityKind.Batch))
            if (batch.archivedAt != null) {
                return@withContext DataResult.Failure(DataError.Invalid(InvalidCode.BatchArchived))
            }
            val found = database.studentDao().getByIds(distinctIds)
            if (found.size != distinctIds.size) {
                return@withContext DataResult.Failure(DataError.NotFound(EntityKind.Student))
            }
            if (found.any { it.archivedAt != null }) {
                return@withContext DataResult.Failure(DataError.Invalid(InvalidCode.StudentArchived))
            }
            if (found.any { it.instituteId != batch.instituteId }) {
                return@withContext DataResult.Failure(DataError.Invalid(InvalidCode.CrossInstitute))
            }
            val createdAt = clock.instant()
            val entities = distinctIds.map { studentId ->
                StudentBatchEntity(
                    id = ids.newId(),
                    studentId = studentId,
                    batchId = batchId,
                    startedOn = startedOn,
                    endedOn = null,
                    activeSlot = activeSlotOf(studentId, batchId),
                    createdAt = createdAt,
                )
            }
            when (val outcome = dao.insertMany(entities, batch.capacity, allowOverCapacity)) {
                is MembershipWrite.Written -> DataResult.Success(entities.map { it.toDomain() })
                else -> outcome.toResult()
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
        allowOverCapacity: Boolean,
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
            when (
                val outcome = dao.move(
                    fromId = active.id,
                    endedOn = on,
                    created = created,
                    capacity = destination.capacity,
                    allowOverCapacity = allowOverCapacity,
                )
            ) {
                is MembershipWrite.Written -> DataResult.Success(created.toDomain())
                else -> outcome.toResult()
            }
        }
    }
}

private fun MembershipWrite.toResult(): DataResult<Nothing> = when (this) {
    is MembershipWrite.OverCapacity -> DataResult.Failure(
        DataError.OverCapacity(enrolled = enrolled, adding = adding, capacity = capacity),
    )
    MembershipWrite.Duplicate -> DataResult.Failure(
        DataError.Conflict(ConflictCode.DuplicateActiveAssignment),
    )
    MembershipWrite.Missing -> DataResult.Failure(DataError.NotFound(EntityKind.StudentBatch))
    is MembershipWrite.Written -> DataResult.Failure(DataError.Storage("assign_student_batch"))
}
