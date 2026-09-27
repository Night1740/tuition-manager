package com.tuitionmanager.feature.students

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuitionmanager.core.domain.dispatch.DispatcherProvider
import com.tuitionmanager.core.domain.error.DataError
import com.tuitionmanager.core.domain.error.DataResult
import com.tuitionmanager.core.domain.model.Batch
import com.tuitionmanager.core.domain.model.OpenAssignment
import com.tuitionmanager.core.domain.model.Student
import com.tuitionmanager.core.domain.repository.StudentBatchRepository
import com.tuitionmanager.core.domain.repository.StudentRepository
import com.tuitionmanager.core.domain.time.LocalCalendar
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StudentBatchLine(
    val assignmentId: String,
    val batchId: String,
    val name: String,
    val subject: String,
)

data class BatchChoice(
    val id: String,
    val name: String,
    val subject: String,
)

sealed interface StudentBatchAction {
    data class Add(val batchId: String) : StudentBatchAction

    data class Move(val fromBatchId: String, val toBatchId: String) : StudentBatchAction
}

data class StudentCapacityPrompt(
    val enrolled: Int,
    val adding: Int,
    val capacity: Int,
    val action: StudentBatchAction,
)

sealed interface StudentDetailsUiState {
    data object Loading : StudentDetailsUiState

    data object Error : StudentDetailsUiState

    data class Ready(
        val name: String,
        val code: String,
        val guardianName: String?,
        val guardianPhone: String?,
        val phone: String?,
        val photoUri: String?,
        val admissionDate: LocalDate,
        val notes: String?,
        val archived: Boolean,
        val batches: List<StudentBatchLine>,
        val availableBatches: List<BatchChoice>,
        val working: Boolean,
        val actionError: Boolean,
        val capacityPrompt: StudentCapacityPrompt?,
    ) : StudentDetailsUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StudentDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val students: StudentRepository,
    private val studentBatches: StudentBatchRepository,
    private val calendar: LocalCalendar,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {
    private val studentId: String? = savedStateHandle.get<String>("studentId")
    private val attempts = MutableStateFlow(0)
    private val working = MutableStateFlow(false)
    private val actionError = MutableStateFlow(false)
    private val capacityPrompt = MutableStateFlow<StudentCapacityPrompt?>(null)

    val state: StateFlow<StudentDetailsUiState> = combine(
        attempts.flatMapLatest { observeStudent() },
        working,
        actionError,
        capacityPrompt,
    ) { base, busy, failed, prompt ->
        if (base is StudentDetailsUiState.Ready) {
            base.copy(working = busy, actionError = failed, capacityPrompt = prompt)
        } else {
            base
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = StudentDetailsUiState.Loading,
    )

    fun retry() {
        actionError.value = false
        attempts.update { it + 1 }
    }

    fun archive() = mutate { id -> students.archive(id, calendar.today()) }

    fun restore() = mutate { id -> students.restore(id) }

    fun addToBatch(batchId: String) = runAction(StudentBatchAction.Add(batchId), allowOverCapacity = false)

    fun move(fromBatchId: String, toBatchId: String) =
        runAction(StudentBatchAction.Move(fromBatchId, toBatchId), allowOverCapacity = false)

    fun confirmCapacity() {
        val prompt = capacityPrompt.value ?: return
        runAction(prompt.action, allowOverCapacity = true)
    }

    fun dismissCapacity() {
        capacityPrompt.value = null
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

    private fun runAction(action: StudentBatchAction, allowOverCapacity: Boolean) {
        val id = studentId ?: return
        if (working.value) return
        working.value = true
        actionError.value = false
        capacityPrompt.value = null
        viewModelScope.launch(dispatchers.io) {
            val today = calendar.today()
            val result = when (action) {
                is StudentBatchAction.Add -> studentBatches.assign(
                    studentId = id,
                    batchId = action.batchId,
                    startedOn = today,
                    allowOverCapacity = allowOverCapacity,
                )
                is StudentBatchAction.Move -> studentBatches.move(
                    studentId = id,
                    fromBatchId = action.fromBatchId,
                    toBatchId = action.toBatchId,
                    on = today,
                    allowOverCapacity = allowOverCapacity,
                )
            }
            working.value = false
            when (result) {
                is DataResult.Success -> Unit
                is DataResult.Failure -> {
                    val error = result.error
                    if (error is DataError.OverCapacity) {
                        capacityPrompt.value = StudentCapacityPrompt(
                            enrolled = error.enrolled,
                            adding = error.adding,
                            capacity = error.capacity,
                            action = action,
                        )
                    } else {
                        actionError.value = true
                    }
                }
            }
        }
    }

    private fun mutate(block: suspend (String) -> DataResult<Student>) {
        val id = studentId ?: return
        if (working.value) return
        working.value = true
        actionError.value = false
        viewModelScope.launch(dispatchers.io) {
            val result = block(id)
            working.value = false
            if (result is DataResult.Failure) actionError.value = true
        }
    }

    private fun observeStudent(): Flow<StudentDetailsUiState> {
        val id = studentId ?: return flowOf(StudentDetailsUiState.Error)
        return students.observeOne(id).flatMapLatest { studentResult ->
            when (studentResult) {
                is DataResult.Failure -> flowOf(StudentDetailsUiState.Error)
                is DataResult.Success -> combine(
                    studentBatches.observeOpenAssignments(id),
                    studentBatches.observeAvailableBatches(id),
                ) { openResult, availableResult ->
                    when {
                        openResult is DataResult.Failure || availableResult is DataResult.Failure ->
                            StudentDetailsUiState.Error
                        openResult is DataResult.Success && availableResult is DataResult.Success ->
                            studentResult.value.toReady(openResult.value, availableResult.value)
                        else -> StudentDetailsUiState.Error
                    }
                }
            }
        }
    }
}

private fun Student.toReady(open: List<OpenAssignment>, available: List<Batch>) = StudentDetailsUiState.Ready(
    name = name,
    code = studentCode,
    guardianName = guardianName,
    guardianPhone = guardianPhone,
    phone = phone,
    photoUri = photoUri,
    admissionDate = admissionDate,
    notes = notes,
    archived = isArchived,
    batches = open.map { assignment ->
        StudentBatchLine(
            assignmentId = assignment.assignmentId,
            batchId = assignment.batch.id,
            name = assignment.batch.name,
            subject = assignment.batch.subject,
        )
    },
    availableBatches = available.map { batch ->
        BatchChoice(id = batch.id, name = batch.name, subject = batch.subject)
    },
    working = false,
    actionError = false,
    capacityPrompt = null,
)
