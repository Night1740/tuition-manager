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
import com.tuitionmanager.core.domain.model.Batch
import com.tuitionmanager.core.domain.model.NewBatch
import com.tuitionmanager.core.domain.model.NewInstitute
import com.tuitionmanager.core.domain.model.NewStudent
import java.time.DayOfWeek
import com.tuitionmanager.core.id.UuidV7IdGenerator
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
abstract class FeatureRoom {
    protected lateinit var db: TuitionDatabase
    protected val clock: Clock = Clock.fixed(Instant.parse("2026-09-15T08:30:00Z"), ZoneOffset.UTC)
    protected val dispatchers: DispatcherProvider = FeatureDispatchers
    protected lateinit var institutes: RoomInstituteRepository
    protected lateinit var students: RoomStudentRepository
    protected lateinit var batches: RoomBatchRepository
    protected lateinit var assignments: RoomStudentBatchRepository

    @Before
    fun openDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val ids = UuidV7IdGenerator()
        db = TuitionDatabase.create(
            context = context,
            queryDispatcher = Dispatchers.IO,
            inMemory = true,
            allowMainThread = true,
        )
        institutes = RoomInstituteRepository(db, ids, clock, dispatchers)
        students = RoomStudentRepository(db, ids, clock, dispatchers)
        batches = RoomBatchRepository(db, ids, clock, dispatchers)
        assignments = RoomStudentBatchRepository(db, ids, clock, dispatchers)
    }

    @After
    fun closeDatabase() {
        if (::db.isInitialized) db.close()
    }

    protected fun createInstitute(name: String = "Morning Maths") = runBlocking {
        val result = institutes.create(
            NewInstitute(
                name = name,
                ownerName = "Anita Sharma",
                phone = "9876543210",
                address = "Lane 4",
            ),
        )
        check(result is DataResult.Success) { result }
        result.value
    }

    protected fun createStudent(
        instituteId: String,
        name: String = "Ravi Kumar",
        code: String = "A-01",
    ) = runBlocking {
        val result = students.create(
            NewStudent(
                instituteId = instituteId,
                name = name,
                studentCode = code,
                guardianName = "Guardian",
                guardianPhone = "9876543210",
                phone = null,
                photoUri = null,
                admissionDate = LocalDate.of(2026, 4, 1),
                notes = null,
            ),
        )
        check(result is DataResult.Success) { result }
        result.value
    }

    protected fun createBatch(instituteId: String, name: String = "Algebra"): Batch = runBlocking {
        val result = batches.create(
            NewBatch(
                instituteId = instituteId,
                name = name,
                subject = "Maths",
                daysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
                startMinute = 16 * 60,
                endMinute = 17 * 60,
                room = "Hall",
                capacity = 20,
            ),
        )
        check(result is DataResult.Success) { result }
        result.value
    }
}

private object FeatureDispatchers : DispatcherProvider {
    override val io: CoroutineDispatcher = Dispatchers.Unconfined
    override val default: CoroutineDispatcher = Dispatchers.Unconfined
    override val main: CoroutineDispatcher = Dispatchers.Unconfined
}
