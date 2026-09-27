package com.tuitionmanager.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuitionmanager.core.domain.dispatch.DispatcherProvider
import com.tuitionmanager.core.domain.error.DataError
import com.tuitionmanager.core.domain.error.DataResult
import com.tuitionmanager.core.domain.error.InvalidCode
import com.tuitionmanager.core.domain.model.NewInstitute
import com.tuitionmanager.core.domain.repository.InstituteRepository
import com.tuitionmanager.core.domain.validation.InstituteValidation
import com.tuitionmanager.core.domain.validation.validateInstituteInput
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OnboardingUiState(
    val className: String = "",
    val ownerName: String = "",
    val phone: String = "",
    val address: String = "",
    val classNameError: InvalidCode? = null,
    val ownerNameError: InvalidCode? = null,
    val phoneError: InvalidCode? = null,
    val addressError: InvalidCode? = null,
    val saving: Boolean = false,
    val storageError: Boolean = false,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val institutes: InstituteRepository,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {
    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    fun onClassName(value: String) {
        _state.update { it.copy(className = value, classNameError = null, storageError = false) }
    }

    fun onOwnerName(value: String) {
        _state.update { it.copy(ownerName = value, ownerNameError = null, storageError = false) }
    }

    fun onPhone(value: String) {
        _state.update { it.copy(phone = value, phoneError = null, storageError = false) }
    }

    fun onAddress(value: String) {
        _state.update { it.copy(address = value, addressError = null, storageError = false) }
    }

    fun save() {
        val current = _state.value
        if (current.saving) return
        when (
            val validated = validateInstituteInput(
                current.className,
                current.ownerName,
                current.phone,
                current.address,
            )
        ) {
            is InstituteValidation.Rejected ->
                _state.update { it.withInvalid(validated.code).copy(saving = false, storageError = false) }
            is InstituteValidation.Accepted -> {
                _state.update { it.clearErrors().copy(saving = true) }
                viewModelScope.launch(dispatchers.io) {
                    val result = institutes.create(
                        NewInstitute(
                            name = validated.draft.name,
                            ownerName = validated.draft.ownerName,
                            phone = validated.draft.phone,
                            address = validated.draft.address,
                        ),
                    )
                    _state.update { state ->
                        when (result) {
                            is DataResult.Success -> state.copy(saving = false)
                            is DataResult.Failure -> state.copy(saving = false).applyFailure(result.error)
                        }
                    }
                }
            }
        }
    }

    private fun OnboardingUiState.clearErrors(): OnboardingUiState = copy(
        classNameError = null,
        ownerNameError = null,
        phoneError = null,
        addressError = null,
        storageError = false,
    )

    private fun OnboardingUiState.withInvalid(code: InvalidCode): OnboardingUiState = when (code) {
        InvalidCode.BlankName -> copy(classNameError = code)
        InvalidCode.BlankOwnerName -> copy(ownerNameError = code)
        InvalidCode.InvalidPhone -> copy(phoneError = code)
        InvalidCode.AddressTooLong -> copy(addressError = code)
        else -> copy(storageError = true)
    }

    private fun OnboardingUiState.applyFailure(error: DataError): OnboardingUiState = when (error) {
        is DataError.Invalid -> withInvalid(error.code)
        else -> copy(storageError = true)
    }
}
