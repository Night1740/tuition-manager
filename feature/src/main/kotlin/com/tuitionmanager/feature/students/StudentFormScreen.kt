package com.tuitionmanager.feature.students

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tuitionmanager.core.domain.error.InvalidCode
import com.tuitionmanager.feature.R

@Composable
fun StudentFormScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: StudentFormViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.start()
    }
    LaunchedEffect(state.saved) {
        if (state.saved) onSaved()
    }
    var confirmDiscard by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    val leave = {
        if (state.dirty) confirmDiscard = true else onBack()
    }
    BackHandler(enabled = state.dirty) { confirmDiscard = true }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            WideButton(stringResource(R.string.nav_back), leave)
            Text(
                text = stringResource(
                    if (state.editing) R.string.student_edit_title else R.string.student_add_title,
                ),
                style = MaterialTheme.typography.headlineMedium,
            )
            when {
                state.loading -> {
                    CircularProgressIndicator(modifier = Modifier.heightIn(min = 48.dp))
                    Text(
                        text = stringResource(R.string.start_loading),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                state.loadError -> {
                    Text(
                        text = stringResource(R.string.student_error_load),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                    WideButton(stringResource(R.string.start_retry), viewModel::retry)
                }
                else -> StudentFormFields(
                    state = state,
                    onName = viewModel::onName,
                    onCode = viewModel::onCode,
                    onGuardianName = viewModel::onGuardianName,
                    onGuardianPhone = viewModel::onGuardianPhone,
                    onPhone = viewModel::onPhone,
                    onNotes = viewModel::onNotes,
                    onPickDate = { showDatePicker = true },
                    onSave = viewModel::save,
                )
            }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.student_discard_title)) },
            text = {
                Text(
                    text = stringResource(R.string.student_discard_body),
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            confirmButton = {
                DialogButton(stringResource(R.string.student_discard_confirm)) {
                    confirmDiscard = false
                    onBack()
                }
            },
            dismissButton = {
                DialogButton(stringResource(R.string.student_discard_keep)) {
                    confirmDiscard = false
                }
            },
        )
    }

    val admissionDate = state.admissionDate
    if (showDatePicker && admissionDate != null) {
        AdmissionDatePicker(
            initial = admissionDate,
            onDismiss = { showDatePicker = false },
            onDate = { picked ->
                viewModel.onAdmissionDate(picked)
                showDatePicker = false
            },
        )
    }
}

@Composable
private fun StudentFormFields(
    state: StudentFormUiState,
    onName: (String) -> Unit,
    onCode: (String) -> Unit,
    onGuardianName: (String) -> Unit,
    onGuardianPhone: (String) -> Unit,
    onPhone: (String) -> Unit,
    onNotes: (String) -> Unit,
    onPickDate: () -> Unit,
    onSave: () -> Unit,
) {
    val codeMessage = when {
        state.codeTaken -> stringResource(R.string.student_error_code_taken)
        state.codeError != null -> invalidText(state.codeError)
        else -> null
    }
    FormField(
        value = state.name,
        onValueChange = onName,
        label = stringResource(R.string.student_name),
        error = state.nameError?.let { invalidText(it) },
    )
    FormField(
        value = state.code,
        onValueChange = onCode,
        label = stringResource(R.string.student_code),
        error = codeMessage,
    )
    FormField(
        value = state.guardianName,
        onValueChange = onGuardianName,
        label = stringResource(R.string.student_guardian_name),
        error = state.guardianNameError?.let { invalidText(it) },
    )
    FormField(
        value = state.guardianPhone,
        onValueChange = onGuardianPhone,
        label = stringResource(R.string.student_guardian_phone),
        error = state.guardianPhoneError?.let { invalidText(it) },
        keyboardType = KeyboardType.Phone,
    )
    FormField(
        value = state.phone,
        onValueChange = onPhone,
        label = stringResource(R.string.student_phone),
        error = state.phoneError?.let { invalidText(it) },
        keyboardType = KeyboardType.Phone,
    )
    if (state.contactError != null) {
        Text(
            text = invalidText(state.contactError),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
        )
    }
    val admissionDate = state.admissionDate
    if (admissionDate != null) {
        Text(
            text = stringResource(R.string.student_admission_date),
            style = MaterialTheme.typography.bodyLarge,
        )
        WideButton(
            label = formatStudentDate(admissionDate),
            onClick = onPickDate,
            enabled = !state.saving,
        )
    }
    FormField(
        value = state.notes,
        onValueChange = onNotes,
        label = stringResource(R.string.student_notes),
        error = state.notesError?.let { invalidText(it) },
        singleLine = false,
    )
    if (state.storageError) {
        Text(
            text = stringResource(R.string.student_error_save),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
        )
    }
    WideButton(
        label = stringResource(if (state.saving) R.string.student_saving else R.string.student_save),
        onClick = onSave,
        enabled = !state.saving,
    )
}

@Composable
private fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: String?,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        isError = error != null,
        supportingText = error?.let { message -> { Text(message) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 2,
        textStyle = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdmissionDatePicker(
    initial: java.time.LocalDate,
    onDismiss: () -> Unit,
    onDate: (java.time.LocalDate) -> Unit,
) {
    val pickerState = rememberDatePickerState(initialSelectedDateMillis = localDateToUtcMillis(initial))
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            DialogButton(stringResource(R.string.student_date_ok)) {
                val millis = pickerState.selectedDateMillis
                if (millis != null) onDate(utcMillisToLocalDate(millis)) else onDismiss()
            }
        },
        dismissButton = {
            DialogButton(stringResource(R.string.student_cancel), onDismiss)
        },
    ) {
        DatePicker(state = pickerState)
    }
}

@Composable
private fun invalidText(code: InvalidCode): String = when (code) {
    InvalidCode.BlankName -> stringResource(R.string.student_error_name)
    InvalidCode.NameTooLong -> stringResource(R.string.student_error_name_long)
    InvalidCode.BlankStudentCode -> stringResource(R.string.student_error_code)
    InvalidCode.InvalidStudentCode -> stringResource(R.string.student_error_code_invalid)
    InvalidCode.InvalidPhone -> stringResource(R.string.student_error_phone)
    InvalidCode.MissingContactPhone -> stringResource(R.string.student_error_contact)
    InvalidCode.NotesTooLong -> stringResource(R.string.student_error_notes)
    else -> stringResource(R.string.student_error_save)
}

@Composable
private fun WideButton(label: String, onClick: () -> Unit, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
    ) {
        Text(text = label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun DialogButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.heightIn(min = 48.dp),
    ) {
        Text(text = label, style = MaterialTheme.typography.labelLarge)
    }
}
