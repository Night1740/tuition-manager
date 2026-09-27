package com.tuitionmanager

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * First-launch flow. Persistence after `am force-stop` is [RelaunchPersistenceTest],
 * started as its own instrumentation so this process is not killed mid-run.
 */
@RunWith(AndroidJUnit4::class)
class Phase1EndToEndTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Before
    fun disableAnimations() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        listOf(
            "settings put global animator_duration_scale 0",
            "settings put global transition_animation_scale 0",
            "settings put global window_animation_scale 0",
        ).forEach { command ->
            automation.executeShellCommand(command).close()
        }
    }

    @Test(timeout = 300_000)
    fun firstLaunchThroughBatches() {
        waitFor("Set up your class")
        shot("01-onboarding")

        replace("Class name", "Morning Maths")
        replace("Your name", "Anita Sharma")
        replace("Phone (optional)", "+91 98765-43210")
        click("Save")

        waitFor("Students: 0")
        waitFor("Batches: 0")
        waitFor("No students yet")
        shot("02-dashboard-empty")

        click("Add Student")
        waitFor("Student name")
        replace("Student name", "Ravi Kumar")
        replace("Student code", "1")
        replace("Student phone", "9876543210")
        click("Save")
        awaitThenClear("Student saved")
        waitFor("Students: 1")
        shot("03-student-added")

        click("Students")
        waitFor("Ravi Kumar")
        click("Ravi Kumar")
        waitFor("Edit")
        click("Edit")
        waitFor("Edit student")
        waitFor("Student name")
        replace("Student name", "Ravi Edited")
        click("Save")
        awaitThenClear("Student saved")
        waitFor("Ravi Edited")
        shot("04-student-edited")

        click("Back")
        waitFor("Search")
        replace("Search", "Edited")
        waitFor("Ravi Edited")
        replace("Search", "zzzz")
        waitFor("No results")
        shot("05-search")
        replace("Search", "")
        waitFor("Ravi Edited")

        click("Ravi Edited")
        click("Archive")
        waitFor("This student will be removed from their batches. Restoring them will not add them back.")
        clickLast("Archive")
        waitFor("Archived")
        click("Back")
        waitFor("No students yet")
        click("Archived")
        waitFor("Ravi Edited")
        shot("06-archived")
        click("Ravi Edited")
        click("Restore")
        waitFor("Archive")
        click("Back")
        waitFor("No archived students")
        click("Active")
        waitFor("Ravi Edited")
        shot("07-restored")

        click("Back")
        click("Batches")
        waitFor("No batches yet")
        createBatch(name = "Algebra", subject = "Maths", day = "Mon")
        awaitThenClear("Batch saved")
        waitFor("Algebra")
        createBatch(name = "Science", subject = "Physics", day = "Tue")
        awaitThenClear("Batch saved")
        waitFor("Science")
        shot("08-batches")

        click("Back")
        click("Students")
        click("Ravi Edited")
        click("Add to batch")
        waitFor("Algebra · Maths")
        click("Algebra · Maths")
        waitFor("Algebra · Maths")
        shot("09-assigned")

        click("Move to another batch")
        waitFor("Science · Physics")
        click("Science · Physics")
        waitFor("Science · Physics")
        shot("10-moved")

        click("Remove")
        waitFor("Remove Science from this batch?")
        clickLast("Remove")
        waitFor("Not in any batch")
        shot("11-removed")

        click("Back")
        click("Back")
        waitFor("Students: 1")
        waitFor("Batches: 2")
        shot("12-dashboard")
    }

    private fun createBatch(name: String, subject: String, day: String) {
        click("Create Batch")
        waitFor("Batch name")
        replace("Batch name", name)
        replace("Subject", subject)
        click(day)
        replace("Capacity", "20")
        click("Save")
    }

    private fun replace(label: String, value: String) {
        val node = compose.onNodeWithText(label)
        runCatching { node.performScrollTo() }
        node.performClick()
        node.performTextReplacement(value)
        compose.waitForIdle()
    }

    private fun click(text: String) {
        val node = compose.onNodeWithText(text)
        runCatching { node.performScrollTo() }
        node.performClick()
        compose.waitForIdle()
    }

    private fun clickLast(text: String) {
        val nodes = compose.onAllNodesWithText(text)
        val node = nodes[nodes.fetchSemanticsNodes().lastIndex]
        runCatching { node.performScrollTo() }
        node.performClick()
        compose.waitForIdle()
    }

    private fun waitFor(text: String) {
        compose.waitUntil(timeoutMillis = 20_000) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun awaitThenClear(text: String) {
        waitFor(text)
        compose.waitUntil(timeoutMillis = 15_000) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty()
        }
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        captureNamedShot(name)
    }
}
