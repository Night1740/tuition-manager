package com.tuitionmanager.feature.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe destinations. [TuitionNavHost] registers only what exists today.
 * Later phases add a `composable<Route>` block for each planned route. Argument routes
 * (a student id, a batch id) should be added here as `@Serializable` data classes.
 */
@Serializable
data object FoundationRoute

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
