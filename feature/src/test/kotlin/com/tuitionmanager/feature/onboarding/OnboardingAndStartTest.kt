package com.tuitionmanager.feature.onboarding

import com.tuitionmanager.core.domain.error.DataResult
import com.tuitionmanager.core.domain.error.InvalidCode
import com.tuitionmanager.core.domain.model.NewBatch
import com.tuitionmanager.feature.FeatureRoom
import com.tuitionmanager.feature.dashboard.DashboardUiState
import com.tuitionmanager.feature.dashboard.DashboardViewModel
import com.tuitionmanager.feature.start.StartUiState
import com.tuitionmanager.feature.start.StartViewModel
import java.time.DayOfWeek
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingAndStartTest : FeatureRoom() {
    @Before
    fun setMainDispatcher() {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @After
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun invalidOnboardingIsNotStoredAndAValidSaveBecomesTheStartDestination() = runBlocking {
        val onboarding = OnboardingViewModel(institutes, dispatchers)
        onboarding.save()
        assertEquals(InvalidCode.BlankName, onboarding.state.value.classNameError)
        assertNull((institutes.get() as DataResult.Success).value)

        onboarding.onClassName("Morning Maths")
        onboarding.onOwnerName(" ")
        onboarding.save()
        assertEquals(InvalidCode.BlankOwnerName, onboarding.state.value.ownerNameError)

        onboarding.onOwnerName("Anita Sharma")
        onboarding.onPhone("12")
        onboarding.save()
        assertEquals(InvalidCode.InvalidPhone, onboarding.state.value.phoneError)
        assertNull((institutes.get() as DataResult.Success).value)

        onboarding.onPhone("+91 98765 43210")
        onboarding.onAddress("Lane 4")
        onboarding.save()
        onboarding.state.first { !it.saving }
        val stored = (institutes.get() as DataResult.Success).value
        checkNotNull(stored)
        assertEquals("Morning Maths", stored.name)
        assertEquals("Anita Sharma", stored.ownerName)
        assertEquals("9876543210", stored.phone)
        assertEquals("Lane 4", stored.address)

        val start = StartViewModel(institutes)
        assertEquals(StartUiState.Ready, start.state.first { it != StartUiState.Loading })
    }

    @Test
    fun emptyDatabaseChoosesOnboarding() = runBlocking {
        val start = StartViewModel(institutes)
        assertEquals(StartUiState.NeedsOnboarding, start.state.first { it != StartUiState.Loading })
    }

    @Test
    fun dashboardCountsFollowActiveStudentsAndBatches() = runBlocking {
        val institute = createInstitute()
        val dashboard = DashboardViewModel(institutes, students, batches)
        val seen = mutableListOf<DashboardUiState.Ready>()
        val job = launch {
            dashboard.state.collect { state ->
                if (state is DashboardUiState.Ready) seen += state
            }
        }
        await { seen.any { it.studentCount == 0 && it.batchCount == 0 && it.studentsEmpty } }
        assertEquals("Morning Maths", seen.last().instituteName)

        createStudent(institute.id)
        batches.create(
            NewBatch(
                instituteId = institute.id,
                name = "Algebra",
                subject = "Maths",
                daysOfWeek = setOf(DayOfWeek.MONDAY),
                startMinute = 16 * 60,
                endMinute = 17 * 60,
                room = null,
                capacity = 20,
            ),
        ).let { check(it is DataResult.Success) { it } }
        await { seen.any { it.studentCount == 1 && it.batchCount == 1 && !it.studentsEmpty } }
        job.cancel()
    }

    private suspend fun await(ready: () -> Boolean) {
        withTimeout(5_000) {
            while (!ready()) yield()
        }
        assertTrue(ready())
    }
}
