package com.tuitionmanager.core.data

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
import com.tuitionmanager.core.domain.model.Institute
import com.tuitionmanager.core.domain.model.NewBatch
import com.tuitionmanager.core.domain.model.NewInstitute
import com.tuitionmanager.core.domain.model.NewStudent
import com.tuitionmanager.core.domain.model.Student
import com.tuitionmanager.core.id.Rfc9562Ids
import com.tuitionmanager.core.id.UuidV7IdGenerator
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
abstract class RoomFixture {
    protected lateinit var db: TuitionDatabase
    protected val clock: Clock = Clock.fixed(Instant.parse("2026-09-15T08:30:00Z"), ZoneOffset.UTC)
    protected val ids = UuidV7IdGenerator()
    protected val deterministicIds = Rfc9562Ids()
    protected val dispatchers: DispatcherProvider = TestDispatchers
    protected lateinit var institutes: RoomInstituteRepository
    protected lateinit var students: RoomStudentRepository
    protected lateinit var batches: RoomBatchRepository
    protected lateinit var assignments: RoomStudentBatchRepository

    @Before
    fun openDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
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

    protected fun createInstitute(name: String = "Morning Maths"): Institute = runBlocking {
        institutes.create(
            NewInstitute(
                name = name,
                ownerName = "Anita Sharma",
                phone = "9876543210",
                address = "Lane 4",
            ),
        ).success()
    }

    protected fun createStudent(
        instituteId: String,
        name: String = "Ravi Kumar",
        code: String = "A-01",
        guardianPhone: String = "9876543210",
    ): Student = runBlocking {
        students.create(
            NewStudent(
                instituteId = instituteId,
                name = name,
                studentCode = code,
                guardianName = "Guardian",
                guardianPhone = guardianPhone,
                phone = null,
                photoUri = null,
                admissionDate = LocalDate.of(2026, 4, 1),
                notes = null,
            ),
        ).success()
    }

    protected fun createBatch(
        instituteId: String,
        name: String = "Algebra",
        capacity: Int = 20,
    ): Batch = runBlocking {
        batches.create(
            NewBatch(
                instituteId = instituteId,
                name = name,
                subject = "Maths",
                daysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
                startMinute = 16 * 60,
                endMinute = 17 * 60,
                room = "Hall",
                capacity = capacity,
            ),
        ).success()
    }
}

internal object TestDispatchers : DispatcherProvider {
    override val io: CoroutineDispatcher = Dispatchers.Unconfined
    override val default: CoroutineDispatcher = Dispatchers.Unconfined
    override val main: CoroutineDispatcher = Dispatchers.Unconfined
}

internal suspend fun <T> Flow<DataResult<T>>.awaitSuccess(): T {
    val result = withTimeout(10_000) { first() }
    check(result is DataResult.Success) { result }
    return result.value
}

internal fun <T> DataResult<T>.success(): T {
    check(this is DataResult.Success) { this }
    return value
}
