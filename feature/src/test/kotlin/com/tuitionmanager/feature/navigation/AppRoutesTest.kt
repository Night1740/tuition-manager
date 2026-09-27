package com.tuitionmanager.feature.navigation

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class AppRoutesTest {
    @Test
    fun routesRoundTripAndPlannedSetStaysExplicit() {
        roundTrip(OnboardingRoute)
        roundTrip(DashboardRoute)
        roundTrip(StudentsRoute)
        roundTrip(BatchesRoute)
        roundTrip(AttendanceRoute)
        roundTrip(FeesRoute)
        roundTrip(NoticesRoute)
        roundTrip(HomeworkRoute)
        roundTrip(MaterialsRoute)
        roundTrip(TestsRoute)
        roundTrip(ReportsRoute)
        roundTrip(SettingsRoute)
        roundTrip(SmartRoute)
        assertEquals(11, plannedRoutes.size)
    }

    private inline fun <reified T> roundTrip(value: T) {
        val json = Json.encodeToString(value)
        assertEquals(value, Json.decodeFromString<T>(json))
    }
}
