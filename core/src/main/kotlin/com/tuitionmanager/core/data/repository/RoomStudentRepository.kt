package com.tuitionmanager.core.data.repository

import com.tuitionmanager.core.data.local.TuitionDatabase
import com.tuitionmanager.core.data.local.entity.StudentEntity
import com.tuitionmanager.core.data.mapper.toDomain
import com.tuitionmanager.core.domain.dispatch.DispatcherProvider
import com.tuitionmanager.core.domain.error.DataError
import com.tuitionmanager.core.domain.error.DataResult
import com.tuitionmanager.core.domain.error.EntityKind
import com.tuitionmanager.core.domain.error.InvalidCode
import com.tuitionmanager.core.domain.model.NewStudent
import com.tuitionmanager.core.domain.model.Student
import com.tuitionmanager.core.domain.repository.StudentRepository
import com.tuitionmanager.core.domain.validation.StudentFormValidation
import com.tuitionmanager.core.domain.validation.nextFreeStudentCode
import com.tuitionmanager.core.domain.validation.validateStudentForm
import com.tuitionmanager.core.id.IdGenerator
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
class RoomStudentRepository @Inject constructor(
    private val database: TuitionDatabase,
    private val ids: IdGenerator,
    private val clock: Clock,
    private val dispatchers: DispatcherProvider,
) : StudentRepository {
    private val dao get() = database.studentDao()

    override fun observe(instituteId: String, includeArchived: Boolean): Flow<DataResult<List<Student>>> =
        (if (includeArchived) dao.observeAll(instituteId) else dao.observeActive(instituteId))
            .map { rows -> DataResult.Success(rows.map { it.toDomain() }) }
            .storageFailures("observe_students")
            .flowOn(dispatchers.io)

    override fun observeActive(instituteId: String, query: String): Flow<DataResult<List<Student>>> =
        observeList(instituteId, archivedOnly = false, query = query)

    override fun observeList(
        instituteId: String,
        archivedOnly: Boolean,
        query: String,
    ): Flow<DataResult<List<Student>>> {
        val trimmed = query.trim()
        val source = if (trimmed.isEmpty()) {
            if (archivedOnly) dao.observeArchived(instituteId) else dao.observeActive(instituteId)
        } else {
            val digits = trimmed.filter { it.isDigit() }
            dao.observeMatching(
                instituteId = instituteId,
                archivedOnly = if (archivedOnly) 1 else 0,
                pattern = toContainsLikePattern(trimmed),
                digitPattern = if (digits.isEmpty()) "" else toContainsLikePattern(digits),
            )
        }
        return source
            .map { rows -> DataResult.Success(rows.map { it.toDomain() }) }
            .storageFailures("observe_students")
            .flowOn(dispatchers.io)
    }

    override fun observeOne(id: String): Flow<DataResult<Student>> =
        dao.observeById(id)
            .map { entity ->
                if (entity == null) {
                    DataResult.Failure(DataError.NotFound(EntityKind.Student))
                } else {
                    DataResult.Success(entity.toDomain())
                }
            }
            .storageFailures("observe_student")
            .flowOn(dispatchers.io)

    override suspend fun suggestCode(instituteId: String): DataResult<String> = runData("suggest_student_code") {
        withContext(dispatchers.io) {
            if (database.instituteDao().getById(instituteId) == null) {
                return@withContext DataResult.Failure(DataError.NotFound(EntityKind.Institute))
            }
            DataResult.Success(nextFreeStudentCode(dao.studentCodes(instituteId)))
        }
    }

    override suspend fun countActive(instituteId: String): DataResult<Int> = runData("count_students") {
        withContext(dispatchers.io) {
            DataResult.Success(dao.countActive(instituteId))
        }
    }

    override suspend fun get(id: String): DataResult<Student> = runData("get_student") {
        withContext(dispatchers.io) {
            val entity = dao.getById(id)
                ?: return@withContext DataResult.Failure(DataError.NotFound(EntityKind.Student))
            DataResult.Success(entity.toDomain())
        }
    }

    override suspend fun create(draft: NewStudent): DataResult<Student> = runData("create_student") {
        val parsed = when (
            val validated = validateStudentForm(
                name = draft.name,
                studentCode = draft.studentCode,
                guardianName = draft.guardianName,
                guardianPhone = draft.guardianPhone,
                studentPhone = draft.phone,
                notes = draft.notes,
                photoUri = draft.photoUri,
            )
        ) {
            is StudentFormValidation.Rejected -> return@runData rejected(validated)
            is StudentFormValidation.Accepted -> validated.student
        }
        withContext(dispatchers.io) {
            if (database.instituteDao().getById(draft.instituteId) == null) {
                return@withContext DataResult.Failure(DataError.NotFound(EntityKind.Institute))
            }
            val now = clock.instant()
            val entity = StudentEntity(
                id = ids.newId(),
                instituteId = draft.instituteId,
                name = parsed.name,
                studentCode = parsed.studentCode,
                guardianName = parsed.guardianName,
                guardianPhone = parsed.guardianPhone,
                phone = parsed.phone,
                photoUri = parsed.photoUri,
                admissionDate = draft.admissionDate,
                notes = parsed.notes,
                archivedAt = null,
                createdAt = now,
                updatedAt = now,
            )
            dao.insert(entity)
            DataResult.Success(entity.toDomain())
        }
    }

    override suspend fun update(student: Student): DataResult<Student> = runData("update_student") {
        val parsed = when (
            val validated = validateStudentForm(
                name = student.name,
                studentCode = student.studentCode,
                guardianName = student.guardianName,
                guardianPhone = student.guardianPhone,
                studentPhone = student.phone,
                notes = student.notes,
                photoUri = student.photoUri,
            )
        ) {
            is StudentFormValidation.Rejected -> return@runData rejected(validated)
            is StudentFormValidation.Accepted -> validated.student
        }
        withContext(dispatchers.io) {
            val existing = dao.getById(student.id)
                ?: return@withContext DataResult.Failure(DataError.NotFound(EntityKind.Student))
            if (existing.instituteId != student.instituteId) {
                return@withContext DataResult.Failure(DataError.Invalid(InvalidCode.CrossInstitute))
            }
            val updated = existing.copy(
                name = parsed.name,
                studentCode = parsed.studentCode,
                guardianName = parsed.guardianName,
                guardianPhone = parsed.guardianPhone,
                phone = parsed.phone,
                photoUri = parsed.photoUri,
                admissionDate = student.admissionDate,
                notes = parsed.notes,
                updatedAt = clock.instant(),
            )
            dao.update(updated)
            DataResult.Success(updated.toDomain())
        }
    }

    override suspend fun archive(id: String, on: LocalDate): DataResult<Student> = runData("archive_student") {
        withContext(dispatchers.io) {
            val now = clock.instant()
            dao.archiveAndCloseAssignments(id, now, now, on)
            val entity = dao.getById(id)
                ?: return@withContext DataResult.Failure(DataError.NotFound(EntityKind.Student))
            DataResult.Success(entity.toDomain())
        }
    }

    override suspend fun restore(id: String): DataResult<Student> = runData("restore_student") {
        withContext(dispatchers.io) {
            val existing = dao.getById(id)
                ?: return@withContext DataResult.Failure(DataError.NotFound(EntityKind.Student))
            if (existing.archivedAt == null) {
                return@withContext DataResult.Success(existing.toDomain())
            }
            val updated = existing.copy(archivedAt = null, updatedAt = clock.instant())
            dao.update(updated)
            DataResult.Success(updated.toDomain())
        }
    }
}

private fun rejected(validation: StudentFormValidation.Rejected): DataResult<Nothing> {
    val code = validation.errors.firstOrNull()?.code ?: InvalidCode.BlankName
    return DataResult.Failure(DataError.Invalid(code))
}

