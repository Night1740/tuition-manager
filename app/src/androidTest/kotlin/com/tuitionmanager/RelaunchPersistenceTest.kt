package com.tuitionmanager

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Run only after [Phase1EndToEndTest] and `adb shell am force-stop com.tuitionmanager`.
 * A fresh install has no class, so the Gradle connected task excludes this class.
 */
@RunWith(AndroidJUnit4::class)
class RelaunchPersistenceTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test(timeout = 60_000)
    fun keepsClassAndSkipsOnboarding() {
        compose.onNodeWithText("Set up your class").assertDoesNotExist()
        waitFor("Morning Maths")
        waitFor("Students: 1")
        waitFor("Batches: 2")
        shot("13-relaunch-dashboard")
        click("Students")
        waitFor("Ravi Edited")
        shot("14-relaunch-student")
        click("Back")
        click("Batches")
        waitFor("Algebra")
        waitFor("Science")
        shot("15-relaunch-batches")
    }

    private fun click(text: String) {
        val node = compose.onNodeWithText(text)
        runCatching { node.performScrollTo() }
        node.performClick()
        compose.waitForIdle()
    }

    private fun waitFor(text: String) {
        compose.waitUntil(timeoutMillis = 20_000) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        captureNamedShot(name)
    }
}
