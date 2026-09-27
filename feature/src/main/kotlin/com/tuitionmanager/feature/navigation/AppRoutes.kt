package com.tuitionmanager.feature.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe destinations. [TuitionNavHost] registers the screens that exist today.
 * Later phases replace the student and batch placeholders and add a `composable` block
 * for each remaining route. Argument routes (a student id, a batch id) should be added
 * here as `@Serializable` data classes.
 */
@Serializable
data object OnboardingRoute

@Serializable
data object DashboardRoute

@Serializable
data object StudentsRoute

@Serializable
data object BatchesRoute

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
    StudentsRoute,
    BatchesRoute,
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
