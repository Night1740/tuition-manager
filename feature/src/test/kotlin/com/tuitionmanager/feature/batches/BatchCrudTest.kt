package com.tuitionmanager.feature.batches

import androidx.lifecycle.SavedStateHandle
import com.tuitionmanager.core.domain.error.DataResult
import com.tuitionmanager.core.domain.error.InvalidCode
import com.tuitionmanager.core.domain.model.NewBatch
import com.tuitionmanager.core.domain.time.ClockLocalCalendar
import com.tuitionmanager.feature.FeatureRoom
import com.tuitionmanager.feature.dashboard.DashboardUiState
import com.tuitionmanager.feature.dashboard.DashboardViewModel
import com.tuitionmanager.feature.students.StudentDetailsUiState
import com.tuitionmanager.feature.students.StudentDetailsViewModel
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BatchCrudTest : FeatureRoom() {
    @Before
    fun setMainDispatcher() {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @After
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun clockLabelsUseTwelveHourTime() {
        assertEquals("12:00 AM", formatClock(0))
        assertEquals("12:00 PM", formatClock(12 * 60))
        assertEquals("4:00 PM", formatClock(16 * 60))
        assertEquals("11:05 PM", formatClock(23 * 60 + 5))
    }

    @Test
    fun formMapsEachValidationFailureToItsFieldThenSaves() = runBlocking {
        val institute = createInstitute()
        val form = form()
        form.start()
        val loaded = form.state.await { !it.loading && !it.loadError }
        assertFalse(loaded.dirty)
        assertEquals(16 * 60, loaded.startMinute)
        assertEquals(17 * 60, loaded.endMinute)

        form.save()
        val blank = form.state.value
        assertEquals(InvalidCode.BlankBatchName, blank.nameError)
        assertEquals(InvalidCode.BlankSubject, blank.subjectError)
        assertEquals(InvalidCode.NoDaysSelected, blank.daysError)
        assertEquals(InvalidCode.InvalidCapacity, blank.capacityError)
        assertNull(blank.timeError)
        assertNull(blank.roomError)

        form.onName("n".repeat(121))
        form.onSubject("s".repeat(81))
        form.onRoom("r".repeat(81))
        form.onCapacity("0")
        form.onEndMinute(16 * 60)
        form.onToggleDay(DayOfWeek.MONDAY)
        form.save()
        val invalid = form.state.value
        assertEquals(InvalidCode.NameTooLong, invalid.nameError)
        assertEquals(InvalidCode.SubjectTooLong, invalid.subjectError)
        assertEquals(InvalidCode.RoomTooLong, invalid.roomError)
        assertEquals(InvalidCode.InvalidCapacity, invalid.capacityError)
        assertEquals(InvalidCode.EndNotAfterStart, invalid.timeError)
        assertNull(invalid.daysError)

        form.onName("Evening")
        form.onSubject("Physics")
        form.onRoom("Lab")
        form.onCapacity("12")
        form.onEndMinute(18 * 60)
        form.save()
        form.state.await { it.saved }

        val stored = (batches.observeActive(institute.id).first() as DataResult.Success).value.single()
        assertEquals("Evening", stored.name)
        assertEquals("Physics", stored.subject)
        assertEquals(setOf(DayOfWeek.MONDAY), stored.daysOfWeek)
        assertEquals(16 * 60, stored.startMinute)
        assertEquals(18 * 60, stored.endMinute)
        assertEquals("Lab", stored.room)
        assertEquals(12, stored.capacity)

        val list = BatchListViewModel(institutes, batches)
        val row = list.state.awaitList().rows.single()
        assertEquals("Evening", row.name)
        assertEquals("Physics", row.subject)
        assertEquals("Mon · 4:00 PM - 6:00 PM", row.schedule)
        assertEquals("0 / 12", row.occupancy)
    }

    @Test
    fun listsKeepActiveAndArchivedApartAndDashboardCountsActiveOnly() = runBlocking {
        val institute = createInstitute()
        createBatch(institute.id, name = "Morning")
        val archived = batches.create(
            NewBatch(
                instituteId = institute.id,
                name = "Old",
                subject = "Maths",
                daysOfWeek = setOf(DayOfWeek.FRIDAY),
                startMinute = 16 * 60,
                endMinute = 17 * 60,
                room = null,
                capacity = 8,
            ),
        )
        check(archived is DataResult.Success)
        batches.archive(archived.value.id, LocalDate.of(2026, 9, 15))

        val list = BatchListViewModel(institutes, batches)
        val active = list.state.awaitList()
        assertEquals(listOf("Morning"), active.rows.map { it.name })
        assertFalse(active.archivedOnly)
        list.onArchivedOnly(true)
        val onlyArchived = list.state.awaitList { it.archivedOnly }
        assertEquals(listOf("Old"), onlyArchived.rows.map { it.name })

        val dashboard = DashboardViewModel(institutes, students, batches)
        val counts = dashboard.state.await { it is DashboardUiState.Ready } as DashboardUiState.Ready
        assertEquals(1, counts.batchCount)
    }

    @Test
    fun archiveUsesTheLocalCalendarAndRestoreDoesNotReopenAssignments() = runBlocking {
        val institute = createInstitute()
        val student = createStudent(institute.id)
        val batch = createBatch(institute.id)
        val assigned = assignments.assign(student.id, batch.id, LocalDate.of(2026, 9, 1))
        check(assigned is DataResult.Success)
        val kolkata = ClockLocalCalendar(
            Clock.fixed(Instant.parse("2026-09-15T20:00:00Z"), ZoneId.of("Asia/Kolkata")),
        )
        val details = details(batch.id, kolkata)
        val before = details.state.await {
            it is BatchDetailsUiState.Ready && it.students.size == 1
        } as BatchDetailsUiState.Ready
        assertEquals("1 / 20", before.occupancy)
        details.archive()
        val archived = details.state.await {
            it is BatchDetailsUiState.Ready && it.archived && !it.working && it.students.isEmpty()
        } as BatchDetailsUiState.Ready
        assertTrue(archived.students.isEmpty())
        val history = assignments.observeHistory(student.id).first {
            it is DataResult.Success && it.value.singleOrNull()?.endedOn != null
        } as DataResult.Success
        assertEquals(LocalDate.of(2026, 9, 16), history.value.single().endedOn)
        details.restore()
        val restored = details.state.await {
            it is BatchDetailsUiState.Ready && !it.archived && !it.working
        } as BatchDetailsUiState.Ready
        assertTrue(restored.students.isEmpty())
        val stillClosed = assignments.observeHistory(student.id).first() as DataResult.Success
        assertEquals(LocalDate.of(2026, 9, 16), stillClosed.value.single().endedOn)
    }

    @Test
    fun addStudentsWarnsOnCapacityThenConfirmsAndStudentCanMove() = runBlocking {
        val institute = createInstitute()
        val first = createStudent(institute.id, name = "Annika", code = "1")
        val second = createStudent(institute.id, name = "Ravi", code = "2")
        val small = batches.create(
            NewBatch(
                instituteId = institute.id,
                name = "Small",
                subject = "Maths",
                daysOfWeek = setOf(DayOfWeek.MONDAY),
                startMinute = 16 * 60,
                endMinute = 17 * 60,
                room = null,
                capacity = 1,
            ),
        )
        check(small is DataResult.Success)
        val other = createBatch(institute.id, name = "Other")
        val picker = AddStudentsViewModel(
            SavedStateHandle(mapOf("batchId" to small.value.id)),
            assignments,
            ClockLocalCalendar(clock),
            dispatchers,
        )
        picker.searchDebounceMillis = 0
        val choices = picker.state.awaitPicker()
        assertEquals(listOf("Annika", "Ravi"), choices.rows.map { it.name })
        picker.onToggle(first.id)
        picker.onToggle(second.id)
        picker.save()
        val warned = picker.state.awaitPicker { it.capacityPrompt != null }
        val prompt = checkNotNull(warned.capacityPrompt)
        assertEquals(0, prompt.enrolled)
        assertEquals(2, prompt.adding)
        assertEquals(1, prompt.capacity)
        assertEquals(0, assignments.observeActiveEnrollments(small.value.id).first().let {
            (it as DataResult.Success).value.size
        })
        picker.confirmCapacity()
        picker.state.awaitPicker { it.saved }
        val enrolled = assignments.observeActiveEnrollments(small.value.id).first() as DataResult.Success
        assertEquals(2, enrolled.value.size)

        val studentDetails = StudentDetailsViewModel(
            SavedStateHandle(mapOf("studentId" to first.id)),
            students,
            assignments,
            ClockLocalCalendar(clock),
            dispatchers,
        )
        val ready = studentDetails.state.await {
            it is StudentDetailsUiState.Ready && it.batches.any { batch -> batch.name == "Small" }
        } as StudentDetailsUiState.Ready
        val from = ready.batches.single { it.name == "Small" }
        studentDetails.move(from.batchId, other.id)
        val moved = studentDetails.state.await {
            it is StudentDetailsUiState.Ready &&
                !it.working &&
                it.batches.any { batch -> batch.name == "Other" } &&
                it.batches.none { batch -> batch.name == "Small" }
        } as StudentDetailsUiState.Ready
        assertEquals(listOf("Other"), moved.batches.map { it.name })
        val history = assignments.observeHistory(first.id).first() as DataResult.Success
        assertEquals(2, history.value.size)
        assertEquals(1, history.value.count { it.isActive })
        assertEquals(LocalDate.of(2026, 9, 15), history.value.single { it.batchId == small.value.id }.endedOn)
        assertNull(history.value.single { it.batchId == other.id }.endedOn)
    }

    private fun form() = BatchFormViewModel(
        SavedStateHandle(),
        institutes,
        batches,
        dispatchers,
    )

    private fun details(batchId: String, calendar: ClockLocalCalendar) = BatchDetailsViewModel(
        SavedStateHandle(mapOf("batchId" to batchId)),
        batches,
        assignments,
        calendar,
        dispatchers,
    )

    private suspend fun StateFlow<BatchListUiState>.awaitList(
        predicate: (BatchListUiState.Ready) -> Boolean = { true },
    ): BatchListUiState.Ready = await { it is BatchListUiState.Ready && predicate(it) } as BatchListUiState.Ready

    private suspend fun StateFlow<AddStudentsUiState>.awaitPicker(
        predicate: (AddStudentsUiState.Ready) -> Boolean = { true },
    ): AddStudentsUiState.Ready = await { it is AddStudentsUiState.Ready && predicate(it) } as AddStudentsUiState.Ready
}

private suspend fun <T> StateFlow<T>.await(predicate: (T) -> Boolean): T =
    withTimeout(5_000) { first(predicate) }
