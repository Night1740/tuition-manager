package com.tuitionmanager.feature.batches

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
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
import java.time.DayOfWeek

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchFormScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: BatchFormViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.start()
    }
    LaunchedEffect(state.saved) {
        if (state.saved) onSaved()
    }
    var confirmDiscard by remember { mutableStateOf(false) }
    var clockTarget by remember { mutableStateOf<ClockTarget?>(null) }
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
                text = stringResource(if (state.editing) R.string.batch_edit_title else R.string.batch_add_title),
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
                        text = stringResource(R.string.batch_error_load),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                    WideButton(stringResource(R.string.start_retry), viewModel::retry)
                }
                else -> BatchFormFields(
                    state = state,
                    viewModel = viewModel,
                    onPickStart = { clockTarget = ClockTarget.Start },
                    onPickEnd = { clockTarget = ClockTarget.End },
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
    val target = clockTarget
    if (target != null && !state.loading) {
        ClockDialog(
            minute = if (target == ClockTarget.Start) state.startMinute else state.endMinute,
            onDismiss = { clockTarget = null },
            onConfirm = { minute ->
                clockTarget = null
                if (target == ClockTarget.Start) viewModel.onStartMinute(minute) else viewModel.onEndMinute(minute)
            },
        )
    }
}

private enum class ClockTarget { Start, End }

@Composable
private fun BatchFormFields(
    state: BatchFormUiState,
    viewModel: BatchFormViewModel,
    onPickStart: () -> Unit,
    onPickEnd: () -> Unit,
) {
    FormField(
        value = state.name,
        onValueChange = viewModel::onName,
        label = stringResource(R.string.batch_name),
        error = state.nameError?.let { invalidText(it) },
    )
    FormField(
        value = state.subject,
        onValueChange = viewModel::onSubject,
        label = stringResource(R.string.batch_subject),
        error = state.subjectError?.let { invalidText(it) },
    )
    Text(text = stringResource(R.string.batch_days), style = MaterialTheme.typography.bodyLarge)
    DayOfWeek.entries.forEach { day ->
        FilterChip(
            selected = day in state.days,
            onClick = { viewModel.onToggleDay(day) },
            label = { Text(dayLabel(day), style = MaterialTheme.typography.bodyLarge) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        )
    }
    if (state.daysError != null) {
        Text(
            text = invalidText(state.daysError),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
        )
    }
    WideButton(
        label = stringResource(R.string.batch_start_time, formatClock(state.startMinute)),
        onClick = onPickStart,
        enabled = !state.saving,
    )
    WideButton(
        label = stringResource(R.string.batch_end_time, formatClock(state.endMinute)),
        onClick = onPickEnd,
        enabled = !state.saving,
    )
    if (state.timeError != null) {
        Text(
            text = invalidText(state.timeError),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
        )
    }
    FormField(
        value = state.room,
        onValueChange = viewModel::onRoom,
        label = stringResource(R.string.batch_room),
        error = state.roomError?.let { invalidText(it) },
    )
    FormField(
        value = state.capacity,
        onValueChange = viewModel::onCapacity,
        label = stringResource(R.string.batch_capacity),
        error = state.capacityError?.let { invalidText(it) },
        keyboardType = KeyboardType.Number,
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
        onClick = viewModel::save,
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
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        isError = error != null,
        supportingText = error?.let { message -> { Text(message) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClockDialog(
    minute: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val pickerState = rememberTimePickerState(
        initialHour = minute / 60,
        initialMinute = minute % 60,
        is24Hour = false,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            DialogButton(stringResource(R.string.student_date_ok)) {
                onConfirm(pickerState.hour * 60 + pickerState.minute)
            }
        },
        dismissButton = {
            DialogButton(stringResource(R.string.student_cancel), onDismiss)
        },
        text = { TimePicker(state = pickerState) },
    )
}

@Composable
private fun invalidText(code: InvalidCode): String = when (code) {
    InvalidCode.BlankBatchName -> stringResource(R.string.batch_error_name)
    InvalidCode.NameTooLong -> stringResource(R.string.batch_error_name_long)
    InvalidCode.BlankSubject -> stringResource(R.string.batch_error_subject)
    InvalidCode.SubjectTooLong -> stringResource(R.string.batch_error_subject_long)
    InvalidCode.NoDaysSelected -> stringResource(R.string.batch_error_days)
    InvalidCode.EndNotAfterStart, InvalidCode.InvalidSchedule -> stringResource(R.string.batch_error_time)
    InvalidCode.RoomTooLong -> stringResource(R.string.batch_error_room)
    InvalidCode.InvalidCapacity -> stringResource(R.string.batch_error_capacity)
    else -> stringResource(R.string.student_error_save)
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
