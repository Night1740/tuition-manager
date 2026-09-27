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
import com.tuitionmanager.core.domain.model.BatchRoster
import com.tuitionmanager.core.domain.model.NewBatch
import com.tuitionmanager.core.domain.repository.BatchRepository
import com.tuitionmanager.core.domain.validation.BatchFormValidation
import com.tuitionmanager.core.domain.validation.validateBatchForm
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

    override fun observeRoster(
        instituteId: String,
        archivedOnly: Boolean,
    ): Flow<DataResult<List<BatchRoster>>> =
        dao.observeRoster(instituteId, if (archivedOnly) 1 else 0)
            .map { rows ->
                DataResult.Success(
                    rows.map { row ->
                        BatchRoster(batch = row.batch.toDomain(), studentCount = row.studentCount)
                    },
                )
            }
            .storageFailures("observe_batches")
            .flowOn(dispatchers.io)

    override fun observeOne(id: String): Flow<DataResult<Batch>> =
        dao.observeById(id)
            .map { entity ->
                if (entity == null) {
                    DataResult.Failure(DataError.NotFound(EntityKind.Batch))
                } else {
                    DataResult.Success(entity.toDomain())
                }
            }
            .storageFailures("observe_batch")
            .flowOn(dispatchers.io)

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
        val parsed = when (
            val validated = validateBatchForm(
                name = draft.name,
                subject = draft.subject,
                days = draft.daysOfWeek,
                startMinute = draft.startMinute,
                endMinute = draft.endMinute,
                room = draft.room,
                capacity = draft.capacity,
            )
        ) {
            is BatchFormValidation.Rejected -> return@runData rejected(validated)
            is BatchFormValidation.Accepted -> validated.batch
        }
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
                daysOfWeek = DaysOfWeekCodec.encode(parsed.daysOfWeek),
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
        val parsed = when (
            val validated = validateBatchForm(
                name = batch.name,
                subject = batch.subject,
                days = batch.daysOfWeek,
                startMinute = batch.startMinute,
                endMinute = batch.endMinute,
                room = batch.room,
                capacity = batch.capacity,
            )
        ) {
            is BatchFormValidation.Rejected -> return@runData rejected(validated)
            is BatchFormValidation.Accepted -> validated.batch
        }
        withContext(dispatchers.io) {
            val existing = dao.getById(batch.id)
                ?: return@withContext DataResult.Failure(DataError.NotFound(EntityKind.Batch))
            if (existing.instituteId != batch.instituteId) {
                return@withContext DataResult.Failure(DataError.Invalid(InvalidCode.CrossInstitute))
            }
            val updated = existing.copy(
                name = parsed.name,
                subject = parsed.subject,
                daysOfWeek = DaysOfWeekCodec.encode(parsed.daysOfWeek),
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

    override suspend fun archive(id: String, on: LocalDate): DataResult<Batch> = runData("archive_batch") {
        withContext(dispatchers.io) {
            val now = clock.instant()
            dao.archiveAndCloseAssignments(id, now, now, on)
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

private fun rejected(validation: BatchFormValidation.Rejected): DataResult<Nothing> {
    val code = validation.errors.firstOrNull()?.code ?: InvalidCode.BlankBatchName
    return DataResult.Failure(DataError.Invalid(code))
}
