package com.tuitionmanager.core.data.local

import android.content.Context
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.room3.ColumnTypeConverters
import androidx.sqlite.driver.AndroidSQLiteDriver
import com.tuitionmanager.core.data.local.converter.TimeConverters
import com.tuitionmanager.core.data.local.dao.AttendanceDao
import com.tuitionmanager.core.data.local.dao.BatchDao
import com.tuitionmanager.core.data.local.dao.FeeObligationDao
import com.tuitionmanager.core.data.local.dao.FeePlanDao
import com.tuitionmanager.core.data.local.dao.InstituteDao
import com.tuitionmanager.core.data.local.dao.NoticeDao
import com.tuitionmanager.core.data.local.dao.PaymentDao
import com.tuitionmanager.core.data.local.dao.StudentBatchDao
import com.tuitionmanager.core.data.local.dao.StudentDao
import com.tuitionmanager.core.data.local.entity.AttendanceEntity
import com.tuitionmanager.core.data.local.entity.BatchEntity
import com.tuitionmanager.core.data.local.entity.FeeObligationEntity
import com.tuitionmanager.core.data.local.entity.FeePlanEntity
import com.tuitionmanager.core.data.local.entity.InstituteEntity
import com.tuitionmanager.core.data.local.entity.NoticeEntity
import com.tuitionmanager.core.data.local.entity.PaymentEntity
import com.tuitionmanager.core.data.local.entity.StudentBatchEntity
import com.tuitionmanager.core.data.local.entity.StudentEntity
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Schema version 1. Ship a [androidx.room3.migration.Migration] when the version increases.
 * Do not call fallbackToDestructiveMigration: a failed migration must not wipe student or payment data.
 */
@Database(
    entities = [
        InstituteEntity::class,
        StudentEntity::class,
        BatchEntity::class,
        StudentBatchEntity::class,
        AttendanceEntity::class,
        FeePlanEntity::class,
        FeeObligationEntity::class,
        PaymentEntity::class,
        NoticeEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@ColumnTypeConverters(TimeConverters::class)
abstract class TuitionDatabase : RoomDatabase() {
    abstract fun instituteDao(): InstituteDao

    abstract fun studentDao(): StudentDao

    abstract fun batchDao(): BatchDao

    abstract fun studentBatchDao(): StudentBatchDao

    abstract fun attendanceDao(): AttendanceDao

    abstract fun feePlanDao(): FeePlanDao

    abstract fun feeObligationDao(): FeeObligationDao

    abstract fun paymentDao(): PaymentDao

    abstract fun noticeDao(): NoticeDao

    companion object {
        const val NAME = "tuition_manager.db"

        fun create(
            context: Context,
            queryDispatcher: CoroutineDispatcher,
            inMemory: Boolean = false,
            allowMainThread: Boolean = false,
        ): TuitionDatabase {
            val builder = if (inMemory) {
                Room.inMemoryDatabaseBuilder<TuitionDatabase>(context)
            } else {
                Room.databaseBuilder<TuitionDatabase>(context, NAME)
            }
            if (allowMainThread) {
                builder.allowMainThreadQueries()
            }
            return builder
                .setDriver(AndroidSQLiteDriver())
                .setQueryCoroutineContext(queryDispatcher)
                .addCallback(PaymentLedgerGuard)
                .build()
        }
    }
}
