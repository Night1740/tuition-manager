package com.tuitionmanager.feature.batches

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuitionmanager.core.domain.dispatch.DispatcherProvider
import com.tuitionmanager.core.domain.error.DataError
import com.tuitionmanager.core.domain.error.DataResult
import com.tuitionmanager.core.domain.model.Student
import com.tuitionmanager.core.domain.repository.StudentBatchRepository
import com.tuitionmanager.core.domain.time.LocalCalendar
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SelectableStudent(
    val id: String,
    val name: String,
    val code: String,
    val phone: String?,
    val selected: Boolean,
)

data class AddStudentsCapacityPrompt(
    val enrolled: Int,
    val adding: Int,
    val capacity: Int,
    val studentIds: List<String>,
)

sealed interface AddStudentsUiState {
    data object Loading : AddStudentsUiState

    data object Error : AddStudentsUiState

    data class Ready(
        val query: String,
        val rows: List<SelectableStudent>,
        val selectedCount: Int,
        val saving: Boolean,
        val actionError: Boolean,
        val saved: Boolean,
        val capacityPrompt: AddStudentsCapacityPrompt?,
    ) : AddStudentsUiState {
        val noStudents: Boolean get() = rows.isEmpty() && query.isBlank()
        val noResults: Boolean get() = rows.isEmpty() && query.isNotBlank()
    }
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class AddStudentsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val studentBatches: StudentBatchRepository,
    private val calendar: LocalCalendar,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {
    private val batchId: String? = savedStateHandle.get<String>("batchId")
    private val query = MutableStateFlow("")
    private val selected = MutableStateFlow<Set<String>>(emptySet())
    private val attempts = MutableStateFlow(0)
    private val saving = MutableStateFlow(false)
    private val actionError = MutableStateFlow(false)
    private val saved = MutableStateFlow(false)
    private val capacityPrompt = MutableStateFlow<AddStudentsCapacityPrompt?>(null)

    internal var searchDebounceMillis: Long = 300L

    val queryText: StateFlow<String> = query.asStateFlow()

    val state: StateFlow<AddStudentsUiState> = combine(
        query.debounce { text ->
            val wait = searchDebounceMillis
            if (wait == 0L || text.isBlank()) 0L else wait
        },
        attempts,
    ) { text, _ -> text }
        .flatMapLatest { text ->
            val id = batchId ?: return@flatMapLatest flowOf(AddStudentsUiState.Error)
            studentBatches.observeAssignableStudents(id, text).flatMapLatest { result ->
                combine(selected, saving, actionError, saved, capacityPrompt) { picked, busy, failed, done, prompt ->
                    when (result) {
                        is DataResult.Failure -> AddStudentsUiState.Error
                        is DataResult.Success -> result.value.toReady(
                            query = text.trim(),
                            picked = picked,
                            saving = busy,
                            actionError = failed,
                            saved = done,
                            prompt = prompt,
                        )
                    }
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AddStudentsUiState.Loading,
        )

    fun onQuery(value: String) {
        query.value = value
    }

    fun onToggle(studentId: String) {
        selected.update { current ->
            if (studentId in current) current - studentId else current + studentId
        }
    }

    fun retry() {
        actionError.value = false
        attempts.update { it + 1 }
    }

    fun save() {
        val ids = selected.value.toList()
        if (ids.isEmpty()) return
        write(ids, allowOverCapacity = false)
    }

    fun confirmCapacity() {
        val prompt = capacityPrompt.value ?: return
        write(prompt.studentIds, allowOverCapacity = true)
    }

    fun dismissCapacity() {
        capacityPrompt.value = null
    }

    private fun write(studentIds: List<String>, allowOverCapacity: Boolean) {
        val id = batchId ?: return
        if (studentIds.isEmpty() || saving.value) return
        saving.value = true
        actionError.value = false
        capacityPrompt.value = null
        viewModelScope.launch(dispatchers.io) {
            when (
                val result = studentBatches.assignMany(
                    studentIds = studentIds,
                    batchId = id,
                    startedOn = calendar.today(),
                    allowOverCapacity = allowOverCapacity,
                )
            ) {
                is DataResult.Success -> {
                    saving.value = false
                    saved.value = true
                }
                is DataResult.Failure -> {
                    saving.value = false
                    val error = result.error
                    if (error is DataError.OverCapacity) {
                        capacityPrompt.value = AddStudentsCapacityPrompt(
                            enrolled = error.enrolled,
                            adding = error.adding,
                            capacity = error.capacity,
                            studentIds = studentIds,
                        )
                    } else {
                        actionError.value = true
                    }
                }
            }
        }
    }
}

private fun List<Student>.toReady(
    query: String,
    picked: Set<String>,
    saving: Boolean,
    actionError: Boolean,
    saved: Boolean,
    prompt: AddStudentsCapacityPrompt?,
) = AddStudentsUiState.Ready(
    query = query,
    rows = map { student ->
        SelectableStudent(
            id = student.id,
            name = student.name,
            code = student.studentCode,
            phone = student.primaryPhone,
            selected = student.id in picked,
        )
    },
    selectedCount = picked.size,
    saving = saving,
    actionError = actionError,
    saved = saved,
    capacityPrompt = prompt,
)
