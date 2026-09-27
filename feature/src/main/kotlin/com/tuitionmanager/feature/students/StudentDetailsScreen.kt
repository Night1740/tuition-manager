package com.tuitionmanager.feature.students

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tuitionmanager.feature.R

@Composable
fun StudentDetailsScreen(
    onBack: () -> Unit,
    onEdit: () -> Unit,
    viewModel: StudentDetailsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmArchive by remember { mutableStateOf(false) }
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
                text = stringResource(R.string.student_details_title),
                style = MaterialTheme.typography.headlineMedium,
            )
            when (val current = state) {
                StudentDetailsUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.heightIn(min = 48.dp))
                    Text(
                        text = stringResource(R.string.start_loading),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                StudentDetailsUiState.Error -> {
                    Text(
                        text = stringResource(R.string.student_error_load),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                    WideButton(stringResource(R.string.start_retry), viewModel::retry)
                }
                is StudentDetailsUiState.Ready -> {
                    if (current.archived) {
                        Text(
                            text = stringResource(R.string.student_archived_label),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    DetailLine(stringResource(R.string.student_name), current.name)
                    DetailLine(stringResource(R.string.student_code), current.code)
                    DetailLine(stringResource(R.string.student_guardian_name), current.guardianName)
                    DetailLine(stringResource(R.string.student_guardian_phone), current.guardianPhone)
                    DetailLine(stringResource(R.string.student_phone), current.phone)
                    DetailLine(stringResource(R.string.student_photo), current.photoUri)
                    DetailLine(
                        stringResource(R.string.student_admission_date),
                        formatStudentDate(current.admissionDate),
                    )
                    DetailLine(stringResource(R.string.student_notes), current.notes)
                    Text(
                        text = stringResource(R.string.student_batches),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    if (current.batches.isEmpty()) {
                        Text(
                            text = stringResource(R.string.student_no_batches),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    } else {
                        current.batches.forEach { batch ->
                            Text(
                                text = "${batch.name} · ${batch.subject}",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                    if (current.actionError) {
                        Text(
                            text = stringResource(R.string.student_action_error),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
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
    if (confirmArchive) {
        AlertDialog(
            onDismissRequest = { confirmArchive = false },
            title = { Text(stringResource(R.string.student_archive_title)) },
            text = {
                Text(
                    text = stringResource(R.string.student_archive_body),
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
