package com.tuitionmanager.core.di

import android.content.Context
import com.tuitionmanager.core.data.local.TuitionDatabase
import com.tuitionmanager.core.data.repository.RoomBatchRepository
import com.tuitionmanager.core.data.repository.RoomInstituteRepository
import com.tuitionmanager.core.data.repository.RoomStudentBatchRepository
import com.tuitionmanager.core.data.repository.RoomStudentRepository
import com.tuitionmanager.core.domain.dispatch.DefaultDispatcherProvider
import com.tuitionmanager.core.domain.dispatch.DispatcherProvider
import com.tuitionmanager.core.domain.repository.BatchRepository
import com.tuitionmanager.core.domain.repository.InstituteRepository
import com.tuitionmanager.core.domain.repository.StudentBatchRepository
import com.tuitionmanager.core.domain.repository.StudentRepository
import com.tuitionmanager.core.domain.time.ClockLocalCalendar
import com.tuitionmanager.core.domain.time.LocalCalendar
import com.tuitionmanager.core.id.DeterministicIds
import com.tuitionmanager.core.id.IdGenerator
import com.tuitionmanager.core.id.Rfc9562Ids
import com.tuitionmanager.core.id.UuidV7IdGenerator
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    /**
     * Instants from [Clock.instant] are UTC moments. The zone is the device zone so
     * [LocalCalendar.today] is the teacher's calendar date, not the UTC date.
     */
    @Provides
    @Singleton
    fun clock(): Clock = Clock.systemDefaultZone()

    @Provides
    @Singleton
    fun database(
        @ApplicationContext context: Context,
        dispatchers: DispatcherProvider,
    ): TuitionDatabase = TuitionDatabase.create(
        context = context,
        queryDispatcher = dispatchers.io,
    )
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun idGenerator(impl: UuidV7IdGenerator): IdGenerator

    @Binds
    @Singleton
    abstract fun deterministicIds(impl: Rfc9562Ids): DeterministicIds

    @Binds
    @Singleton
    abstract fun dispatchers(impl: DefaultDispatcherProvider): DispatcherProvider

    @Binds
    @Singleton
    abstract fun calendar(impl: ClockLocalCalendar): LocalCalendar

    @Binds
    @Singleton
    abstract fun institutes(impl: RoomInstituteRepository): InstituteRepository

    @Binds
    @Singleton
    abstract fun students(impl: RoomStudentRepository): StudentRepository

    @Binds
    @Singleton
    abstract fun batches(impl: RoomBatchRepository): BatchRepository

    @Binds
    @Singleton
    abstract fun studentBatches(impl: RoomStudentBatchRepository): StudentBatchRepository
}
