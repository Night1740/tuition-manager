package com.tuitionmanager.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tuitionmanager.core.domain.error.InvalidCode
import com.tuitionmanager.feature.R

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.onboarding_title),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = stringResource(R.string.onboarding_subtitle),
                style = MaterialTheme.typography.bodyLarge,
            )
            LabeledField(
                value = state.className,
                onValueChange = viewModel::onClassName,
                label = stringResource(R.string.onboarding_class_name),
                error = state.classNameError,
            )
            LabeledField(
                value = state.ownerName,
                onValueChange = viewModel::onOwnerName,
                label = stringResource(R.string.onboarding_owner_name),
                error = state.ownerNameError,
            )
            LabeledField(
                value = state.phone,
                onValueChange = viewModel::onPhone,
                label = stringResource(R.string.onboarding_phone),
                error = state.phoneError,
                keyboardType = KeyboardType.Phone,
            )
            LabeledField(
                value = state.address,
                onValueChange = viewModel::onAddress,
                label = stringResource(R.string.onboarding_address),
                error = state.addressError,
                singleLine = false,
            )
            if (state.storageError) {
                Text(
                    text = stringResource(R.string.onboarding_error_save),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Button(
                onClick = viewModel::save,
                enabled = !state.saving,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Text(
                    text = stringResource(
                        if (state.saving) R.string.onboarding_saving else R.string.onboarding_save,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun LabeledField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: InvalidCode?,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        isError = error != null,
        supportingText = error?.let { code ->
            { Text(fieldErrorText(code)) }
        },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 2,
        textStyle = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun fieldErrorText(code: InvalidCode): String = when (code) {
    InvalidCode.BlankName -> stringResource(R.string.onboarding_error_class_name)
    InvalidCode.BlankOwnerName -> stringResource(R.string.onboarding_error_owner)
    InvalidCode.InvalidPhone -> stringResource(R.string.onboarding_error_phone)
    InvalidCode.AddressTooLong -> stringResource(R.string.onboarding_error_address)
    else -> stringResource(R.string.onboarding_error_save)
}
