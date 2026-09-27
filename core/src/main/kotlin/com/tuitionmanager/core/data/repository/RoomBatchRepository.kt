package com.tuitionmanager.core.data.repository

import com.tuitionmanager.core.data.local.DaysOfWeekCodec
import com.tuitionmanager.core.data.local.TuitionDatabase
import com.tuitionmanager.core.data.local.entity.BatchEntity
import com.tuitionmanager.core.data.mapper.toDomain
import com.tuitionmanager.core.domain.dispatch.DispatcherProvider
import com.tuitionmanager.core.domain.error.DataError
import com.tuitionmanager.core.domain.error.DataResult
import com.tuitionmanager.core.domain.error.EntityKind
import com.tuitionmanager.core.domain.error.InvalidCode
import com.tuitionmanager.core.domain.model.Batch
import com.tuitionmanager.core.domain.model.NewBatch
import com.tuitionmanager.core.domain.repository.BatchRepository
import com.tuitionmanager.core.domain.validation.MAX_BATCH_CAPACITY
import com.tuitionmanager.core.domain.validation.MAX_ROOM_LENGTH
import com.tuitionmanager.core.domain.validation.MAX_SUBJECT_LENGTH
import com.tuitionmanager.core.domain.validation.MIN_BATCH_CAPACITY
import com.tuitionmanager.core.domain.validation.OptionalText
import com.tuitionmanager.core.domain.validation.isValidClockMinutes
import com.tuitionmanager.core.domain.validation.normalizeRequiredName
import com.tuitionmanager.core.domain.validation.parseOptionalText
import com.tuitionmanager.core.id.IdGenerator
import java.time.Clock
import java.time.DayOfWeek
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
class RoomBatchRepository @Inject constructor(
    private val database: TuitionDatabase,
    private val ids: IdGenerator,
    private val clock: Clock,
    private val dispatchers: DispatcherProvider,
) : BatchRepository {
    private val dao get() = database.batchDao()

    override fun observe(instituteId: String, includeArchived: Boolean): Flow<DataResult<List<Batch>>> =
        (if (includeArchived) dao.observeAll(instituteId) else dao.observeActive(instituteId))
            .map { rows -> DataResult.Success(rows.map { it.toDomain() }) }
            .storageFailures("observe_batches")
            .flowOn(dispatchers.io)

    override fun observeActive(instituteId: String): Flow<DataResult<List<Batch>>> =
        observe(instituteId, includeArchived = false)

    override suspend fun countActive(instituteId: String): DataResult<Int> = runData("count_batches") {
        withContext(dispatchers.io) {
            DataResult.Success(dao.countActive(instituteId))
        }
    }

    override suspend fun get(id: String): DataResult<Batch> = runData("get_batch") {
        withContext(dispatchers.io) {
            val entity = dao.getById(id)
                ?: return@withContext DataResult.Failure(DataError.NotFound(EntityKind.Batch))
            DataResult.Success(entity.toDomain())
        }
    }

    override suspend fun create(draft: NewBatch): DataResult<Batch> = runData("create_batch") {
        val fields = batchFields(
            name = draft.name,
            subject = draft.subject,
            days = draft.daysOfWeek,
            startMinute = draft.startMinute,
            endMinute = draft.endMinute,
            room = draft.room,
            capacity = draft.capacity,
        )
        if (fields is BatchFieldError) return@runData DataResult.Failure(DataError.Invalid(fields.code))
        val parsed = fields as BatchFields
        withContext(dispatchers.io) {
            if (database.instituteDao().getById(draft.instituteId) == null) {
                return@withContext DataResult.Failure(DataError.NotFound(EntityKind.Institute))
            }
            val now = clock.instant()
            val entity = BatchEntity(
                id = ids.newId(),
                instituteId = draft.instituteId,
                name = parsed.name,
                subject = parsed.subject,
                daysOfWeek = DaysOfWeekCodec.encode(parsed.days),
                startMinute = parsed.startMinute,
                endMinute = parsed.endMinute,
                room = parsed.room,
                capacity = parsed.capacity,
                archivedAt = null,
                createdAt = now,
                updatedAt = now,
            )
            dao.insert(entity)
            DataResult.Success(entity.toDomain())
        }
    }

    override suspend fun update(batch: Batch): DataResult<Batch> = runData("update_batch") {
        val fields = batchFields(
            name = batch.name,
            subject = batch.subject,
            days = batch.daysOfWeek,
            startMinute = batch.startMinute,
            endMinute = batch.endMinute,
            room = batch.room,
            capacity = batch.capacity,
        )
        if (fields is BatchFieldError) return@runData DataResult.Failure(DataError.Invalid(fields.code))
        val parsed = fields as BatchFields
        withContext(dispatchers.io) {
            val existing = dao.getById(batch.id)
                ?: return@withContext DataResult.Failure(DataError.NotFound(EntityKind.Batch))
            if (existing.instituteId != batch.instituteId) {
                return@withContext DataResult.Failure(DataError.Invalid(InvalidCode.CrossInstitute))
            }
            val updated = existing.copy(
                name = parsed.name,
                subject = parsed.subject,
                daysOfWeek = DaysOfWeekCodec.encode(parsed.days),
                startMinute = parsed.startMinute,
                endMinute = parsed.endMinute,
                room = parsed.room,
                capacity = parsed.capacity,
                updatedAt = clock.instant(),
            )
            dao.update(updated)
            DataResult.Success(updated.toDomain())
        }
    }

    override suspend fun archive(id: String): DataResult<Batch> = runData("archive_batch") {
        withContext(dispatchers.io) {
            val now = clock.instant()
            dao.archive(id, now, now)
            val entity = dao.getById(id)
                ?: return@withContext DataResult.Failure(DataError.NotFound(EntityKind.Batch))
            DataResult.Success(entity.toDomain())
        }
    }

    override suspend fun restore(id: String): DataResult<Batch> = runData("restore_batch") {
        withContext(dispatchers.io) {
            val existing = dao.getById(id)
                ?: return@withContext DataResult.Failure(DataError.NotFound(EntityKind.Batch))
            if (existing.archivedAt == null) {
                return@withContext DataResult.Success(existing.toDomain())
            }
            val updated = existing.copy(archivedAt = null, updatedAt = clock.instant())
            dao.update(updated)
            DataResult.Success(updated.toDomain())
        }
    }
}

