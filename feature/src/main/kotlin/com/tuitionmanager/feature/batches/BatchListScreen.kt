package com.tuitionmanager.feature.batches

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
fun BatchListScreen(
    onCreate: () -> Unit,
    onOpen: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: BatchListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
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
                text = stringResource(R.string.nav_batches),
                style = MaterialTheme.typography.headlineMedium,
            )
            when (val current = state) {
                BatchListUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.heightIn(min = 48.dp))
                    Text(
                        text = stringResource(R.string.start_loading),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                BatchListUiState.Error -> {
                    Text(
                        text = stringResource(R.string.batch_list_error),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                    WideButton(stringResource(R.string.start_retry), viewModel::retry)
                }
                is BatchListUiState.Ready -> {
                    WideButton(
                        label = stringResource(
                            if (current.archivedOnly) R.string.list_filter_active else R.string.list_showing_active,
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
                        if (current.noBatches) {
                            Text(
                                text = stringResource(R.string.batch_empty),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                        if (current.noArchived) {
                            Text(
                                text = stringResource(R.string.batch_no_archived),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                        current.rows.forEach { row ->
                            BatchRowButton(row = row, onOpen = onOpen)
                        }
                    }
                    WideButton(stringResource(R.string.batch_create), onCreate)
                }
            }
        }
    }
}

@Composable
private fun BatchRowButton(row: BatchRow, onOpen: (String) -> Unit) {
    Button(
        onClick = { onOpen(row.id) },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(text = row.name, style = MaterialTheme.typography.bodyLarge)
            Text(text = row.subject, style = MaterialTheme.typography.bodyLarge)
            Text(text = row.schedule, style = MaterialTheme.typography.bodyLarge)
            Text(text = row.occupancy, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
internal fun WideButton(label: String, onClick: () -> Unit, enabled: Boolean = true) {
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
