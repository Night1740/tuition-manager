package com.tuitionmanager.feature.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.tuitionmanager.feature.R
import com.tuitionmanager.feature.dashboard.DashboardScreen
import com.tuitionmanager.feature.dashboard.DashboardViewModel
import com.tuitionmanager.feature.onboarding.OnboardingScreen
import com.tuitionmanager.feature.onboarding.OnboardingViewModel
import com.tuitionmanager.feature.start.StartUiState
import com.tuitionmanager.feature.start.StartViewModel
import com.tuitionmanager.feature.students.STUDENT_SAVED
import com.tuitionmanager.feature.students.STUDENT_SAVED_KEY
import com.tuitionmanager.feature.students.StudentDetailsScreen
import com.tuitionmanager.feature.students.StudentFormScreen
import com.tuitionmanager.feature.students.StudentFormViewModel
import com.tuitionmanager.feature.students.StudentListScreen

@Composable
fun TuitionNavHost(
    startViewModel: StartViewModel = hiltViewModel(),
    onboardingViewModel: OnboardingViewModel = hiltViewModel(),
    dashboardViewModel: DashboardViewModel = hiltViewModel(),
    addStudentViewModel: StudentFormViewModel? = null,
) {
    val start by startViewModel.state.collectAsStateWithLifecycle()
    when (start) {
        StartUiState.Loading -> StartStatus(loading = true, onRetry = startViewModel::retry)
        StartUiState.Error -> StartStatus(loading = false, onRetry = startViewModel::retry)
        StartUiState.NeedsOnboarding -> OnboardingScreen(viewModel = onboardingViewModel)
        StartUiState.Ready -> AppNavHost(
            dashboardViewModel = dashboardViewModel,
            addStudentViewModel = addStudentViewModel,
        )
    }
}

@Composable
private fun AppNavHost(
    dashboardViewModel: DashboardViewModel,
    addStudentViewModel: StudentFormViewModel?,
    navController: NavHostController = rememberNavController(),
) {
    val snackbarHostState = remember { SnackbarHostState() }
    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = DashboardRoute,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable<DashboardRoute> {
                DashboardScreen(
                    viewModel = dashboardViewModel,
                    onStudents = { navController.navigate(StudentsRoute) },
                    onAddStudent = { navController.navigate(AddStudentRoute) },
                    onBatches = { navController.navigate(BatchesRoute) },
                    onAttendance = { navController.navigate(AttendanceRoute) },
                    onFees = { navController.navigate(FeesRoute) },
                    onNotices = { navController.navigate(NoticesRoute) },
                    onSettings = { navController.navigate(SettingsRoute) },
                )
            }
            composable<StudentsRoute> {
                StudentListScreen(
                    onBack = { navController.popBackStack() },
                    onAdd = { navController.navigate(AddStudentRoute) },
                    onOpen = { studentId -> navController.navigate(StudentDetailsRoute(studentId)) },
                )
            }
            composable<AddStudentRoute> {
                if (addStudentViewModel != null) {
                    StudentFormScreen(
                        onBack = { navController.popBackStack() },
                        onSaved = { navController.finishStudentSave() },
                        viewModel = addStudentViewModel,
                    )
                } else {
                    StudentFormScreen(
                        onBack = { navController.popBackStack() },
                        onSaved = { navController.finishStudentSave() },
                        viewModel = hiltViewModel(),
                    )
                }
            }
            composable<EditStudentRoute> {
                StudentFormScreen(
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.finishStudentSave() },
                )
            }
            composable<StudentDetailsRoute> { entry ->
                val route = entry.toRoute<StudentDetailsRoute>()
                StudentDetailsScreen(
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate(EditStudentRoute(route.studentId)) },
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
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        )
        StudentSavedMessages(navController, snackbarHostState)
    }
}

@Composable
private fun StudentSavedMessages(
    navController: NavHostController,
    snackbarHostState: SnackbarHostState,
) {
    val entry by navController.currentBackStackEntryAsState()
    val savedText = stringResource(R.string.student_saved)
    LaunchedEffect(entry?.id) {
        val handle = entry?.savedStateHandle ?: return@LaunchedEffect
        if (handle.get<String>(STUDENT_SAVED_KEY) == STUDENT_SAVED) {
            handle.remove<String>(STUDENT_SAVED_KEY)
            snackbarHostState.showSnackbar(savedText)
        }
    }
}

private fun NavHostController.finishStudentSave() {
    previousBackStackEntry?.savedStateHandle?.set(STUDENT_SAVED_KEY, STUDENT_SAVED)
    popBackStack()
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
