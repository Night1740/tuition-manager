package com.tuitionmanager.feature.batches

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tuitionmanager.feature.R

@Composable
fun AddStudentsScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: AddStudentsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val query by viewModel.queryText.collectAsStateWithLifecycle()
    LaunchedEffect(state) {
        if (state is AddStudentsUiState.Ready && (state as AddStudentsUiState.Ready).saved) onSaved()
    }
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            WideButton(stringResource(R.string.nav_back), onBack)
            Text(
                text = stringResource(R.string.batch_add_students),
                style = MaterialTheme.typography.headlineMedium,
            )
            when (val current = state) {
                AddStudentsUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.heightIn(min = 48.dp))
                    Text(
                        text = stringResource(R.string.start_loading),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                AddStudentsUiState.Error -> {
                    Text(
                        text = stringResource(R.string.batch_list_error),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                    WideButton(stringResource(R.string.start_retry), viewModel::retry)
                }
                is AddStudentsUiState.Ready -> {
                    OutlinedTextField(
                        value = query,
                        onValueChange = viewModel::onQuery,
                        label = { Text(stringResource(R.string.student_search)) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (current.noStudents) {
                            Text(
                                text = stringResource(R.string.batch_no_students_to_add),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                        if (current.noResults) {
                            Text(
                                text = stringResource(R.string.student_no_results),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                        current.rows.forEach { row ->
                            StudentPickRow(row = row, onToggle = { viewModel.onToggle(row.id) })
                        }
                    }
                    if (current.actionError) {
                        Text(
                            text = stringResource(R.string.batch_action_error),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    WideButton(
                        label = stringResource(
                            if (current.saving) R.string.student_saving else R.string.batch_add_selected,
                        ),
                        onClick = viewModel::save,
                        enabled = !current.saving && current.selectedCount > 0,
                    )
                }
            }
        }
    }
    val prompt = (state as? AddStudentsUiState.Ready)?.capacityPrompt
    if (prompt != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissCapacity,
            title = { Text(stringResource(R.string.batch_over_capacity_title)) },
            text = {
                Text(
                    text = stringResource(
                        R.string.batch_over_capacity_body,
                        prompt.capacity,
                        prompt.enrolled,
                        prompt.adding,
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            confirmButton = {
                DialogButton(stringResource(R.string.batch_over_capacity_confirm), viewModel::confirmCapacity)
            },
            dismissButton = {
                DialogButton(stringResource(R.string.student_cancel), viewModel::dismissCapacity)
            },
        )
    }
}

@Composable
private fun StudentPickRow(row: SelectableStudent, onToggle: () -> Unit) {
    Button(
        onClick = onToggle,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Checkbox(checked = row.selected, onCheckedChange = { onToggle() })
            Column(modifier = Modifier.weight(1f)) {
                Text(text = row.name, style = MaterialTheme.typography.bodyLarge)
                Text(text = row.code, style = MaterialTheme.typography.bodyLarge)
                if (row.phone != null) {
                    Text(text = row.phone, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
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
