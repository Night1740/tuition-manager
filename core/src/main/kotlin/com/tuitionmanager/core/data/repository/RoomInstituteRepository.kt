package com.tuitionmanager.core.data.repository

import com.tuitionmanager.core.data.local.TuitionDatabase
import com.tuitionmanager.core.data.local.entity.InstituteEntity
import com.tuitionmanager.core.data.mapper.toDomain
import com.tuitionmanager.core.domain.dispatch.DispatcherProvider
import com.tuitionmanager.core.domain.error.ConflictCode
import com.tuitionmanager.core.domain.error.DataError
import com.tuitionmanager.core.domain.error.DataResult
import com.tuitionmanager.core.domain.error.EntityKind
import com.tuitionmanager.core.domain.model.Institute
import com.tuitionmanager.core.domain.model.NewInstitute
import com.tuitionmanager.core.domain.repository.InstituteRepository
import com.tuitionmanager.core.domain.validation.InstituteValidation
import com.tuitionmanager.core.domain.validation.validateInstituteInput
import com.tuitionmanager.core.id.IdGenerator
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
class RoomInstituteRepository @Inject constructor(
    private val database: TuitionDatabase,
    private val ids: IdGenerator,
    private val clock: Clock,
    private val dispatchers: DispatcherProvider,
) : InstituteRepository {
    private val dao get() = database.instituteDao()

    override fun observe(): Flow<DataResult<Institute?>> =
        dao.observeFirst()
            .map { entity -> DataResult.Success(entity?.toDomain()) }
            .storageFailures("observe_institute")
            .flowOn(dispatchers.io)

    override suspend fun get(): DataResult<Institute?> = runData("get_institute") {
        withContext(dispatchers.io) {
            DataResult.Success(dao.getFirst()?.toDomain())
        }
    }

    override suspend fun create(draft: NewInstitute): DataResult<Institute> = runData("create_institute") {
        val fields = when (val validated = validateInstituteInput(draft.name, draft.ownerName, draft.phone, draft.address)) {
            is InstituteValidation.Rejected ->
                return@runData DataResult.Failure(DataError.Invalid(validated.code))
            is InstituteValidation.Accepted -> validated.draft
        }
        withContext(dispatchers.io) {
            val now = clock.instant()
            val entity = InstituteEntity(
                id = ids.newId(),
                name = fields.name,
                ownerName = fields.ownerName,
                phone = fields.phone,
                address = fields.address,
                createdAt = now,
                updatedAt = now,
            )
            if (!dao.insertFirst(entity)) {
                DataResult.Failure(DataError.Conflict(ConflictCode.InstituteAlreadyExists))
            } else {
                DataResult.Success(entity.toDomain())
            }
        }
    }

    override suspend fun update(institute: Institute): DataResult<Institute> = runData("update_institute") {
        val fields = when (
            val validated = validateInstituteInput(
                institute.name,
                institute.ownerName,
                institute.phone,
                institute.address,
            )
        ) {
            is InstituteValidation.Rejected ->
                return@runData DataResult.Failure(DataError.Invalid(validated.code))
            is InstituteValidation.Accepted -> validated.draft
        }
        withContext(dispatchers.io) {
            val existing = dao.getById(institute.id)
                ?: return@withContext DataResult.Failure(DataError.NotFound(EntityKind.Institute))
            val updated = existing.copy(
                name = fields.name,
                ownerName = fields.ownerName,
                phone = fields.phone,
                address = fields.address,
                updatedAt = clock.instant(),
            )
            dao.update(updated)
            DataResult.Success(updated.toDomain())
        }
    }
}
