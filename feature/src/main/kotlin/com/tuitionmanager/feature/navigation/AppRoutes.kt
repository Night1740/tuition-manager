package com.tuitionmanager.feature.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe destinations. Students and batches are registered.
 * Later sections are registered as "coming soon" or left in [plannedRoutes] until their phase.
 */
@Serializable
data object OnboardingRoute

@Serializable
data object DashboardRoute

@Serializable
data object StudentsRoute

@Serializable
data object AddStudentRoute

@Serializable
data class EditStudentRoute(val studentId: String)

@Serializable
data class StudentDetailsRoute(val studentId: String)

@Serializable
data object BatchesRoute

@Serializable
data object AddBatchRoute

@Serializable
data class EditBatchRoute(val batchId: String)

@Serializable
data class BatchDetailsRoute(val batchId: String)

@Serializable
data class AddStudentsToBatchRoute(val batchId: String)

@Serializable
data object AttendanceRoute

@Serializable
data object FeesRoute

@Serializable
data object NoticesRoute

@Serializable
data object HomeworkRoute

@Serializable
data object MaterialsRoute

@Serializable
data object TestsRoute

@Serializable
data object ReportsRoute

@Serializable
data object SettingsRoute

@Serializable
data object SmartRoute

val plannedRoutes: List<Any> = listOf(
    AttendanceRoute,
    FeesRoute,
    NoticesRoute,
    HomeworkRoute,
    MaterialsRoute,
    TestsRoute,
    ReportsRoute,
    SettingsRoute,
    SmartRoute,
)
