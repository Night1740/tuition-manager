package com.tuitionmanager.feature.dashboard

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
fun DashboardScreen(
    onStudents: () -> Unit,
    onAddStudent: () -> Unit,
    onBatches: () -> Unit,
    onAttendance: () -> Unit,
    onFees: () -> Unit,
    onNotices: () -> Unit,
    onSettings: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
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
            when (val current = state) {
                DashboardUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.heightIn(min = 48.dp))
                    Text(
                        text = stringResource(R.string.start_loading),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                is DashboardUiState.Ready -> {
                    Text(
                        text = current.instituteName,
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(
                        text = stringResource(R.string.dashboard_student_count, current.studentCount),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = stringResource(R.string.dashboard_batch_count, current.batchCount),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    if (current.studentsEmpty) {
                        Text(
                            text = stringResource(R.string.dashboard_no_students),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        EntryButton(
                            label = stringResource(R.string.dashboard_add_student),
                            onClick = onAddStudent,
                        )
                    }
                    EntryButton(stringResource(R.string.nav_students), onStudents)
                    EntryButton(stringResource(R.string.nav_batches), onBatches)
                    EntryButton(stringResource(R.string.nav_attendance), onAttendance)
                    EntryButton(stringResource(R.string.nav_fees), onFees)
                    EntryButton(stringResource(R.string.nav_notices), onNotices)
                    EntryButton(stringResource(R.string.nav_settings), onSettings)
                }
                DashboardUiState.Error -> {
                    Text(
                        text = stringResource(R.string.start_error),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                    EntryButton(stringResource(R.string.start_retry), viewModel::retry)
                }
            }
        }
    }
}

@Composable
private fun EntryButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
    ) {
        Text(text = label, style = MaterialTheme.typography.labelLarge)
    }
}
