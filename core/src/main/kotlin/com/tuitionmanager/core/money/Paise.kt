package com.tuitionmanager.core.money

/**
 * Indian currency in paise. ₹99.50 is [Paise] of 9950. Never store or compute money as a float.
 */
@JvmInline
value class Paise(val amount: Long) {
    operator fun plus(other: Paise): Paise = Paise(amount + other.amount)

    operator fun minus(other: Paise): Paise = Paise(amount - other.amount)

    operator fun unaryMinus(): Paise = Paise(-amount)
}

/**
 * Groups rupees in the Indian system (last 3 digits, then pairs) and always shows two paise digits.
 * Example: 9950 -> ₹99.50, 123456789 -> ₹12,34,567.89, -9950 -> -₹99.50.
 */
fun formatInr(amountPaise: Long): String {
    val negative = amountPaise < 0
    val magnitude = if (negative) 0uL - amountPaise.toULong() else amountPaise.toULong()
    val rupees = (magnitude / 100u).toString()
    val fraction = (magnitude % 100u).toInt().toString().padStart(2, '0')
    val sign = if (negative) "-" else ""
    return "$sign₹${groupIndianDigits(rupees)}.$fraction"
}

fun Paise.formatInr(): String = formatInr(amount)

internal fun groupIndianDigits(digits: String): String {
    require(digits.isNotEmpty() && digits.all { it.isDigit() })
    if (digits.length <= 3) return digits
    val tail = digits.takeLast(3)
    val head = digits.dropLast(3)
    val groupedHead = head.reversed().chunked(2).joinToString(",").reversed()
    return "$groupedHead,$tail"
}
