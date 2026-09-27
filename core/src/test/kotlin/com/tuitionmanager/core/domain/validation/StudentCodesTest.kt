package com.tuitionmanager.core.domain.validation

import org.junit.Assert.assertEquals
import org.junit.Test

class StudentCodesTest {
    @Test
    fun nextFreeCodeSkipsUsedNumbersAndIgnoresNonCanonicalCodes() {
        assertEquals("1", nextFreeStudentCode(emptyList()))
        assertEquals("1", nextFreeStudentCode(listOf("A-01", "0", "01")))
        assertEquals("2", nextFreeStudentCode(listOf("1", "3")))
        assertEquals("4", nextFreeStudentCode(listOf("1", "2", "3")))
    }
}
