package com.tuitionmanager.feature.students

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuitionmanager.core.domain.dispatch.DispatcherProvider
import com.tuitionmanager.core.domain.error.DataResult
import com.tuitionmanager.core.domain.model.Batch
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
    val name: String,
    val subject: String,
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
        val working: Boolean,
        val actionError: Boolean,
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

    val state: StateFlow<StudentDetailsUiState> = combine(
        attempts.flatMapLatest { observeStudent() },
        working,
        actionError,
    ) { base, busy, failed ->
        if (base is StudentDetailsUiState.Ready) {
            base.copy(working = busy, actionError = failed)
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
                is DataResult.Success -> studentBatches.observeOpenBatches(id).map { batchResult ->
                    when (batchResult) {
                        is DataResult.Failure -> StudentDetailsUiState.Error
                        is DataResult.Success -> studentResult.value.toReady(batchResult.value)
                    }
                }
            }
        }
    }
}

private fun Student.toReady(open: List<Batch>) = StudentDetailsUiState.Ready(
    name = name,
    code = studentCode,
    guardianName = guardianName,
    guardianPhone = guardianPhone,
    phone = phone,
    photoUri = photoUri,
    admissionDate = admissionDate,
    notes = notes,
    archived = isArchived,
    batches = open.map { StudentBatchLine(name = it.name, subject = it.subject) },
    working = false,
    actionError = false,
)
