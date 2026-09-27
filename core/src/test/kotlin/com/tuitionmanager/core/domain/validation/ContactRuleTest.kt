package com.tuitionmanager.core.domain.validation

import com.tuitionmanager.core.domain.error.InvalidCode
import org.junit.Assert.assertEquals
import org.junit.Test

class ContactRuleTest {
    @Test
    fun acceptsEitherPhoneAndNormalizesIt() {
        val guardianOnly = validateStudentContacts("Parent", "+91 98765-43210", null)
        val accepted = guardianOnly as ContactValidation.Accepted
        assertEquals("Parent", accepted.contacts.guardianName)
        assertEquals("9876543210", accepted.contacts.guardianPhone)
        assertEquals(null, accepted.contacts.studentPhone)

        val studentOnly = validateStudentContacts("  ", null, "09876543210") as ContactValidation.Accepted
        assertEquals(null, studentOnly.contacts.guardianName)
        assertEquals(null, studentOnly.contacts.guardianPhone)
        assertEquals("9876543210", studentOnly.contacts.studentPhone)
    }

    @Test
    fun requiresOnePhoneAndRejectsABadNumber() {
        assertEquals(
            InvalidCode.MissingContactPhone,
            (validateStudentContacts(null, " ", null) as ContactValidation.Rejected).code,
        )
        assertEquals(
            InvalidCode.InvalidPhone,
            (validateStudentContacts(null, "123", "9876543210") as ContactValidation.Rejected).code,
        )
    }
}
