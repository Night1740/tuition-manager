package com.tuitionmanager.feature.batches

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
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class BatchScreensTest : FeatureRoom() {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun emptyBatchListSaysThereAreNoBatchesYet() {
        createInstitute()
        val list = BatchListViewModel(institutes, batches)
        compose.setContent {
            MaterialTheme {
                BatchListScreen(onCreate = {}, onOpen = {}, onBack = {}, viewModel = list)
            }
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("No batches yet").fetchSemanticsNodes().isNotEmpty()
        }
        assertPlaced("No batches yet")
        assertPlaced("Create Batch")
        assertPlaced("Showing active")
        assertPlaced("Archived")
    }

    @Test
    fun blankFormShowsFieldErrorsAndConfirmsDiscard() {
        createInstitute()
        val form = BatchFormViewModel(SavedStateHandle(), institutes, batches, dispatchers)
        var closed by mutableStateOf(false)
        compose.setContent {
            MaterialTheme {
                if (closed) {
                    Text("closed")
                } else {
                    BatchFormScreen(onBack = { closed = true }, onSaved = {}, viewModel = form)
                }
            }
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("Batch name").fetchSemanticsNodes().isNotEmpty()
        }
        assertPlaced("Start 4:00 PM")
        assertPlaced("End 5:00 PM")
        click("Save")
        assertPlaced("Enter a batch name.")
        assertPlaced("Enter a subject.")
        assertPlaced("Select at least one day.")
        assertPlaced("Enter a capacity from 1 to 2000.")

        compose.onNodeWithText("Batch name").performTextInput("Evening")
        click("Back")
        assertPlaced("Discard changes?")
        click("Keep editing")
        assertEquals(0, compose.onAllNodesWithText("Discard changes?").fetchSemanticsNodes().size)

        click("Back")
        click("Discard")
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("closed").fetchSemanticsNodes().isNotEmpty()
        }
        assertPlaced("closed")
    }

    @Test
    fun archiveDialogStatesHowManyStudentsLeave() {
        val institute = createInstitute()
        val batch = createBatch(institute.id)
        val student = createStudent(institute.id)
        kotlinx.coroutines.runBlocking {
            val assigned = assignments.assign(student.id, batch.id, LocalDate.of(2026, 9, 1))
            check(assigned is com.tuitionmanager.core.domain.error.DataResult.Success)
        }
        val details = BatchDetailsViewModel(
            SavedStateHandle(mapOf("batchId" to batch.id)),
            batches,
            assignments,
            ClockLocalCalendar(clock),
            dispatchers,
        )
        compose.setContent {
            MaterialTheme {
                BatchDetailsScreen(
                    onBack = {},
                    onEdit = {},
                    onAddStudents = {},
                    viewModel = details,
                )
            }
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("Ravi Kumar · A-01").fetchSemanticsNodes().isNotEmpty()
        }
        assertPlaced("1 / 20")
        click("Archive")
        assertPlaced("1 student will be removed from this batch. Restoring the batch will not add them back.")
        click("Cancel")
        assertEquals(
            0,
            compose.onAllNodesWithText(
                "1 student will be removed from this batch. Restoring the batch will not add them back.",
            ).fetchSemanticsNodes().size,
        )
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
