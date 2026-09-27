package com.tuitionmanager.feature.students

import androidx.lifecycle.SavedStateHandle
import com.tuitionmanager.core.domain.error.DataResult
import com.tuitionmanager.core.domain.error.InvalidCode
import com.tuitionmanager.core.domain.model.NewStudent
import com.tuitionmanager.core.domain.time.ClockLocalCalendar
import com.tuitionmanager.feature.FeatureRoom
import com.tuitionmanager.feature.dashboard.DashboardUiState
import com.tuitionmanager.feature.dashboard.DashboardViewModel
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StudentCrudTest : FeatureRoom() {
    @Before
    fun setMainDispatcher() {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @After
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun formCreatesAStudentWithTheSuggestedCodeAndLocalAdmissionDate() = runBlocking {
        val institute = createInstitute()
        val form = form()
        form.start()
        val loaded = form.state.await { !it.loading && !it.loadError }
        assertEquals("1", loaded.code)
        assertEquals(LocalDate.of(2026, 9, 15), loaded.admissionDate)
        assertFalse(loaded.dirty)

        form.onName("Ravi Kumar")
        assertTrue(form.state.value.dirty)
        form.onPhone("98765 43210")
        form.save()
        form.state.await { it.saved }

        val stored = (students.observeActive(institute.id).first() as DataResult.Success).value.single()
        assertEquals("Ravi Kumar", stored.name)
        assertEquals("1", stored.studentCode)
        assertEquals("9876543210", stored.phone)
        assertEquals(LocalDate.of(2026, 9, 15), stored.admissionDate)
        assertFalse(stored.isArchived)
    }

    @Test
    fun formShowsValidationErrorsAndRejectsADuplicateCode() = runBlocking {
        val institute = createInstitute()
        createStudent(institute.id, code = "1")
        val form = form()
        form.start()
        form.state.await { !it.loading && it.code == "2" }

        form.save()
        val blank = form.state.value
        assertEquals(InvalidCode.BlankName, blank.nameError)
        assertEquals(InvalidCode.MissingContactPhone, blank.contactError)
        assertEquals(1, activeCount(institute.id))

        form.onName("Ravi Kumar")
        form.onPhone("12")
        form.save()
        assertEquals(InvalidCode.InvalidPhone, form.state.value.phoneError)
        assertEquals(1, activeCount(institute.id))

        form.onPhone("9876543210")
        form.onCode("1")
        form.save()
        val taken = form.state.await { it.codeTaken || it.storageError }
        assertTrue(taken.codeTaken)
        assertNull(taken.codeError)
        assertEquals(1, activeCount(institute.id))
    }

    @Test
    fun editKeepsPhotoArchiveFlagAndOriginalTimestampsRole() = runBlocking {
        val institute = createInstitute()
        val created = students.create(
            NewStudent(
                instituteId = institute.id,
                name = "Ravi Kumar",
                studentCode = "1",
                guardianName = null,
                guardianPhone = null,
                phone = "9876543210",
                photoUri = "content://photo/1",
                admissionDate = LocalDate.of(2026, 4, 1),
                notes = "Bring the book",
            ),
        )
        check(created is DataResult.Success)
        students.archive(created.value.id, LocalDate.of(2026, 9, 10))

        val form = form(created.value.id)
        form.start()
        val loaded = form.state.await { !it.loading && it.editing }
        assertEquals("Ravi Kumar", loaded.name)
        assertEquals("1", loaded.code)
        assertEquals(LocalDate.of(2026, 4, 1), loaded.admissionDate)
        form.onName("Edited Name")
        form.onNotes("Updated note")
        form.save()
        form.state.await { it.saved }

        val stored = students.get(created.value.id)
        check(stored is DataResult.Success)
        assertEquals("Edited Name", stored.value.name)
        assertEquals("Updated note", stored.value.notes)
        assertEquals("content://photo/1", stored.value.photoUri)
        assertEquals(LocalDate.of(2026, 4, 1), stored.value.admissionDate)
        assertTrue(stored.value.isArchived)
        assertEquals(created.value.createdAt, stored.value.createdAt)
    }

    @Test
    fun listSearchesByNameCodeAndPhoneAndHidesArchivedStudents() = runBlocking {
        val institute = createInstitute()
        createListed(institute.id, name = "Annika", code = "N1", phone = "9111111111")
        val archived = createListed(institute.id, name = "Ravi", code = "R2", phone = "9222222222")
        createListed(institute.id, name = "100%", code = "P1", phone = "9333333333")
        createListed(institute.id, name = "A_B", code = "U1", phone = "9444444444")
        students.archive(archived.id, LocalDate.of(2026, 9, 15))

        val list = StudentListViewModel(institutes, students)
        list.searchDebounceMillis = 0
        val active = list.state.awaitReady()
        assertEquals(listOf("100%", "A_B", "Annika"), active.rows.map { it.name })
        assertTrue(active.rows.none { it.archived })

        list.onQuery("ann")
        assertEquals(listOf("Annika"), list.state.awaitReady { it.query == "ann" }.rows.map { it.name })
        list.onQuery("n1")
        assertEquals(listOf("Annika"), list.state.awaitReady { it.query == "n1" }.rows.map { it.name })
        list.onQuery("94444")
        assertEquals(listOf("A_B"), list.state.awaitReady { it.query == "94444" }.rows.map { it.name })
        list.onQuery("%")
        assertEquals(listOf("100%"), list.state.awaitReady { it.query == "%" }.rows.map { it.name })
        list.onQuery("_")
        assertEquals(listOf("A_B"), list.state.awaitReady { it.query == "_" }.rows.map { it.name })
        list.onQuery("92222")
        assertTrue(list.state.awaitReady { it.query == "92222" }.noResults)

        list.onArchivedOnly(true)
        val withArchived = list.state.awaitReady { it.archivedOnly && it.query == "92222" }
        assertEquals(listOf("Ravi"), withArchived.rows.map { it.name })
        assertTrue(withArchived.rows.single().archived)
        list.onQuery("")
        val archivedOnly = list.state.awaitReady { it.archivedOnly && it.query.isEmpty() }
        assertEquals(listOf("Ravi"), archivedOnly.rows.map { it.name })

        val dashboard = DashboardViewModel(institutes, students, batches)
        val counts = dashboard.state.await { it is DashboardUiState.Ready } as DashboardUiState.Ready
        assertEquals(3, counts.studentCount)
    }

    @Test
    fun archiveEndsOpenBatchesAndRestoreDoesNotReopenThem() = runBlocking {
        val institute = createInstitute()
        val student = createStudent(institute.id)
        val batch = createBatch(institute.id)
        val assigned = assignments.assign(student.id, batch.id, LocalDate.of(2026, 9, 1))
        check(assigned is DataResult.Success)

        val details = details(student.id)
        val before = details.state.await {
            it is StudentDetailsUiState.Ready && it.batches.isNotEmpty()
        } as StudentDetailsUiState.Ready
        assertEquals("Algebra", before.batches.single().name)
        assertEquals("Maths", before.batches.single().subject)

        details.archive()
        val archived = details.state.await {
            it is StudentDetailsUiState.Ready && it.archived && !it.working
        } as StudentDetailsUiState.Ready
        assertTrue(archived.batches.isEmpty())

        val history = assignments.observeHistory(student.id).first { result ->
            result is DataResult.Success && result.value.singleOrNull()?.endedOn != null
        } as DataResult.Success
        assertEquals(LocalDate.of(2026, 9, 15), history.value.single().endedOn)
        assertEquals(0, activeCount(institute.id))

        val list = StudentListViewModel(institutes, students)
        list.searchDebounceMillis = 0
        assertTrue(list.state.awaitReady().rows.none { it.id == student.id })
        list.onArchivedOnly(true)
        assertTrue(
            list.state.awaitReady { it.archivedOnly }.rows.any { it.id == student.id && it.archived },
        )

        details.restore()
        val restored = details.state.await {
            it is StudentDetailsUiState.Ready && !it.archived && !it.working
        } as StudentDetailsUiState.Ready
        assertTrue(restored.batches.isEmpty())
        val stillClosed = assignments.observeHistory(student.id).first() as DataResult.Success
        assertEquals(LocalDate.of(2026, 9, 15), stillClosed.value.single().endedOn)
        assertEquals(1, activeCount(institute.id))
    }

    private fun form(studentId: String? = null) = StudentFormViewModel(
        SavedStateHandle(if (studentId == null) emptyMap() else mapOf("studentId" to studentId)),
        institutes,
        students,
        ClockLocalCalendar(clock),
        dispatchers,
    )

    private fun details(studentId: String) = StudentDetailsViewModel(
        SavedStateHandle(mapOf("studentId" to studentId)),
        students,
        assignments,
        ClockLocalCalendar(clock),
        dispatchers,
    )

    private suspend fun createListed(instituteId: String, name: String, code: String, phone: String) =
        (students.create(
            NewStudent(
                instituteId = instituteId,
                name = name,
                studentCode = code,
                guardianName = null,
                guardianPhone = null,
                phone = phone,
                photoUri = null,
                admissionDate = LocalDate.of(2026, 4, 1),
                notes = null,
            ),
        ) as DataResult.Success).value

    private suspend fun activeCount(instituteId: String): Int {
        val count = students.countActive(instituteId)
        check(count is DataResult.Success)
        return count.value
    }

    private suspend fun StateFlow<StudentListUiState>.awaitReady(
        predicate: (StudentListUiState.Ready) -> Boolean = { true },
    ): StudentListUiState.Ready = await { it is StudentListUiState.Ready && predicate(it) } as StudentListUiState.Ready
}

private suspend fun <T> StateFlow<T>.await(predicate: (T) -> Boolean): T =
    withTimeout(5_000) { first(predicate) }
