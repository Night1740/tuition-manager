package com.tuitionmanager.feature.navigation

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
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tuitionmanager.feature.R
import com.tuitionmanager.feature.dashboard.DashboardScreen
import com.tuitionmanager.feature.dashboard.DashboardViewModel
import com.tuitionmanager.feature.onboarding.OnboardingScreen
import com.tuitionmanager.feature.onboarding.OnboardingViewModel
import com.tuitionmanager.feature.start.StartUiState
import com.tuitionmanager.feature.start.StartViewModel

@Composable
fun TuitionNavHost(
    startViewModel: StartViewModel = hiltViewModel(),
    onboardingViewModel: OnboardingViewModel = hiltViewModel(),
    dashboardViewModel: DashboardViewModel = hiltViewModel(),
) {
    val start by startViewModel.state.collectAsStateWithLifecycle()
    when (start) {
        StartUiState.Loading -> StartStatus(loading = true, onRetry = startViewModel::retry)
        StartUiState.Error -> StartStatus(loading = false, onRetry = startViewModel::retry)
        StartUiState.NeedsOnboarding -> OnboardingScreen(viewModel = onboardingViewModel)
        StartUiState.Ready -> AppNavHost(dashboardViewModel = dashboardViewModel)
    }
}

@Composable
private fun AppNavHost(
    dashboardViewModel: DashboardViewModel,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = DashboardRoute,
    ) {
        composable<DashboardRoute> {
            DashboardScreen(
                viewModel = dashboardViewModel,
                onStudents = { navController.navigate(StudentsRoute) },
                onAddStudent = { navController.navigate(StudentsRoute) },
                onBatches = { navController.navigate(BatchesRoute) },
                onAttendance = { navController.navigate(AttendanceRoute) },
                onFees = { navController.navigate(FeesRoute) },
                onNotices = { navController.navigate(NoticesRoute) },
                onSettings = { navController.navigate(SettingsRoute) },
            )
        }
        composable<StudentsRoute> {
            MessageScreen(
                title = stringResource(R.string.nav_students),
                body = stringResource(R.string.students_placeholder),
                onBack = { navController.popBackStack() },
            )
        }
        composable<BatchesRoute> {
            MessageScreen(
                title = stringResource(R.string.nav_batches),
                body = stringResource(R.string.batches_placeholder),
                onBack = { navController.popBackStack() },
            )
        }
        composable<AttendanceRoute> {
            ComingSoon(stringResource(R.string.nav_attendance)) { navController.popBackStack() }
        }
        composable<FeesRoute> {
            ComingSoon(stringResource(R.string.nav_fees)) { navController.popBackStack() }
        }
        composable<NoticesRoute> {
            ComingSoon(stringResource(R.string.nav_notices)) { navController.popBackStack() }
        }
        composable<SettingsRoute> {
            ComingSoon(stringResource(R.string.nav_settings)) { navController.popBackStack() }
        }
    }
}

@Composable
private fun ComingSoon(title: String, onBack: () -> Unit) {
    MessageScreen(
        title = title,
        body = stringResource(R.string.coming_soon),
        onBack = onBack,
    )
}

@Composable
private fun StartStatus(loading: Boolean, onRetry: () -> Unit) {
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.heightIn(min = 48.dp))
                Text(
                    text = stringResource(R.string.start_loading),
                    style = MaterialTheme.typography.bodyLarge,
                )
            } else {
                Text(
                    text = stringResource(R.string.start_error),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                )
                Button(
                    onClick = onRetry,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) {
                    Text(
                        text = stringResource(R.string.start_retry),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}
