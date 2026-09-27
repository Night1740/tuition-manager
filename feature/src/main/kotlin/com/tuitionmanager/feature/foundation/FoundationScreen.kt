package com.tuitionmanager.feature.foundation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
fun FoundationScreen(
    viewModel: FoundationViewModel = hiltViewModel(),
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
            Text(
                text = stringResource(R.string.foundation_title),
                style = MaterialTheme.typography.headlineMedium,
            )
            when (state) {
                FoundationUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.heightIn(min = 48.dp))
                    Text(
                        text = stringResource(R.string.foundation_loading),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                FoundationUiState.Ready -> {
                    Text(
                        text = stringResource(R.string.foundation_ready),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                FoundationUiState.Error -> {
                    Text(
                        text = stringResource(R.string.foundation_error),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Button(
                        onClick = viewModel::retry,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.foundation_retry),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }
}
