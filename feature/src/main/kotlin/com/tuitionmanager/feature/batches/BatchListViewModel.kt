package com.tuitionmanager.feature.batches

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuitionmanager.core.domain.error.DataResult
import com.tuitionmanager.core.domain.model.BatchRoster
import com.tuitionmanager.core.domain.repository.BatchRepository
import com.tuitionmanager.core.domain.repository.InstituteRepository
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class BatchRow(
    val id: String,
    val name: String,
    val subject: String,
    val schedule: String,
    val occupancy: String,
)

sealed interface BatchListUiState {
    data object Loading : BatchListUiState

    data class Ready(
        val archivedOnly: Boolean,
        val rows: List<BatchRow>,
    ) : BatchListUiState {
        val noBatches: Boolean get() = rows.isEmpty() && !archivedOnly
        val noArchived: Boolean get() = rows.isEmpty() && archivedOnly
    }

    data object Error : BatchListUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BatchListViewModel @Inject constructor(
    institutes: InstituteRepository,
    private val batches: BatchRepository,
) : ViewModel() {
    private val archivedOnly = MutableStateFlow(false)
    private val attempts = MutableStateFlow(0)

    val state: StateFlow<BatchListUiState> = combine(archivedOnly, attempts) { archived, _ -> archived }
        .flatMapLatest { archived ->
            institutes.observe().flatMapLatest { instituteResult ->
                when (instituteResult) {
                    is DataResult.Failure -> flowOf(BatchListUiState.Error)
                    is DataResult.Success -> {
                        val institute = instituteResult.value
                        if (institute == null) {
                            flowOf(BatchListUiState.Error)
                        } else {
                            batches.observeRoster(institute.id, archived).mapRows(archived)
                        }
                    }
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = BatchListUiState.Loading,
        )

    fun onArchivedOnly(archived: Boolean) {
        archivedOnly.value = archived
    }

    fun retry() {
        attempts.update { it + 1 }
    }
}

private fun Flow<DataResult<List<BatchRoster>>>.mapRows(archivedOnly: Boolean): Flow<BatchListUiState> =
    map { result ->
        when (result) {
            is DataResult.Failure -> BatchListUiState.Error
            is DataResult.Success -> BatchListUiState.Ready(
                archivedOnly = archivedOnly,
                rows = result.value.map { roster ->
                    val batch = roster.batch
                    BatchRow(
                        id = batch.id,
                        name = batch.name,
                        subject = batch.subject,
                        schedule = formatSchedule(batch.daysOfWeek, batch.startMinute, batch.endMinute),
                        occupancy = formatOccupancy(roster.studentCount, batch.capacity),
                    )
                },
            )
        }
    }
