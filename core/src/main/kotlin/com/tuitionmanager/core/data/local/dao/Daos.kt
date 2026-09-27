package com.tuitionmanager.core.data.local.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Update
import com.tuitionmanager.core.data.local.entity.AttendanceEntity
import com.tuitionmanager.core.data.local.entity.BatchEntity
import com.tuitionmanager.core.data.local.entity.FeeObligationEntity
import com.tuitionmanager.core.data.local.entity.FeePlanEntity
import com.tuitionmanager.core.data.local.entity.InstituteEntity
import com.tuitionmanager.core.data.local.entity.NoticeEntity
import com.tuitionmanager.core.data.local.entity.PaymentEntity
import com.tuitionmanager.core.data.local.entity.StudentBatchEntity
import com.tuitionmanager.core.data.local.entity.StudentEntity
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

@Dao
abstract class InstituteDao {
    @Query("SELECT * FROM institutes ORDER BY created_at ASC LIMIT 1")
    abstract fun observeFirst(): Flow<InstituteEntity?>

    @Query("SELECT * FROM institutes ORDER BY created_at ASC LIMIT 1")
    abstract suspend fun getFirst(): InstituteEntity?

    @Query("SELECT * FROM institutes WHERE id = :id")
    abstract suspend fun getById(id: String): InstituteEntity?

    @Insert
    abstract suspend fun insert(entity: InstituteEntity)

    @Update
    abstract suspend fun update(entity: InstituteEntity)

    @Query("SELECT COUNT(*) FROM institutes")
    abstract suspend fun count(): Int

    @Transaction
    open suspend fun insertFirst(entity: InstituteEntity): Boolean {
        if (count() > 0) return false
        insert(entity)
        return true
    }
}

@Dao
abstract class StudentDao {
    @Query(
        "SELECT * FROM students WHERE institute_id = :instituteId " +
            "ORDER BY name COLLATE NOCASE, student_code COLLATE NOCASE",
    )
    abstract fun observeAll(instituteId: String): Flow<List<StudentEntity>>

    @Query(
        "SELECT * FROM students WHERE institute_id = :instituteId AND archived_at IS NULL " +
            "ORDER BY name COLLATE NOCASE, student_code COLLATE NOCASE",
    )
    abstract fun observeActive(instituteId: String): Flow<List<StudentEntity>>

    @Query(
        "SELECT * FROM students WHERE institute_id = :instituteId " +
            "AND (:includeArchived = 1 OR archived_at IS NULL) " +
            "AND (name COLLATE NOCASE LIKE :pattern ESCAPE '\\' " +
            "OR student_code COLLATE NOCASE LIKE :pattern ESCAPE '\\' " +
            "OR (:digitPattern != '' AND (" +
            "IFNULL(guardian_phone, '') LIKE :digitPattern ESCAPE '\\' " +
            "OR IFNULL(phone, '') LIKE :digitPattern ESCAPE '\\'))) " +
            "ORDER BY name COLLATE NOCASE, student_code COLLATE NOCASE",
    )
    abstract fun observeMatching(
        instituteId: String,
        includeArchived: Int,
        pattern: String,
        digitPattern: String,
    ): Flow<List<StudentEntity>>

    @Query("SELECT student_code FROM students WHERE institute_id = :instituteId")
    abstract suspend fun studentCodes(instituteId: String): List<String>

    @Query("SELECT * FROM students WHERE id = :id")
    abstract fun observeById(id: String): Flow<StudentEntity?>

    @Query(
        "SELECT COUNT(*) FROM students WHERE institute_id = :instituteId AND archived_at IS NULL",
    )
    abstract suspend fun countActive(instituteId: String): Int

    @Query("SELECT * FROM students WHERE id = :id")
    abstract suspend fun getById(id: String): StudentEntity?

    @Query("SELECT * FROM students WHERE id IN (:ids)")
    abstract suspend fun getByIds(ids: List<String>): List<StudentEntity>

    @Insert
    abstract suspend fun insert(entity: StudentEntity)

    @Update
    abstract suspend fun update(entity: StudentEntity)

    @Query(
        "UPDATE students SET archived_at = :archivedAt, updated_at = :updatedAt " +
            "WHERE id = :id AND archived_at IS NULL",
    )
    abstract suspend fun archive(id: String, archivedAt: Instant, updatedAt: Instant): Int

    @Query(
        "UPDATE student_batch SET ended_on = :endedOn, active_slot = NULL " +
            "WHERE student_id = :studentId AND ended_on IS NULL",
    )
    abstract suspend fun endOpenAssignments(studentId: String, endedOn: LocalDate): Int

    /**
     * Archives the student and closes open assignments together.
     * A student who is already archived is left unchanged, including their assignment history.
     */
    @Transaction
    open suspend fun archiveAndCloseAssignments(
        id: String,
        archivedAt: Instant,
        updatedAt: Instant,
        endedOn: LocalDate,
    ): Int {
        val updated = archive(id, archivedAt, updatedAt)
        if (updated == 1) {
            endOpenAssignments(id, endedOn)
        }
        return updated
    }
}

@Dao
interface BatchDao {
    @Query(
        "SELECT * FROM batches WHERE institute_id = :instituteId " +
            "ORDER BY name COLLATE NOCASE",
    )
    fun observeAll(instituteId: String): Flow<List<BatchEntity>>

