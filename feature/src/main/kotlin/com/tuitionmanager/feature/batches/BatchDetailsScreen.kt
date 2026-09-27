package com.tuitionmanager.feature.batches

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tuitionmanager.feature.R

@Composable
fun BatchDetailsScreen(
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onAddStudents: () -> Unit,
    viewModel: BatchDetailsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmArchive by remember { mutableStateOf(false) }
    var removeTarget by remember { mutableStateOf<BatchStudentLine?>(null) }
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            WideButton(stringResource(R.string.nav_back), onBack)
            Text(
                text = stringResource(R.string.batch_details_title),
                style = MaterialTheme.typography.headlineMedium,
            )
            when (val current = state) {
                BatchDetailsUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.heightIn(min = 48.dp))
                    Text(
                        text = stringResource(R.string.start_loading),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                BatchDetailsUiState.Error -> {
                    Text(
                        text = stringResource(R.string.batch_error_load),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                    WideButton(stringResource(R.string.start_retry), viewModel::retry)
                }
                is BatchDetailsUiState.Ready -> {
                    if (current.archived) {
                        Text(
                            text = stringResource(R.string.student_archived_label),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    DetailLine(stringResource(R.string.batch_name), current.name)
                    DetailLine(stringResource(R.string.batch_subject), current.subject)
                    DetailLine(stringResource(R.string.batch_schedule), current.schedule)
                    DetailLine(stringResource(R.string.batch_room), current.room)
                    DetailLine(stringResource(R.string.batch_students), current.occupancy)
                    if (current.students.isEmpty()) {
                        Text(
                            text = stringResource(R.string.batch_no_students),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    } else {
                        current.students.forEach { student ->
                            Text(
                                text = "${student.name} · ${student.code}",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            WideButton(
                                label = stringResource(R.string.batch_remove_student),
                                onClick = { removeTarget = student },
                                enabled = !current.working,
                            )
                        }
                    }
                    if (current.actionError) {
                        Text(
                            text = stringResource(R.string.batch_action_error),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    if (!current.archived) {
                        WideButton(
                            label = stringResource(R.string.batch_add_students),
                            onClick = onAddStudents,
                            enabled = !current.working,
                        )
                    }
                    WideButton(
                        label = stringResource(R.string.student_edit),
                        onClick = onEdit,
                        enabled = !current.working,
                    )
                    if (current.archived) {
                        WideButton(
                            label = stringResource(R.string.student_restore),
                            onClick = viewModel::restore,
                            enabled = !current.working,
                        )
                    } else {
                        WideButton(
                            label = stringResource(R.string.student_archive),
                            onClick = { confirmArchive = true },
                            enabled = !current.working,
                        )
                    }
                }
            }
        }
    }
    val ready = state as? BatchDetailsUiState.Ready
    if (confirmArchive && ready != null) {
        val count = ready.students.size
        AlertDialog(
            onDismissRequest = { confirmArchive = false },
            title = { Text(stringResource(R.string.batch_archive_title)) },
            text = {
                Text(
                    text = pluralStringResource(R.plurals.batch_archive_body, count, count),
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            confirmButton = {
                DialogButton(stringResource(R.string.student_archive_confirm)) {
                    confirmArchive = false
                    viewModel.archive()
                }
            },
            dismissButton = {
                DialogButton(stringResource(R.string.student_cancel)) {
                    confirmArchive = false
                }
            },
        )
    }
    val removing = removeTarget
    if (removing != null) {
        AlertDialog(
            onDismissRequest = { removeTarget = null },
            title = { Text(stringResource(R.string.batch_remove_student)) },
            text = {
                Text(
                    text = stringResource(R.string.batch_remove_student_body, removing.name),
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            confirmButton = {
                DialogButton(stringResource(R.string.student_remove_confirm)) {
                    removeTarget = null
                    viewModel.remove(removing.assignmentId)
                }
            },
            dismissButton = {
                DialogButton(stringResource(R.string.student_cancel)) {
                    removeTarget = null
                }
            },
        )
    }
}

@Composable
private fun DetailLine(label: String, value: String?) {
    Text(text = label, style = MaterialTheme.typography.labelLarge)
    Text(
        text = value?.takeIf { it.isNotBlank() } ?: stringResource(R.string.student_none),
        style = MaterialTheme.typography.bodyLarge,
    )
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
