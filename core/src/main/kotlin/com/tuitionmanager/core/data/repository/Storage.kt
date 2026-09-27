package com.tuitionmanager.core.data.repository

import com.tuitionmanager.core.domain.error.ConflictCode
import com.tuitionmanager.core.domain.error.DataError
import com.tuitionmanager.core.domain.error.DataResult
import com.tuitionmanager.core.logging.AppLog
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch

internal suspend fun <T> runData(
    operation: String,
    block: suspend () -> DataResult<T>,
): DataResult<T> =
    try {
        block()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        AppLog.storageFailure(operation, error)
        DataResult.Failure(error.toDataError(operation))
    }

internal fun Throwable.toDataError(operation: String): DataError {
    val messages = generateSequence(this) { it.cause }
        .mapNotNull { it.message }
        .joinToString(" | ")
    return when {
        "student_code" in messages -> DataError.Conflict(ConflictCode.DuplicateStudentCode)
        "active_slot" in messages -> DataError.Conflict(ConflictCode.DuplicateActiveAssignment)
        else -> DataError.Storage(operation)
    }
}

internal fun <T> Flow<DataResult<T>>.storageFailures(operation: String): Flow<DataResult<T>> =
    catch { error ->
        AppLog.storageFailure(operation, error)
        emit(DataResult.Failure(error.toDataError(operation)))
    }

internal fun toContainsLikePattern(raw: String): String {
    val escaped = raw.trim()
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")
    return "%$escaped%"
}
