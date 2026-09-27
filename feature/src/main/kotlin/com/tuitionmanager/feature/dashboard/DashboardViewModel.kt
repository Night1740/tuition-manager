package com.tuitionmanager.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuitionmanager.core.domain.error.DataResult
import com.tuitionmanager.core.domain.repository.BatchRepository
import com.tuitionmanager.core.domain.repository.InstituteRepository
import com.tuitionmanager.core.domain.repository.StudentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

sealed interface DashboardUiState {
    data object Loading : DashboardUiState

    data class Ready(
        val instituteName: String,
        val studentCount: Int,
        val batchCount: Int,
    ) : DashboardUiState {
        val studentsEmpty: Boolean get() = studentCount == 0
    }

    data object Error : DashboardUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    institutes: InstituteRepository,
    students: StudentRepository,
    batches: BatchRepository,
) : ViewModel() {
    private val attempts = MutableStateFlow(0)

    val state: StateFlow<DashboardUiState> = attempts
        .flatMapLatest {
            institutes.observe().flatMapLatest { instituteResult ->
                when (instituteResult) {
                    is DataResult.Failure -> flowOf(DashboardUiState.Error)
                    is DataResult.Success -> {
                        val institute = instituteResult.value
                        if (institute == null) {
                            flowOf(DashboardUiState.Error)
                        } else {
                            combine(
                                students.observeActive(institute.id),
                                batches.observeActive(institute.id),
                            ) { studentResult, batchResult ->
                                if (studentResult is DataResult.Success && batchResult is DataResult.Success) {
                                    DashboardUiState.Ready(
                                        instituteName = institute.name,
                                        studentCount = studentResult.value.size,
                                        batchCount = batchResult.value.size,
                                    )
                                } else {
                                    DashboardUiState.Error
                                }
                            }
                        }
                    }
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DashboardUiState.Loading,
        )

    fun retry() {
        attempts.update { it + 1 }
    }
}
