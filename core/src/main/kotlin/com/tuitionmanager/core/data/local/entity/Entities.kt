package com.tuitionmanager.core.data.local.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import java.time.Instant
import java.time.LocalDate

@Entity(tableName = "institutes")
data class InstituteEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "owner_name") val ownerName: String,
    @ColumnInfo(name = "phone") val phone: String?,
    @ColumnInfo(name = "address") val address: String?,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
)

/**
 * Guardian name and phone live on the student and are optional. Adult students often have
 * no guardian. Validation still requires a guardian phone or a student phone.
 * A separate guardian table can be extracted later if a student needs more than one contact.
 */
@Entity(
    tableName = "students",
    foreignKeys = [
        ForeignKey(
            entity = InstituteEntity::class,
            parentColumns = ["id"],
            childColumns = ["institute_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["institute_id", "student_code"], unique = true),
        Index(value = ["institute_id", "archived_at"]),
    ],
)
data class StudentEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "institute_id") val instituteId: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "student_code", collate = ColumnInfo.NOCASE) val studentCode: String,
    @ColumnInfo(name = "guardian_name") val guardianName: String?,
    @ColumnInfo(name = "guardian_phone") val guardianPhone: String?,
    @ColumnInfo(name = "phone") val phone: String?,
    @ColumnInfo(name = "photo_uri") val photoUri: String?,
    @ColumnInfo(name = "admission_date") val admissionDate: LocalDate,
    @ColumnInfo(name = "notes") val notes: String?,
    @ColumnInfo(name = "archived_at") val archivedAt: Instant?,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
)

@Entity(
    tableName = "batches",
    foreignKeys = [
        ForeignKey(
            entity = InstituteEntity::class,
            parentColumns = ["id"],
            childColumns = ["institute_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["institute_id", "archived_at"]),
    ],
)
data class BatchEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "institute_id") val instituteId: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "subject") val subject: String,
    @ColumnInfo(name = "days_of_week") val daysOfWeek: String,
    @ColumnInfo(name = "start_minute") val startMinute: Int,
    @ColumnInfo(name = "end_minute") val endMinute: Int,
    @ColumnInfo(name = "room") val room: String?,
    @ColumnInfo(name = "capacity") val capacity: Int,
    @ColumnInfo(name = "archived_at") val archivedAt: Instant?,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
)

/**
 * History of batch membership. [activeSlot] is `"studentId|batchId"` while the row is open
 * and null after it ends. A unique index on that nullable column allows many closed rows
 * (SQLite permits multiple NULLs) and only one open row per student+batch. A student can
 * still be open in several batches at once.
 *
 * [endedOn] is the first day the student is no longer in the batch (exclusive end).
 */
@Entity(
    tableName = "student_batch",
    foreignKeys = [
        ForeignKey(
            entity = StudentEntity::class,
            parentColumns = ["id"],
            childColumns = ["student_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = BatchEntity::class,
            parentColumns = ["id"],
            childColumns = ["batch_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["active_slot"], unique = true),
        Index(value = ["student_id", "ended_on"]),
        Index(value = ["batch_id", "ended_on"]),
    ],
)
data class StudentBatchEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "student_id") val studentId: String,
    @ColumnInfo(name = "batch_id") val batchId: String,
    @ColumnInfo(name = "started_on") val startedOn: LocalDate,
    @ColumnInfo(name = "ended_on") val endedOn: LocalDate?,
    @ColumnInfo(name = "active_slot") val activeSlot: String?,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
)

@Entity(
    tableName = "attendance",
    foreignKeys = [
        ForeignKey(
            entity = StudentEntity::class,
            parentColumns = ["id"],
            childColumns = ["student_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = BatchEntity::class,
            parentColumns = ["id"],
            childColumns = ["batch_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["student_id", "batch_id", "session_date"], unique = true),
        Index(value = ["batch_id"]),
        Index(value = ["session_date"]),
    ],
)
data class AttendanceEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "student_id") val studentId: String,
    @ColumnInfo(name = "batch_id") val batchId: String,
    @ColumnInfo(name = "session_date") val sessionDate: LocalDate,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "marked_at") val markedAt: Instant,
)

@Entity(
    tableName = "fee_plans",
    foreignKeys = [
        ForeignKey(
            entity = InstituteEntity::class,
            parentColumns = ["id"],
            childColumns = ["institute_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = BatchEntity::class,
            parentColumns = ["id"],
            childColumns = ["batch_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["institute_id"]),
        Index(value = ["batch_id"]),
    ],
)
data class FeePlanEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "institute_id") val instituteId: String,
    @ColumnInfo(name = "batch_id") val batchId: String?,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "amount_paise") val amountPaise: Long,
    @ColumnInfo(name = "cadence") val cadence: String,
    @ColumnInfo(name = "archived_at") val archivedAt: Instant?,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
)

@Entity(
    tableName = "fee_obligations",
    foreignKeys = [
        ForeignKey(
            entity = StudentEntity::class,
            parentColumns = ["id"],
            childColumns = ["student_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = FeePlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["fee_plan_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["student_id", "fee_plan_id", "period_key"], unique = true),
        Index(value = ["fee_plan_id"]),
    ],
)
data class FeeObligationEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "student_id") val studentId: String,
    @ColumnInfo(name = "fee_plan_id") val feePlanId: String,
    @ColumnInfo(name = "period_key") val periodKey: String,
    @ColumnInfo(name = "amount_paise") val amountPaise: Long,
    @ColumnInfo(name = "due_date") val dueDate: LocalDate?,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
)

/**
 * Append-only money ledger. A receipt is a positive [amountPaise]. A correction is a new row
 * with a negative amount and [reversesPaymentId] pointing at the original. Rows are not updated
 * or deleted; [PaymentLedgerGuard] also rejects UPDATE and DELETE in SQLite.
 */
@Entity(
    tableName = "payments",
    foreignKeys = [
        ForeignKey(
            entity = StudentEntity::class,
            parentColumns = ["id"],
            childColumns = ["student_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = FeeObligationEntity::class,
            parentColumns = ["id"],
            childColumns = ["obligation_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = PaymentEntity::class,
            parentColumns = ["id"],
            childColumns = ["reverses_payment_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["student_id", "recorded_at"]),
        Index(value = ["obligation_id"]),
        Index(value = ["reverses_payment_id"]),
    ],
)
data class PaymentEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "student_id") val studentId: String,
    @ColumnInfo(name = "obligation_id") val obligationId: String?,
    @ColumnInfo(name = "amount_paise") val amountPaise: Long,
    @ColumnInfo(name = "kind") val kind: String,
    @ColumnInfo(name = "reverses_payment_id") val reversesPaymentId: String?,
    @ColumnInfo(name = "note") val note: String?,
    @ColumnInfo(name = "recorded_at") val recordedAt: Instant,
)

@Entity(
    tableName = "notices",
    foreignKeys = [
        ForeignKey(
            entity = InstituteEntity::class,
            parentColumns = ["id"],
            childColumns = ["institute_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["institute_id", "created_at"]),
    ],
)
data class NoticeEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "institute_id") val instituteId: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "body") val body: String,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "archived_at") val archivedAt: Instant?,
)

internal fun activeSlotOf(studentId: String, batchId: String): String = "$studentId|$batchId"

object AttendanceStatus {
    const val PRESENT = "PRESENT"
    const val ABSENT = "ABSENT"
    const val LATE = "LATE"
}

object PaymentKind {
    const val RECEIPT = "RECEIPT"
    const val REVERSAL = "REVERSAL"
}

object FeeCadence {
    const val MONTHLY = "MONTHLY"
}
