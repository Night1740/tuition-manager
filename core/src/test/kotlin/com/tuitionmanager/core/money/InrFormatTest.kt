package com.tuitionmanager.core.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class InrFormatTest {
    @Test
    fun formatsPaiseAsIndianRupees() {
        assertEquals("₹0.00", formatInr(0))
        assertEquals("₹0.01", formatInr(1))
        assertEquals("₹0.10", formatInr(10))
        assertEquals("₹1.00", formatInr(100))
        assertEquals("₹99.50", Paise(9950).formatInr())
        assertEquals("₹1,000.00", formatInr(100_000))
        assertEquals("₹1,00,000.00", formatInr(10_000_000))
        assertEquals("₹12,34,567.89", formatInr(123_456_789))
        assertEquals("-₹99.50", formatInr(-9950))
        assertEquals("-₹0.01", formatInr(-1))
    }

    @Test
    fun arithmeticStaysInPaise() {
        assertEquals(Paise(10050), Paise(9950) + Paise(100))
        assertEquals(Paise(-9950), -Paise(9950))
    }

    @Test
    fun moneySourcesDoNotUseFloatingPoint() {
        val source = sourceRoot().resolve("src/main/kotlin/com/tuitionmanager/core/money")
        val text = source.walkTopDown().filter { it.extension == "kt" }.joinToString("\n") { it.readText() }
        assertFalse(text.contains("Double"))
        assertFalse(text.contains("Float"))
    }
}

internal fun sourceRoot(): java.io.File {
    val candidates = listOf(java.io.File("."), java.io.File("core"))
    return candidates.first { it.resolve("src/main").isDirectory }
}
