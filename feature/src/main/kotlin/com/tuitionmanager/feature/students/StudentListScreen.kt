package com.tuitionmanager.feature.students

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tuitionmanager.feature.R

@Composable
fun StudentListScreen(
    onAdd: () -> Unit,
    onOpen: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: StudentListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val query by viewModel.queryText.collectAsStateWithLifecycle()
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
                text = stringResource(R.string.nav_students),
                style = MaterialTheme.typography.headlineMedium,
            )
            when (val current = state) {
                StudentListUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.heightIn(min = 48.dp))
                    Text(
                        text = stringResource(R.string.start_loading),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                StudentListUiState.Error -> {
                    Text(
                        text = stringResource(R.string.student_list_error),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                    WideButton(stringResource(R.string.start_retry), viewModel::retry)
                }
                is StudentListUiState.Ready -> {
                    OutlinedTextField(
                        value = query,
                        onValueChange = viewModel::onQuery,
                        label = { Text(stringResource(R.string.student_search)) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    WideButton(
                        label = stringResource(
                            if (current.archivedOnly) {
                                R.string.list_filter_active
                            } else {
                                R.string.list_showing_active
                            },
                        ),
                        onClick = { viewModel.onArchivedOnly(false) },
                    )
                    WideButton(
                        label = stringResource(
                            if (current.archivedOnly) {
                                R.string.list_showing_archived
                            } else {
                                R.string.list_filter_archived
                            },
                        ),
                        onClick = { viewModel.onArchivedOnly(true) },
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (current.noStudents) {
                            Text(
                                text = stringResource(R.string.dashboard_no_students),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                        if (current.noArchived) {
                            Text(
                                text = stringResource(R.string.student_no_archived),
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
                            StudentRowButton(row = row, onOpen = onOpen)
                        }
                    }
                    WideButton(stringResource(R.string.dashboard_add_student), onAdd)
                }
            }
        }
    }
}

@Composable
private fun StudentRowButton(row: StudentRow, onOpen: (String) -> Unit) {
    Button(
        onClick = { onOpen(row.id) },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(text = row.name, style = MaterialTheme.typography.bodyLarge)
            Text(text = row.code, style = MaterialTheme.typography.bodyLarge)
            if (row.phone != null) {
                Text(text = row.phone, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun WideButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
    ) {
        Text(text = label, style = MaterialTheme.typography.labelLarge)
    }
}