private sealed interface ParsedBatch

private data class BatchFields(
    val name: String,
    val subject: String,
    val days: Set<DayOfWeek>,
    val startMinute: Int,
    val endMinute: Int,
    val room: String?,
    val capacity: Int,
) : ParsedBatch

private data class BatchFieldError(val code: InvalidCode) : ParsedBatch

private fun batchFields(
    name: String,
    subject: String,
    days: Set<DayOfWeek>,
    startMinute: Int,
    endMinute: Int,
    room: String?,
    capacity: Int,
): ParsedBatch {
    val normalizedName = normalizeRequiredName(name) ?: return BatchFieldError(InvalidCode.BlankBatchName)
    val normalizedSubject = normalizeRequiredName(subject, MAX_SUBJECT_LENGTH)
        ?: return BatchFieldError(InvalidCode.BlankSubject)
    if (days.isEmpty()) return BatchFieldError(InvalidCode.InvalidSchedule)
    if (!isValidClockMinutes(startMinute, endMinute)) return BatchFieldError(InvalidCode.InvalidSchedule)
    if (capacity !in MIN_BATCH_CAPACITY..MAX_BATCH_CAPACITY) {
        return BatchFieldError(InvalidCode.InvalidCapacity)
    }
    val parsedRoom = parseOptionalText(room, MAX_ROOM_LENGTH)
    if (parsedRoom is OptionalText.TooLong) return BatchFieldError(InvalidCode.InvalidSchedule)
    return BatchFields(
        name = normalizedName,
        subject = normalizedSubject,
        days = days,
        startMinute = startMinute,
        endMinute = endMinute,
        room = (parsedRoom as OptionalText.Value).text,
        capacity = capacity,
    )
}
