package com.tuitionmanager.core.domain.validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneNumberTest {
    @Test
    fun acceptsMobilesAndLandlinesInCommonForms() {
        val accepted = listOf(
            "9876543210" to "9876543210",
            "98765 43210" to "9876543210",
            "98765-43210" to "9876543210",
            "+91 98765 43210" to "9876543210",
            "+91-98765-43210" to "9876543210",
            "+91 (98765) 43210" to "9876543210",
            "91 9876543210" to "9876543210",
            "919876543210" to "9876543210",
            "09876543210" to "9876543210",
            "0 98765 43210" to "9876543210",
            "9123456789" to "9123456789",
            "011-2345 6789" to "1123456789",
            "01123456789" to "1123456789",
            "+91 11 2345 6789" to "1123456789",
            "080 2345 6789" to "8023456789",
            "040-12345678" to "4012345678",
            "022 1234 5678" to "2212345678",
        )
        for ((raw, expected) in accepted) {
            assertEquals(raw, expected, nationalPhoneOrNull(raw))
        }
    }

    @Test
    fun rejectsGarbage() {
        val rejected = listOf(
            null,
            "",
            "   ",
            "12345",
            "987654321",
            "98765432101",
            "98765abc10",
            "+1 9876543210",
            "++919876543210",
            "0000000000",
            "0123456789",
            "91",
            "abcdefghij",
            "+91 123",
            "111-222",
        )
        for (raw in rejected) {
            assertNull(raw, nationalPhoneOrNull(raw))
        }
    }

    @Test
    fun contactRuleUsesTheSameNormalization() {
        val parsed = validateStudentContacts(
            guardianName = null,
            guardianPhone = "+91 98765-43210",
            studentPhone = null,
        )
        val accepted = parsed as ContactValidation.Accepted
        assertEquals("9876543210", accepted.contacts.guardianPhone)
        assertNull(accepted.contacts.studentPhone)
    }
}
