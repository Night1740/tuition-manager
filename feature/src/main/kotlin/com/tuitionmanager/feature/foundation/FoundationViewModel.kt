package com.tuitionmanager.feature.foundation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuitionmanager.core.domain.error.DataResult
import com.tuitionmanager.core.domain.repository.InstituteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FoundationViewModel @Inject constructor(
    institutes: InstituteRepository,
) : ViewModel() {
    private val attempts = MutableStateFlow(0)

    val state: StateFlow<FoundationUiState> = attempts
        .flatMapLatest { institutes.observe() }
        .map { result ->
            when (result) {
                is DataResult.Success -> FoundationUiState.Ready
                is DataResult.Failure -> FoundationUiState.Error
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = FoundationUiState.Loading,
        )

    fun retry() {
        attempts.update { it + 1 }
    }
}

sealed interface FoundationUiState {
    data object Loading : FoundationUiState

    data object Ready : FoundationUiState

    data object Error : FoundationUiState
}
