package com.tuitionmanager.core.id

import java.security.MessageDigest
import java.util.UUID

/**
 * UUIDv5 (RFC 9562 §5.5 / RFC 4122): SHA-1 of the 16-byte namespace UUID and the UTF-8 name.
 * Version nibble is 5 and the variant bits are the RFC 4122 value (`10`).
 *
 * The same namespace and name always produce the same UUID. Attendance and fee-obligation
 * ids depend on that, so the name strings in [IdNames] must not change.
 */
internal object UuidV5 {
    fun generate(namespace: UUID, name: String): UUID {
        val digest = MessageDigest.getInstance("SHA-1")
        digest.update(namespace.toRfcBytes())
        digest.update(name.toByteArray(Charsets.UTF_8))
        val hash = digest.digest()
        hash[6] = ((hash[6].toInt() and 0x0F) or 0x50).toByte()
        hash[8] = ((hash[8].toInt() and 0x3F) or 0x80).toByte()
        return hash.toUuid()
    }
}

internal fun UUID.toRfcBytes(): ByteArray {
    val bytes = ByteArray(16)
    val most = mostSignificantBits
    val least = leastSignificantBits
    for (index in 0..7) {
        bytes[index] = (most ushr ((7 - index) * 8)).toByte()
        bytes[index + 8] = (least ushr ((7 - index) * 8)).toByte()
    }
    return bytes
}

private fun ByteArray.toUuid(): UUID {
    var most = 0L
    var least = 0L
    for (index in 0..7) {
        most = (most shl 8) or (this[index].toLong() and 0xFF)
        least = (least shl 8) or (this[index + 8].toLong() and 0xFF)
    }
    return UUID(most, least)
}