    @Query(
        "SELECT * FROM batches WHERE institute_id = :instituteId AND archived_at IS NULL " +
            "ORDER BY name COLLATE NOCASE",
    )
    fun observeActive(instituteId: String): Flow<List<BatchEntity>>

    @Query(
        "SELECT COUNT(*) FROM batches WHERE institute_id = :instituteId AND archived_at IS NULL",
    )
    suspend fun countActive(instituteId: String): Int

    @Query("SELECT * FROM batches WHERE id = :id")
    suspend fun getById(id: String): BatchEntity?

    @Query(
        "SELECT batches.* FROM batches " +
            "INNER JOIN student_batch ON student_batch.batch_id = batches.id " +
            "WHERE student_batch.student_id = :studentId AND student_batch.ended_on IS NULL " +
            "ORDER BY batches.name COLLATE NOCASE",
    )
    fun observeOpenForStudent(studentId: String): Flow<List<BatchEntity>>

    @Insert
    suspend fun insert(entity: BatchEntity)

    @Update
    suspend fun update(entity: BatchEntity)

    @Query(
        "UPDATE batches SET archived_at = :archivedAt, updated_at = :updatedAt " +
            "WHERE id = :id AND archived_at IS NULL",
    )
    suspend fun archive(id: String, archivedAt: Instant, updatedAt: Instant): Int
}

@Dao
abstract class StudentBatchDao {
    @Insert
    abstract suspend fun insert(entity: StudentBatchEntity)

    @Query("SELECT * FROM student_batch WHERE id = :id")
    abstract suspend fun getById(id: String): StudentBatchEntity?

    @Query(
        "SELECT * FROM student_batch WHERE student_id = :studentId AND batch_id = :batchId " +
            "AND ended_on IS NULL LIMIT 1",
    )
    abstract suspend fun findActive(studentId: String, batchId: String): StudentBatchEntity?

    @Query(
        "SELECT * FROM student_batch WHERE batch_id = :batchId AND ended_on IS NULL " +
            "ORDER BY started_on ASC, id ASC",
    )
    abstract fun observeActiveByBatch(batchId: String): Flow<List<StudentBatchEntity>>

    @Query(
        "SELECT * FROM student_batch WHERE student_id = :studentId " +
            "ORDER BY started_on ASC, created_at ASC, id ASC",
    )
    abstract fun observeByStudent(studentId: String): Flow<List<StudentBatchEntity>>

    @Query(
        "SELECT COUNT(*) FROM student_batch " +
            "INNER JOIN students ON students.id = student_batch.student_id " +
            "WHERE student_batch.batch_id = :batchId AND student_batch.ended_on IS NULL " +
            "AND students.archived_at IS NULL",
    )
    abstract suspend fun countActiveStudents(batchId: String): Int

    @Query(
        "UPDATE student_batch SET ended_on = :endedOn, active_slot = NULL " +
            "WHERE id = :id AND ended_on IS NULL",
    )
    abstract suspend fun end(id: String, endedOn: LocalDate): Int

    /** @return 1 inserted, -1 batch is full. */
    @Transaction
    open suspend fun insertActive(entity: StudentBatchEntity, capacity: Int): Int {
        if (countActiveStudents(entity.batchId) >= capacity) return -1
        insert(entity)
        return 1
    }

    /**
     * @return 1 moved, -1 destination is full, 0 the source assignment was no longer open.
     * Full and missing cases roll back so the source row stays unchanged.
     */
    @Transaction
    open suspend fun move(
        fromId: String,
        endedOn: LocalDate,
        created: StudentBatchEntity,
        capacity: Int,
    ): Int {
        if (countActiveStudents(created.batchId) >= capacity) return -1
        if (end(fromId, endedOn) != 1) return 0
        insert(created)
        return 1
    }
}

/** Insert and read only. Attendance behavior is a later phase. */
@Dao
interface AttendanceDao {
    @Insert
    suspend fun insert(entity: AttendanceEntity)

    @Query("SELECT * FROM attendance WHERE id = :id")
    suspend fun getById(id: String): AttendanceEntity?
}

@Dao
interface FeePlanDao {
    @Insert
    suspend fun insert(entity: FeePlanEntity)

    @Query("SELECT * FROM fee_plans WHERE id = :id")
    suspend fun getById(id: String): FeePlanEntity?
}

@Dao
interface FeeObligationDao {
    @Insert
    suspend fun insert(entity: FeeObligationEntity)

    @Query("SELECT * FROM fee_obligations WHERE id = :id")
    suspend fun getById(id: String): FeeObligationEntity?
}

/**
 * Append-only. There is no update or delete method. Corrections are new reversal rows.
 */
@Dao
interface PaymentDao {
    @Insert
    suspend fun insert(entity: PaymentEntity)

    @Query("SELECT * FROM payments WHERE id = :id")
    suspend fun getById(id: String): PaymentEntity?

    @Query(
        "SELECT * FROM payments WHERE student_id = :studentId ORDER BY recorded_at ASC, id ASC",
    )
    fun observeByStudent(studentId: String): Flow<List<PaymentEntity>>

    @Query("SELECT COALESCE(SUM(amount_paise), 0) FROM payments WHERE student_id = :studentId")
    suspend fun netPaise(studentId: String): Long
}

@Dao
interface NoticeDao {
    @Insert
    suspend fun insert(entity: NoticeEntity)

    @Query("SELECT * FROM notices WHERE id = :id")
    suspend fun getById(id: String): NoticeEntity?
}
