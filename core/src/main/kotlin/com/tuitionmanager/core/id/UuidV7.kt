package com.tuitionmanager.core.id

import java.util.UUID
import kotlin.random.Random

/**
 * UUIDv7 (RFC 9562): 48-bit Unix epoch milliseconds, version nibble 7, RFC variant bits.
 *
 * [UuidV7Generator] keeps a 12-bit counter in `rand_a` (RFC 9562 §6.2) so values stay
 * time-ordered when several ids are created in the same millisecond or the clock steps backward.
 */
internal object UuidV7 {
    const val MAX_TIMESTAMP_MILLIS: Long = 0xFFFF_FFFF_FFFFL
    const val MAX_RAND_A: Int = 0xFFF
    const val MAX_RAND_B: Long = 0x3FFF_FFFF_FFFF_FFFFL

    fun compose(unixTimestampMillis: Long, randA: Int, randB: Long): UUID {
        require(unixTimestampMillis in 0..MAX_TIMESTAMP_MILLIS) {
            "UUIDv7 timestamp out of range"
        }
        require(randA in 0..MAX_RAND_A) { "UUIDv7 rand_a out of range" }
        require(randB in 0..MAX_RAND_B) { "UUIDv7 rand_b out of range" }

        val mostSignificantBits =
            (unixTimestampMillis shl 16) or
                (0x7L shl 12) or
                randA.toLong()
        val leastSignificantBits = (2L shl 62) or randB
        return UUID(mostSignificantBits, leastSignificantBits)
    }

    fun timestampMillis(uuid: UUID): Long = uuid.mostSignificantBits ushr 16

    fun randA(uuid: UUID): Int = (uuid.mostSignificantBits and MAX_RAND_A.toLong()).toInt()

    fun randB(uuid: UUID): Long = uuid.leastSignificantBits and MAX_RAND_B
}

class UuidV7Generator(
    private val timeMillis: () -> Long = System::currentTimeMillis,
    private val random: Random = Random.Default,
) {
    private val lock = Any()
    private var lastTimestamp = -1L
    private var lastRandA = 0
    private var lastRandB = 0L

    fun generate(): UUID = synchronized(lock) {
        var timestamp = timeMillis().coerceAtLeast(0L)
        if (timestamp <= lastTimestamp) {
            timestamp = lastTimestamp
            if (lastRandA >= UuidV7.MAX_RAND_A) {
                timestamp = lastTimestamp + 1
                lastRandA = 0
                lastRandB = nextRandB()
            } else {
                lastRandA += 1
            }
        } else {
            lastRandA = random.nextInt(UuidV7.MAX_RAND_A + 1)
            lastRandB = nextRandB()
        }
        lastTimestamp = timestamp
        UuidV7.compose(timestamp, lastRandA, lastRandB)
    }

    private fun nextRandB(): Long = random.nextLong() and UuidV7.MAX_RAND_B
}
