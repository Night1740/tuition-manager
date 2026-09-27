package com.tuitionmanager.core.data

import com.tuitionmanager.core.domain.error.ConflictCode
import com.tuitionmanager.core.domain.error.DataError
import com.tuitionmanager.core.domain.error.DataResult
import com.tuitionmanager.core.domain.error.InvalidCode
import com.tuitionmanager.core.domain.model.NewBatch
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchAssignmentRepositoryTest : RoomFixture() {
    private val monday = LocalDate.of(2026, 9, 14)

    @Test
    fun rejectsInvalidScheduleAndCapacity() = runBlocking {
        val institute = createInstitute()
        val emptyDays = batches.create(draft(institute.id, days = emptySet()))
        assertEquals(DataResult.Failure(DataError.Invalid(InvalidCode.InvalidSchedule)), emptyDays)
        val backwards = batches.create(draft(institute.id, start = 600, end = 600))
        assertEquals(DataResult.Failure(DataError.Invalid(InvalidCode.InvalidSchedule)), backwards)
        val capacity = batches.create(draft(institute.id, capacity = 0))
        assertEquals(DataResult.Failure(DataError.Invalid(InvalidCode.InvalidCapacity)), capacity)
        val huge = batches.create(draft(institute.id, capacity = 2_001))
        assertEquals(DataResult.Failure(DataError.Invalid(InvalidCode.InvalidCapacity)), huge)
        assertEquals(0, batches.countActive(institute.id).success())
    }

    @Test
    fun archiveHidesBatchFromActiveList() = runBlocking {
        val institute = createInstitute()
        val batch = createBatch(institute.id)
        batches.archive(batch.id).success()
        assertTrue(batches.observeActive(institute.id).awaitSuccess().isEmpty())
        assertEquals(1, batches.observe(institute.id, includeArchived = true).awaitSuccess().size)
        assertTrue(batches.get(batch.id).success().isArchived)
    }

    @Test
    fun studentCanBeInTwoBatchesButNotTwiceInOne() = runBlocking {
        val institute = createInstitute()
        val student = createStudent(institute.id)
        val algebra = createBatch(institute.id, name = "Algebra", capacity = 2)
        val science = createBatch(institute.id, name = "Science", capacity = 2)
        assignments.assign(student.id, algebra.id, monday).success()
        assignments.assign(student.id, science.id, monday).success()
        val duplicate = assignments.assign(student.id, algebra.id, monday.plusDays(1))
        assertEquals(
            DataResult.Failure(DataError.Conflict(ConflictCode.DuplicateActiveAssignment)),
            duplicate,
        )
        assertEquals(2, assignments.observeHistory(student.id).awaitSuccess().size)
    }

    @Test
    fun moveClosesTheOldAssignmentAndOpensTheNewOne() = runBlocking {
        val institute = createInstitute()
        val student = createStudent(institute.id)
        val morning = createBatch(institute.id, name = "Morning")
        val evening = createBatch(institute.id, name = "Evening")
        assignments.assign(student.id, morning.id, monday).success()
        val moved = assignments.move(student.id, morning.id, evening.id, monday.plusDays(7)).success()
        assertEquals(evening.id, moved.batchId)
        assertNull(moved.endedOn)
        val history = assignments.observeHistory(student.id).awaitSuccess()
        assertEquals(2, history.size)
        val closed = history.single { it.batchId == morning.id }
        assertEquals(monday.plusDays(7), closed.endedOn)
        assertTrue(assignments.observeActiveForBatch(morning.id).awaitSuccess().isEmpty())
        assertEquals(1, assignments.observeActiveEnrollments(evening.id).awaitSuccess().size)
    }

    @Test
    fun endingThenRejoiningKeepsHistory() = runBlocking {
        val institute = createInstitute()
        val student = createStudent(institute.id)
        val batch = createBatch(institute.id)
        val first = assignments.assign(student.id, batch.id, monday).success()
        assignments.end(first.id, monday.plusDays(3)).success()
        val second = assignments.assign(student.id, batch.id, monday.plusDays(10)).success()
        val history = assignments.observeHistory(student.id).awaitSuccess()
        assertEquals(listOf(first.id, second.id), history.map { it.id })
        assertEquals(1, history.count { it.isActive })
    }

    @Test
    fun fullBatchRejectsAssignAndDoesNotMove() = runBlocking {
        val institute = createInstitute()
        val first = createStudent(institute.id, name = "One", code = "1")
        val second = createStudent(institute.id, name = "Two", code = "2")
        val source = createBatch(institute.id, name = "Source", capacity = 2)
        val destination = createBatch(institute.id, name = "Destination", capacity = 1)
        assignments.assign(first.id, destination.id, monday).success()
        assignments.assign(second.id, source.id, monday).success()
        val overflow = assignments.assign(second.id, destination.id, monday)
        assertEquals(DataResult.Failure(DataError.Conflict(ConflictCode.BatchFull)), overflow)
        val move = assignments.move(second.id, source.id, destination.id, monday.plusDays(1))
        assertEquals(DataResult.Failure(DataError.Conflict(ConflictCode.BatchFull)), move)
        assertEquals(source.id, assignments.observeHistory(second.id).awaitSuccess().single().batchId)
        assertTrue(assignments.observeHistory(second.id).awaitSuccess().single().isActive)
    }

    @Test
    fun archivingEndsOpenAssignmentsAndRestoreLeavesThemClosed() = runBlocking {
        val institute = createInstitute()
        val student = createStudent(institute.id)
        val algebra = createBatch(institute.id, name = "Algebra")
        val science = createBatch(institute.id, name = "Science")
        assignments.assign(student.id, algebra.id, monday).success()
        assignments.assign(student.id, science.id, monday).success()
        val archiveDay = LocalDate.of(2026, 9, 20)
        students.archive(student.id, archiveDay).success()
        val closed = assignments.observeHistory(student.id).awaitSuccess()
        assertEquals(2, closed.size)
        assertTrue(closed.all { it.endedOn == archiveDay && !it.isActive })
        assertTrue(assignments.observeActiveForBatch(algebra.id).awaitSuccess().isEmpty())
        assertTrue(assignments.observeActiveEnrollments(science.id).awaitSuccess().isEmpty())
        students.restore(student.id).success()
        assertFalse(students.get(student.id).success().isArchived)
        val afterRestore = assignments.observeHistory(student.id).awaitSuccess()
        assertTrue(afterRestore.all { it.endedOn == archiveDay })
        assertEquals(0, assignments.observeActiveEnrollments(algebra.id).awaitSuccess().size)
        assertEquals(0, assignments.observeActiveEnrollments(science.id).awaitSuccess().size)
    }

    @Test
    fun archivedStudentCannotBeAssigned() = runBlocking {
        val institute = createInstitute()
        val student = createStudent(institute.id)
        val batch = createBatch(institute.id)
        students.archive(student.id, monday).success()
        val result = assignments.assign(student.id, batch.id, monday)
        assertEquals(DataResult.Failure(DataError.Invalid(InvalidCode.StudentArchived)), result)
    }

    private fun draft(
        instituteId: String,
        days: Set<DayOfWeek> = setOf(DayOfWeek.TUESDAY),
        start: Int = 9 * 60,
        end: Int = 10 * 60,
        capacity: Int = 10,
    ) = NewBatch(
        instituteId = instituteId,
        name = "Batch",
        subject = "Science",
        daysOfWeek = days,
        startMinute = start,
        endMinute = end,
        room = null,
        capacity = capacity,
    )
}
