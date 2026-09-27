package com.tuitionmanager.feature.batches

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuitionmanager.core.domain.dispatch.DispatcherProvider
import com.tuitionmanager.core.domain.error.DataResult
import com.tuitionmanager.core.domain.model.Batch
import com.tuitionmanager.core.domain.model.Enrollment
import com.tuitionmanager.core.domain.repository.BatchRepository
import com.tuitionmanager.core.domain.repository.StudentBatchRepository
import com.tuitionmanager.core.domain.time.LocalCalendar
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BatchStudentLine(
    val assignmentId: String,
    val name: String,
    val code: String,
)

sealed interface BatchDetailsUiState {
    data object Loading : BatchDetailsUiState

    data object Error : BatchDetailsUiState

    data class Ready(
        val name: String,
        val subject: String,
        val schedule: String,
        val room: String?,
        val occupancy: String,
        val capacity: Int,
        val archived: Boolean,
        val students: List<BatchStudentLine>,
        val working: Boolean,
        val actionError: Boolean,
    ) : BatchDetailsUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BatchDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val batches: BatchRepository,
    private val studentBatches: StudentBatchRepository,
    private val calendar: LocalCalendar,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {
    private val batchId: String? = savedStateHandle.get<String>("batchId")
    private val attempts = MutableStateFlow(0)
    private val working = MutableStateFlow(false)
    private val actionError = MutableStateFlow(false)

    val state: StateFlow<BatchDetailsUiState> = combine(
        attempts.flatMapLatest { observeBatch() },
        working,
        actionError,
    ) { base, busy, failed ->
        if (base is BatchDetailsUiState.Ready) {
            base.copy(working = busy, actionError = failed)
        } else {
            base
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BatchDetailsUiState.Loading,
    )

    fun retry() {
        actionError.value = false
        attempts.update { it + 1 }
    }

    fun archive() {
        val id = batchId ?: return
        mutate { batches.archive(id, calendar.today()) }
    }

    fun restore() {
        val id = batchId ?: return
        mutate { batches.restore(id) }
    }

    fun remove(assignmentId: String) {
        if (working.value) return
        working.value = true
        actionError.value = false
        viewModelScope.launch(dispatchers.io) {
            val result = studentBatches.end(assignmentId, calendar.today())
            working.value = false
            if (result is DataResult.Failure) actionError.value = true
        }
    }

    private fun mutate(block: suspend () -> DataResult<Batch>) {
        if (working.value) return
        working.value = true
        actionError.value = false
        viewModelScope.launch(dispatchers.io) {
            val result = block()
            working.value = false
            if (result is DataResult.Failure) actionError.value = true
        }
    }

    private fun observeBatch(): Flow<BatchDetailsUiState> {
        val id = batchId ?: return flowOf(BatchDetailsUiState.Error)
        return batches.observeOne(id).flatMapLatest { batchResult ->
            when (batchResult) {
                is DataResult.Failure -> flowOf(BatchDetailsUiState.Error)
                is DataResult.Success -> studentBatches.observeActiveEnrollments(id).flatMapLatest { enrollmentResult ->
                    flowOf(
                        when (enrollmentResult) {
                            is DataResult.Failure -> BatchDetailsUiState.Error
                            is DataResult.Success -> batchResult.value.toReady(enrollmentResult.value)
                        },
                    )
                }
            }
        }
    }
}

private fun Batch.toReady(enrollments: List<Enrollment>): BatchDetailsUiState.Ready {
    val lines = enrollments
        .sortedBy { it.student.name.lowercase() }
        .map { enrollment ->
            BatchStudentLine(
                assignmentId = enrollment.assignment.id,
                name = enrollment.student.name,
                code = enrollment.student.studentCode,
            )
        }
    return BatchDetailsUiState.Ready(
        name = name,
        subject = subject,
        schedule = formatSchedule(daysOfWeek, startMinute, endMinute),
        room = room,
        occupancy = formatOccupancy(lines.size, capacity),
        capacity = capacity,
        archived = isArchived,
        students = lines,
        working = false,
        actionError = false,
    )
}
