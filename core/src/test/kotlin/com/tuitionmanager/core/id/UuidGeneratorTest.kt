package com.tuitionmanager.core.id

import java.time.LocalDate
import java.util.UUID
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UuidGeneratorTest {
    @Test
    fun v7_setsVersionAndVariantBits() {
        val uuid = UuidV7.compose(
            unixTimestampMillis = 0x0189_0A5C_D0E1,
            randA = 0xABC,
            randB = 0x1234_5678_9ABCDE,
        )
        assertEquals(7, uuid.version())
        assertEquals(2, uuid.variant())
        assertEquals(2L, uuid.leastSignificantBits ushr 62)
        assertEquals(0x0189_0A5C_D0E1, UuidV7.timestampMillis(uuid))
        assertEquals(0xABC, UuidV7.randA(uuid))
        assertEquals(0x1234_5678_9ABCDE, UuidV7.randB(uuid))
    }

    @Test
    fun v7_ordersByTimeAndThenByCounter() {
        val clock = mutableListOf(5_000L, 5_000L, 4_000L, 6_000L)
        val generator = UuidV7Generator(
            timeMillis = { clock.removeAt(0) },
            random = FixedRandom(nextInt = 10, nextLong = 99L),
        )
        val ids = List(4) { generator.generate() }
        val stamps = ids.map { UuidV7.timestampMillis(it) }
        assertEquals(listOf(5_000L, 5_000L, 5_000L, 6_000L), stamps)
        val text = ids.map { it.toString() }
        assertEquals(text.sorted(), text)
        assertEquals(4, text.toSet().size)
    }

    @Test
    fun v7_counterOverflowAdvancesTimestamp() {
        val generator = UuidV7Generator(
            timeMillis = { 1_000L },
            random = FixedRandom(nextInt = UuidV7.MAX_RAND_A, nextLong = 7L),
        )
        val first = generator.generate()
        val second = generator.generate()
        assertEquals(1_000L, UuidV7.timestampMillis(first))
        assertEquals(UuidV7.MAX_RAND_A, UuidV7.randA(first))
        assertEquals(1_001L, UuidV7.timestampMillis(second))
        assertEquals(0, UuidV7.randA(second))
        assertTrue(first.toString() < second.toString())
    }

    @Test
    fun v5_matchesRfcVectors() {
        val dns = UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8")
        assertEquals(
            UUID.fromString("2ed6657d-e927-568b-95e1-2665a8aea6a2"),
            UuidV5.generate(dns, "www.example.com"),
        )
        assertEquals(
            UUID.fromString("886313e1-3b8a-5372-9b90-0c9aee199e5d"),
            UuidV5.generate(dns, "python.org"),
        )
        val sample = UuidV5.generate(dns, "www.example.com")
        assertEquals(5, sample.version())
        assertEquals(2, sample.variant())
    }

    @Test
    fun v5_isDeterministicForUtf8Names() {
        val namespace = UUID.fromString("7d931b7f-60fa-4e03-927c-feeaf2e1248c")
        val first = UuidV5.generate(namespace, "माता")
        val second = UuidV5.generate(namespace, "माता")
        assertEquals(first, second)
        assertNotEquals(first, UuidV5.generate(namespace, "माता "))
    }

    @Test
    fun attendanceAndFeeIds_matchKnownVectors() {
        val student = "018f3b2c-7a11-7c2a-8e01-23456789abcd"
        val batch = "018f3b2c-7a11-7c2b-8e02-abcdef012345"
        val plan = "018f3b2c-7a11-7c2c-8e03-0123456789ab"
        val date = LocalDate.of(2026, 9, 15)
        assertEquals(20_711L, date.toEpochDay())
        val ids = Rfc9562Ids()
        assertEquals(
            "2bafbf6e-3105-53e8-aca4-048a8c090b9d",
            ids.attendance(student, batch, date),
        )
        assertEquals(
            "371d4cfd-381f-5326-8cbf-031389b21ff9",
            ids.feeObligation(student, plan, feePeriodKey(2026, 9)),
        )
        assertNotEquals(
            ids.attendance(student, batch, date),
            ids.attendance(student, batch, date.plusDays(1)),
        )
    }

    private class FixedRandom(
        private val nextInt: Int,
        private val nextLong: Long,
    ) : Random() {
        override fun nextBits(bitCount: Int): Int = nextInt
        override fun nextInt(until: Int): Int = nextInt
        override fun nextLong(): Long = nextLong
    }
}
