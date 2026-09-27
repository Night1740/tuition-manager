package com.tuitionmanager.feature.students

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuitionmanager.core.domain.error.DataResult
import com.tuitionmanager.core.domain.model.Student
import com.tuitionmanager.core.domain.repository.InstituteRepository
import com.tuitionmanager.core.domain.repository.StudentRepository
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class StudentRow(
    val id: String,
    val name: String,
    val code: String,
    val phone: String?,
    val archived: Boolean,
)

sealed interface StudentListUiState {
    data object Loading : StudentListUiState

    data class Ready(
        val query: String,
        val archivedOnly: Boolean,
        val rows: List<StudentRow>,
    ) : StudentListUiState {
        val noStudents: Boolean get() = rows.isEmpty() && query.isBlank() && !archivedOnly
        val noArchived: Boolean get() = rows.isEmpty() && query.isBlank() && archivedOnly
        val noResults: Boolean get() = rows.isEmpty() && query.isNotBlank()
    }

    data object Error : StudentListUiState
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class StudentListViewModel @Inject constructor(
    institutes: InstituteRepository,
    private val students: StudentRepository,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val archivedOnly = MutableStateFlow(false)
    private val attempts = MutableStateFlow(0)

    /** Tests set this before collection. Production search waits briefly so each keystroke does not query. */
    internal var searchDebounceMillis: Long = 300L

    /** The field text, before the debounce used for the SQL query. */
    val queryText: StateFlow<String> = query.asStateFlow()

    val state: StateFlow<StudentListUiState> = combine(
        query.debounce { text ->
            val wait = searchDebounceMillis
            if (wait == 0L || text.isBlank()) 0L else wait
        },
        archivedOnly,
        attempts,
    ) { text, archived, _ -> text to archived }
        .flatMapLatest { (text, archived) ->
            institutes.observe().flatMapLatest { instituteResult ->
                when (instituteResult) {
                    is DataResult.Failure -> flowOf(StudentListUiState.Error)
                    is DataResult.Success -> {
                        val institute = instituteResult.value
                        if (institute == null) {
                            flowOf(StudentListUiState.Error)
                        } else {
                            students.observeList(institute.id, archived, text).mapRows(text, archived)
                        }
                    }
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = StudentListUiState.Loading,
        )

    fun onQuery(value: String) {
        query.value = value
    }

    fun onArchivedOnly(archived: Boolean) {
        archivedOnly.value = archived
    }

    fun retry() {
        attempts.update { it + 1 }
    }
}

private fun Flow<DataResult<List<Student>>>.mapRows(
    query: String,
    archivedOnly: Boolean,
): Flow<StudentListUiState> = map { result ->
    when (result) {
        is DataResult.Failure -> StudentListUiState.Error
        is DataResult.Success -> StudentListUiState.Ready(
            query = query.trim(),
            archivedOnly = archivedOnly,
            rows = result.value.map { student ->
                StudentRow(
                    id = student.id,
                    name = student.name,
                    code = student.studentCode,
                    phone = student.primaryPhone,
                    archived = student.isArchived,
                )
            },
        )
    }
}
