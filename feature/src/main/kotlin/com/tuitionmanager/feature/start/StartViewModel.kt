package com.tuitionmanager.feature.start

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

sealed interface StartUiState {
    data object Loading : StartUiState

    data object NeedsOnboarding : StartUiState

    data object Ready : StartUiState

    data object Error : StartUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StartViewModel @Inject constructor(
    institutes: InstituteRepository,
) : ViewModel() {
    private val attempts = MutableStateFlow(0)

    val state: StateFlow<StartUiState> = attempts
        .flatMapLatest { institutes.observe() }
        .map { result ->
            when (result) {
                is DataResult.Failure -> StartUiState.Error
                is DataResult.Success ->
                    if (result.value == null) StartUiState.NeedsOnboarding else StartUiState.Ready
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = StartUiState.Loading,
        )

    fun retry() {
        attempts.update { it + 1 }
    }
}
