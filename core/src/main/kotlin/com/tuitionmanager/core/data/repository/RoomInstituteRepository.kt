package com.tuitionmanager.core.data.repository

import com.tuitionmanager.core.data.local.TuitionDatabase
import com.tuitionmanager.core.data.local.entity.InstituteEntity
import com.tuitionmanager.core.data.mapper.toDomain
import com.tuitionmanager.core.domain.dispatch.DispatcherProvider
import com.tuitionmanager.core.domain.error.ConflictCode
import com.tuitionmanager.core.domain.error.DataError
import com.tuitionmanager.core.domain.error.DataResult
import com.tuitionmanager.core.domain.error.EntityKind
import com.tuitionmanager.core.domain.error.InvalidCode
import com.tuitionmanager.core.domain.model.Institute
import com.tuitionmanager.core.domain.model.NewInstitute
import com.tuitionmanager.core.domain.repository.InstituteRepository
import com.tuitionmanager.core.domain.validation.MAX_ADDRESS_LENGTH
import com.tuitionmanager.core.domain.validation.OptionalText
import com.tuitionmanager.core.domain.validation.PhoneParse
import com.tuitionmanager.core.domain.validation.normalizeRequiredName
import com.tuitionmanager.core.domain.validation.parseOptionalText
import com.tuitionmanager.core.domain.validation.parsePhone
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
        val fields = validate(draft.name, draft.ownerName, draft.phone, draft.address)
            ?: return@runData invalidFields(draft.name, draft.ownerName, draft.phone, draft.address)
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
        val fields = validate(institute.name, institute.ownerName, institute.phone, institute.address)
            ?: return@runData invalidFields(
                institute.name,
                institute.ownerName,
                institute.phone,
                institute.address,
            )
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

    private fun invalidFields(
        name: String,
        ownerName: String,
        phone: String?,
        address: String?,
    ): DataResult<Institute> {
        val code = when {
            normalizeRequiredName(name) == null -> InvalidCode.BlankName
            normalizeRequiredName(ownerName) == null -> InvalidCode.BlankOwnerName
            parsePhone(phone, required = false) is PhoneParse.Invalid -> InvalidCode.InvalidPhone
            parseOptionalText(address, MAX_ADDRESS_LENGTH) is OptionalText.TooLong -> InvalidCode.AddressTooLong
            else -> InvalidCode.BlankName
        }
        return DataResult.Failure(DataError.Invalid(code))
    }
}

private data class InstituteFields(
    val name: String,
    val ownerName: String,
    val phone: String?,
    val address: String?,
)

private fun validate(name: String, ownerName: String, phone: String?, address: String?): InstituteFields? {
    val normalizedName = normalizeRequiredName(name) ?: return null
    val normalizedOwner = normalizeRequiredName(ownerName) ?: return null
    val parsedPhone = parsePhone(phone, required = false)
    if (parsedPhone is PhoneParse.Invalid) return null
    val parsedAddress = parseOptionalText(address, MAX_ADDRESS_LENGTH)
    if (parsedAddress is OptionalText.TooLong) return null
    return InstituteFields(
        name = normalizedName,
        ownerName = normalizedOwner,
        phone = (parsedPhone as PhoneParse.Value).national,
        address = (parsedAddress as OptionalText.Value).text,
    )
}
