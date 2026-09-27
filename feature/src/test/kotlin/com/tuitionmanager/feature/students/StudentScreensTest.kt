package com.tuitionmanager.feature.students

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import com.tuitionmanager.core.domain.time.ClockLocalCalendar
import com.tuitionmanager.feature.FeatureRoom
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class StudentScreensTest : FeatureRoom() {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun emptyStudentListSaysThereAreNoStudentsYet() {
        createInstitute()
        val list = StudentListViewModel(institutes, students)
        list.searchDebounceMillis = 0
        compose.setContent {
            MaterialTheme {
                StudentListScreen(onAdd = {}, onOpen = {}, onBack = {}, viewModel = list)
            }
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("No students yet").fetchSemanticsNodes().isNotEmpty()
        }
        assertPlaced("No students yet")
        assertPlaced("Add Student")
        assertPlaced("Search")
        assertPlaced("Showing active")
        assertPlaced("Archived")
    }

    @Test
    fun blankFormShowsFieldErrorsAndConfirmsDiscard() {
        createInstitute()
        val form = StudentFormViewModel(
            SavedStateHandle(),
            institutes,
            students,
            ClockLocalCalendar(clock),
            dispatchers,
        )
        var closed by mutableStateOf(false)
        compose.setContent {
            MaterialTheme {
                if (closed) {
                    Text("closed")
                } else {
                    StudentFormScreen(
                        onBack = { closed = true },
                        onSaved = {},
                        viewModel = form,
                    )
                }
            }
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("15 Sep 2026").fetchSemanticsNodes().isNotEmpty()
        }
        assertPlaced("1")
        click("Save")
        assertPlaced("Enter the student name.")
        assertPlaced("Enter a phone number for the student or the guardian.")

        compose.onNodeWithText("Student name").performTextInput("Ravi Kumar")
        click("Back")
        assertPlaced("Discard changes?")
        assertPlaced("Your edits will be lost.")
        click("Keep editing")
        assertEquals(0, compose.onAllNodesWithText("Discard changes?").fetchSemanticsNodes().size)
        assertPlaced("Student name")

        click("Back")
        click("Discard")
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("closed").fetchSemanticsNodes().isNotEmpty()
        }
        assertPlaced("closed")
    }

    @Test
    fun archiveDialogExplainsThatBatchesAreLeftBehind() {
        val institute = createInstitute()
        val student = createStudent(institute.id)
        val details = StudentDetailsViewModel(
            SavedStateHandle(mapOf("studentId" to student.id)),
            students,
            assignments,
            ClockLocalCalendar(clock),
            dispatchers,
        )
        compose.setContent {
            MaterialTheme {
                StudentDetailsScreen(onBack = {}, onEdit = {}, viewModel = details)
            }
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("Ravi Kumar").fetchSemanticsNodes().isNotEmpty()
        }
        assertPlaced("Not in any batch")
        assertPlaced("9876543210")
        click("Archive")
        assertPlaced("This student will be removed from their batches. Restoring them will not add them back.")
        click("Cancel")
        assertEquals(
            0,
            compose.onAllNodesWithText("Restoring them will not add them back.").fetchSemanticsNodes().size,
        )
        assertPlaced("Archive")
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
