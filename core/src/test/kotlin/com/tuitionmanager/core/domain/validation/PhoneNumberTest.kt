package com.tuitionmanager.core.domain.validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneNumberTest {
    @Test
    fun acceptsCommonIndianForms() {
        assertEquals("9876543210", nationalPhoneOrNull("9876543210"))
        assertEquals("9876543210", nationalPhoneOrNull("+91 98765 43210"))
        assertEquals("9876543210", nationalPhoneOrNull("09876543210"))
        assertEquals("9876543210", nationalPhoneOrNull("919876543210"))
        assertEquals("9876543210", nationalPhoneOrNull("+91-98765-43210"))
        assertEquals("1123456789", nationalPhoneOrNull("011-2345 6789"))
        assertEquals("1123456789", nationalPhoneOrNull("+91 11 2345 6789"))
        assertEquals("9876543210", nationalPhoneOrNull("+91 (98765) 43210"))
    }

    @Test
    fun rejectsShortOrNonNumericInput() {
        assertNull(nationalPhoneOrNull("12345"))
        assertNull(nationalPhoneOrNull("98765abc10"))
        assertNull(nationalPhoneOrNull("+1 9876543210"))
        assertNull(nationalPhoneOrNull("987654321"))
        assertNull(nationalPhoneOrNull(null))
        assertNull(nationalPhoneOrNull("   "))
    }

    @Test
    fun keepsTenDigitNumbersThatStartWith91() {
        assertEquals("9123456789", nationalPhoneOrNull("9123456789"))
    }
}
