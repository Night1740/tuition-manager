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
import com.tuitionmanager.core.domain.validation.ContactValidation
import com.tuitionmanager.core.domain.validation.MAX_NOTES_LENGTH
import com.tuitionmanager.core.domain.validation.MAX_PHOTO_URI_LENGTH
import com.tuitionmanager.core.domain.validation.OptionalText
import com.tuitionmanager.core.domain.validation.normalizeRequiredName
import com.tuitionmanager.core.domain.validation.normalizeStudentCode
import com.tuitionmanager.core.domain.validation.parseOptionalText
import com.tuitionmanager.core.domain.validation.validateStudentContacts
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

    override fun observeActive(instituteId: String, query: String): Flow<DataResult<List<Student>>> {
        val source = if (query.isBlank()) {
            dao.observeActive(instituteId)
        } else {
            dao.observeActiveMatching(instituteId, toContainsLikePattern(query))
        }
        return source
            .map { rows -> DataResult.Success(rows.map { it.toDomain() }) }
            .storageFailures("observe_students")
            .flowOn(dispatchers.io)
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
        val fields = studentFields(
            name = draft.name,
            studentCode = draft.studentCode,
            guardianName = draft.guardianName,
            guardianPhone = draft.guardianPhone,
            phone = draft.phone,
            photoUri = draft.photoUri,
            notes = draft.notes,
        )
        if (fields is FieldError) return@runData DataResult.Failure(DataError.Invalid(fields.code))
        val parsed = fields as StudentFields
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
        val fields = studentFields(
            name = student.name,
            studentCode = student.studentCode,
            guardianName = student.guardianName,
            guardianPhone = student.guardianPhone,
            phone = student.phone,
            photoUri = student.photoUri,
            notes = student.notes,
        )
        if (fields is FieldError) return@runData DataResult.Failure(DataError.Invalid(fields.code))
        val parsed = fields as StudentFields
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

private sealed interface ParsedStudent

private data class StudentFields(
    val name: String,
    val studentCode: String,
    val guardianName: String?,
    val guardianPhone: String?,
    val phone: String?,
    val photoUri: String?,
    val notes: String?,
) : ParsedStudent

private data class FieldError(val code: InvalidCode) : ParsedStudent

private fun studentFields(
    name: String,
    studentCode: String,
    guardianName: String?,
    guardianPhone: String?,
    phone: String?,
    photoUri: String?,
    notes: String?,
): ParsedStudent {
    val normalizedName = normalizeRequiredName(name) ?: return FieldError(InvalidCode.BlankName)
    val code = when (val trimmed = studentCode.trim()) {
        "" -> return FieldError(InvalidCode.BlankStudentCode)
        else -> normalizeStudentCode(trimmed) ?: return FieldError(InvalidCode.InvalidStudentCode)
    }
    val contacts = when (val parsed = validateStudentContacts(guardianName, guardianPhone, phone)) {
        is ContactValidation.Rejected -> return FieldError(parsed.code)
        is ContactValidation.Accepted -> parsed.contacts
    }
    val photo = when (val parsed = parsePhoto(photoUri)) {
        is PhotoValue -> parsed.uri
        is FieldError -> return parsed
        is StudentFields -> error("Unexpected photo parse result")
    }
    val parsedNotes = parseOptionalText(notes, MAX_NOTES_LENGTH)
    if (parsedNotes is OptionalText.TooLong) return FieldError(InvalidCode.NotesTooLong)
    return StudentFields(
        name = normalizedName,
        studentCode = code,
        guardianName = contacts.guardianName,
        guardianPhone = contacts.guardianPhone,
        phone = contacts.studentPhone,
        photoUri = photo,
        notes = (parsedNotes as OptionalText.Value).text,
    )
}

private data class PhotoValue(val uri: String?) : ParsedStudent

private fun parsePhoto(raw: String?): ParsedStudent {
    if (raw.isNullOrBlank()) return PhotoValue(null)
    val trimmed = raw.trim()
    if ('\n' in trimmed || '\r' in trimmed) return FieldError(InvalidCode.InvalidPhotoUri)
    if (trimmed.length > MAX_PHOTO_URI_LENGTH) return FieldError(InvalidCode.PhotoUriTooLong)
    return PhotoValue(trimmed)
}
