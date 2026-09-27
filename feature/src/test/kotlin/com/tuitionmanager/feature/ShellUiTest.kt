package com.tuitionmanager.feature

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import com.tuitionmanager.feature.dashboard.DashboardViewModel
import com.tuitionmanager.feature.navigation.TuitionNavHost
import com.tuitionmanager.feature.onboarding.OnboardingViewModel
import com.tuitionmanager.feature.start.StartViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ShellUiTest : FeatureRoom() {
    @get:Rule
    val compose = createComposeRule()

    @Before
    fun mainDispatcher() {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @After
    fun resetDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun onboardingThenDashboardDoesNotReturnToTheForm() {
        val start = StartViewModel(institutes)
        val onboarding = OnboardingViewModel(institutes, dispatchers)
        val dashboard = DashboardViewModel(institutes, students, batches)
        compose.setContent {
            MaterialTheme {
                TuitionNavHost(
                    startViewModel = start,
                    onboardingViewModel = onboarding,
                    dashboardViewModel = dashboard,
                )
            }
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("Class name").fetchSemanticsNodes().isNotEmpty()
        }
        click("Save")
        assertPlaced("Enter the class name.")

        compose.onNodeWithText("Class name").performTextInput("Morning Maths")
        compose.onNodeWithText("Your name").performTextInput("Anita Sharma")
        click("Save")

        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("No students yet").fetchSemanticsNodes().isNotEmpty()
        }
        assertPlaced("Morning Maths")
        assertPlaced("Students: 0")
        assertPlaced("Batches: 0")
        assertEquals(0, compose.onAllNodesWithText("Set up your class").fetchSemanticsNodes().size)

        click("Add Student")
        assertPlaced("The student list is not ready yet.")
        click("Back")
        assertPlaced("No students yet")
        assertEquals(0, compose.onAllNodesWithText("Set up your class").fetchSemanticsNodes().size)

        click("Attendance")
        assertPlaced("Coming soon")
        click("Back")
        assertPlaced("No students yet")
    }

    private fun click(text: String) {
        compose.onNodeWithText(text).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    private fun assertPlaced(text: String) {
        val node = compose.onNodeWithText(text).fetchSemanticsNode()
        check(node.size.width > 0 && node.size.height > 0) {
            "$text size=${node.size} position=${node.positionInRoot}"
        }
    }
}
