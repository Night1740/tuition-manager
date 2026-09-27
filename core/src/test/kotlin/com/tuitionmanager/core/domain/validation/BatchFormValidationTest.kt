package com.tuitionmanager.core.domain.validation

import com.tuitionmanager.core.domain.error.InvalidCode
import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchFormValidationTest {
    @Test
    fun capacityMustStayInsideTheStoredBounds() {
        val cases = listOf(
            null to true,
            0 to true,
            -1 to true,
            1 to false,
            20 to false,
            2000 to false,
            2001 to true,
        )
        for ((capacity, invalid) in cases) {
            val result = sample(capacity = capacity)
            if (invalid) {
                val rejected = result as BatchFormValidation.Rejected
                assertTrue(
                    capacity.toString(),
                    rejected.errors.any {
                        it.field == BatchField.Capacity && it.code == InvalidCode.InvalidCapacity
                    },
                )
            } else {
                val accepted = result as BatchFormValidation.Accepted
                assertEquals(capacity, accepted.batch.capacity)
            }
        }
    }

    @Test
    fun blankNameSubjectAndDaysAreSeparateFieldErrors() {
        val rejected = sample(
            name = "  ",
            subject = "",
            days = emptySet(),
            capacity = null,
        ) as BatchFormValidation.Rejected
        assertEquals(InvalidCode.BlankBatchName, rejected.errors.first { it.field == BatchField.Name }.code)
        assertEquals(InvalidCode.BlankSubject, rejected.errors.first { it.field == BatchField.Subject }.code)
        assertEquals(InvalidCode.NoDaysSelected, rejected.errors.first { it.field == BatchField.Days }.code)
        assertEquals(InvalidCode.InvalidCapacity, rejected.errors.first { it.field == BatchField.Capacity }.code)
    }

    private fun sample(
        name: String = "Algebra",
        subject: String = "Maths",
        days: Set<DayOfWeek> = setOf(DayOfWeek.MONDAY),
        capacity: Int? = 20,
    ): BatchFormValidation = validateBatchForm(
        name = name,
        subject = subject,
        days = days,
        startMinute = 16 * 60,
        endMinute = 17 * 60,
        room = null,
        capacity = capacity,
    )
}
