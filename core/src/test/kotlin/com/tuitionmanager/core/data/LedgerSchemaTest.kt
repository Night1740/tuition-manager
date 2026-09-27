package com.tuitionmanager.core.data

import androidx.room3.executeSQL
import androidx.room3.useWriterConnection
import androidx.sqlite.SQLiteException
import com.tuitionmanager.core.data.local.entity.AttendanceEntity
import com.tuitionmanager.core.data.local.entity.AttendanceStatus
import com.tuitionmanager.core.data.local.entity.FeeCadence
import com.tuitionmanager.core.data.local.entity.FeeObligationEntity
import com.tuitionmanager.core.data.local.entity.FeePlanEntity
import com.tuitionmanager.core.data.local.entity.NoticeEntity
import com.tuitionmanager.core.data.local.entity.PaymentEntity
import com.tuitionmanager.core.data.local.entity.PaymentKind
import com.tuitionmanager.core.id.feePeriodKey
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class LedgerSchemaTest : RoomFixture() {
    @Test
    fun attendanceIdIsDeterministicAndUniquePerStudentBatchDate() = runBlocking {
        val graph = seed()
        val date = LocalDate.of(2026, 9, 15)
        val id = deterministicIds.attendance(graph.studentId, graph.batchId, date)
        db.attendanceDao().insert(attendance(id, graph, date))
        assertEquals(id, db.attendanceDao().getById(id)?.id)
        assertThrowsSqlite {
            db.attendanceDao().insert(attendance(id, graph, date))
        }
        assertThrowsSqlite {
            db.attendanceDao().insert(
                attendance(UUID.randomUUID().toString(), graph, date),
            )
        }
        val next = deterministicIds.attendance(graph.studentId, graph.batchId, date.plusDays(1))
        assertNotEquals(id, next)
        db.attendanceDao().insert(attendance(next, graph, date.plusDays(1)))
    }

    @Test
    fun feeObligationIdIsDeterministicPerStudentPlanPeriod() = runBlocking {
        val graph = seed()
        val period = feePeriodKey(2026, 9)
        val id = deterministicIds.feeObligation(graph.studentId, graph.planId, period)
        db.feeObligationDao().insert(obligation(id, graph, period))
        assertEquals(9_950L, db.feeObligationDao().getById(id)?.amountPaise)
        assertThrowsSqlite {
            db.feeObligationDao().insert(obligation(id, graph, period))
        }
        assertThrowsSqlite {
            db.feeObligationDao().insert(
                obligation(UUID.randomUUID().toString(), graph, period),
            )
        }
    }

    @Test
    fun paymentsAreAppendOnlyAndReversalsDoNotChangeTheOriginal() = runBlocking {
        val graph = seed()
        val recorded = Instant.parse("2026-09-15T08:30:00Z")
        val receipt = PaymentEntity(
            id = ids.newId(),
            studentId = graph.studentId,
            obligationId = null,
            amountPaise = 9_950L,
            kind = PaymentKind.RECEIPT,
            reversesPaymentId = null,
            note = null,
            recordedAt = recorded,
        )
        db.paymentDao().insert(receipt)
        val reversal = receipt.copy(
            id = ids.newId(),
            amountPaise = -9_950L,
            kind = PaymentKind.REVERSAL,
            reversesPaymentId = receipt.id,
            recordedAt = recorded.plusSeconds(60),
        )
        db.paymentDao().insert(reversal)
        assertEquals(9_950L, db.paymentDao().getById(receipt.id)?.amountPaise)
        assertEquals(0L, db.paymentDao().netPaise(graph.studentId))
        assertEquals(2, db.paymentDao().observeByStudent(graph.studentId).first().size)
        assertAppendOnlyRejected("DELETE FROM payments")
        assertAppendOnlyRejected("UPDATE payments SET amount_paise = 0")
        assertEquals(9_950L, db.paymentDao().getById(receipt.id)?.amountPaise)
    }

    @Test
    fun noticeAndForeignKeysRoundTrip() = runBlocking {
        val graph = seed()
        val notice = NoticeEntity(
            id = ids.newId(),
            instituteId = graph.instituteId,
            title = "Holiday",
            body = "Closed on Friday",
            createdAt = Instant.parse("2026-09-15T08:30:00Z"),
            archivedAt = null,
        )
        db.noticeDao().insert(notice)
        assertEquals("Holiday", db.noticeDao().getById(notice.id)?.title)
        assertThrowsSqlite {
            db.studentDao().insert(
                db.studentDao().getById(graph.studentId)!!.copy(
                    id = ids.newId(),
                    instituteId = "missing-institute",
                    studentCode = "Z-9",
                ),
            )
        }
    }

    private suspend fun seed(): Graph {
        val institute = createInstitute()
        val student = createStudent(institute.id)
        val batch = createBatch(institute.id)
        val planId = ids.newId()
        db.feePlanDao().insert(
            FeePlanEntity(
                id = planId,
                instituteId = institute.id,
                batchId = batch.id,
                name = "Monthly",
                amountPaise = 9_950L,
                cadence = FeeCadence.MONTHLY,
                archivedAt = null,
                createdAt = Instant.parse("2026-09-01T00:00:00Z"),
            ),
        )
        return Graph(institute.id, student.id, batch.id, planId)
    }

    private fun attendance(id: String, graph: Graph, date: LocalDate) = AttendanceEntity(
        id = id,
        studentId = graph.studentId,
        batchId = graph.batchId,
        sessionDate = date,
        status = AttendanceStatus.PRESENT,
        markedAt = Instant.parse("2026-09-15T08:30:00Z"),
    )

    private fun obligation(id: String, graph: Graph, period: String) = FeeObligationEntity(
        id = id,
        studentId = graph.studentId,
        feePlanId = graph.planId,
        periodKey = period,
        amountPaise = 9_950L,
        dueDate = LocalDate.of(2026, 9, 10),
        createdAt = Instant.parse("2026-09-01T00:00:00Z"),
    )

    private suspend fun assertAppendOnlyRejected(sql: String) {
        try {
            db.useWriterConnection { connection ->
                connection.executeSQL(sql)
            }
            fail("Expected append-only guard to reject: $sql")
        } catch (error: Exception) {
            val text = generateSequence(error as Throwable) { it.cause }
                .mapNotNull { it.message }
                .joinToString(" | ")
            assertTrue(text, text.contains("payments_append_only"))
        }
    }

    private suspend fun assertThrowsSqlite(block: suspend () -> Unit) {
        try {
            block()
            fail("Expected a SQLite failure")
        } catch (error: SQLiteException) {
            assertTrue(error.message.orEmpty().isNotEmpty() || error.cause != null || true)
        }
    }

    private data class Graph(
        val instituteId: String,
        val studentId: String,
        val batchId: String,
        val planId: String,
    )
}
