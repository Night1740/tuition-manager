package com.tuitionmanager.feature

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.tuitionmanager.core.data.local.TuitionDatabase
import com.tuitionmanager.core.data.repository.RoomBatchRepository
import com.tuitionmanager.core.data.repository.RoomInstituteRepository
import com.tuitionmanager.core.data.repository.RoomStudentBatchRepository
import com.tuitionmanager.core.data.repository.RoomStudentRepository
import com.tuitionmanager.core.domain.dispatch.DispatcherProvider
import com.tuitionmanager.core.domain.error.DataResult
import com.tuitionmanager.core.domain.model.NewBatch
import com.tuitionmanager.core.domain.model.NewInstitute
import com.tuitionmanager.core.domain.model.NewStudent
import com.tuitionmanager.core.id.UuidV7IdGenerator
import com.tuitionmanager.feature.start.StartUiState
import com.tuitionmanager.feature.start.StartViewModel
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Closes a file-backed database and opens it again. The start destination follows the
 * institute row that survived the close.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class FileBackedPersistenceTest {
    private val clock: Clock = Clock.fixed(Instant.parse("2026-09-15T08:30:00Z"), ZoneOffset.UTC)
    private val dispatchers: DispatcherProvider = UnconfinedDispatchers
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun resetMainAndDatabase() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        context.deleteDatabase(TuitionDatabase.NAME)
    }

    @After
    fun cleanup() {
        context.deleteDatabase(TuitionDatabase.NAME)
        Dispatchers.resetMain()
    }

    @Test
    fun fileDatabaseSurvivesCloseAndSkipsOnboarding() = runBlocking {
        val ids = UuidV7IdGenerator()
        val first = open()
        try {
            val institutes = RoomInstituteRepository(first, ids, clock, dispatchers)
            val students = RoomStudentRepository(first, ids, clock, dispatchers)
            val batches = RoomBatchRepository(first, ids, clock, dispatchers)
            val assignments = RoomStudentBatchRepository(first, ids, clock, dispatchers)
            assertEquals(
                StartUiState.NeedsOnboarding,
                StartViewModel(institutes).state.first { it != StartUiState.Loading },
            )
            val institute = institutes.create(
                NewInstitute(
                    name = "Morning Maths",
                    ownerName = "Anita Sharma",
                    phone = "+91 98765-43210",
                    address = null,
                ),
            ).value()
            assertEquals("9876543210", institute.phone)
            val student = students.create(
                NewStudent(
                    instituteId = institute.id,
                    name = "Ravi Kumar",
                    studentCode = "1",
                    guardianName = null,
                    guardianPhone = null,
                    phone = "9876543210",
                    photoUri = null,
                    admissionDate = LocalDate.of(2026, 9, 15),
                    notes = null,
                ),
            ).value()
            val batch = batches.create(
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
            ).value()
            assignments.assign(student.id, batch.id, LocalDate.of(2026, 9, 15)).value()
        } finally {
            first.close()
        }

        val second = open()
        try {
            val institutes = RoomInstituteRepository(second, ids, clock, dispatchers)
            assertEquals(
                StartUiState.Ready,
                StartViewModel(institutes).state.first { it != StartUiState.Loading },
            )
            val institute = institutes.get().value()
            checkNotNull(institute)
            assertEquals("Morning Maths", institute.name)
            val students = RoomStudentRepository(second, ids, clock, dispatchers)
            val active = students.observeActive(institute.id).first().value()
            assertEquals(listOf("Ravi Kumar"), active.map { it.name })
            assertTrue(active.none { it.isArchived })
            val batches = RoomBatchRepository(second, ids, clock, dispatchers)
            assertEquals(1, batches.observeActiveCount(institute.id).first().value())
            val assignments = RoomStudentBatchRepository(second, ids, clock, dispatchers)
            val openAssignments = assignments.observeOpenAssignments(active.single().id).first().value()
            assertEquals(listOf("Algebra"), openAssignments.map { it.batch.name })
        } finally {
            second.close()
        }
    }

    private fun open(): TuitionDatabase = TuitionDatabase.create(
        context = context,
        queryDispatcher = Dispatchers.Unconfined,
        inMemory = false,
        allowMainThread = true,
    )
}

private object UnconfinedDispatchers : DispatcherProvider {
    override val io: CoroutineDispatcher = Dispatchers.Unconfined
    override val default: CoroutineDispatcher = Dispatchers.Unconfined
    override val main: CoroutineDispatcher = Dispatchers.Unconfined
}

private fun <T> DataResult<T>.value(): T {
    val success = this as DataResult.Success
    return success.value
}
