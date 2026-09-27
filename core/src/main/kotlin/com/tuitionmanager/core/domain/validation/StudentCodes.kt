package com.tuitionmanager.core.domain.validation

/**
 * Smallest positive integer that is not already a student code in this institute.
 * Non-numeric codes (for example "A-01") do not occupy a number. Archived students
 * still hold their codes because the unique index includes them.
 */
fun nextFreeStudentCode(existingCodes: Iterable<String>): String {
    val used = HashSet<Int>()
    for (code in existingCodes) {
        val number = code.toIntOrNull() ?: continue
        if (number > 0 && number.toString() == code) used += number
    }
    var candidate = 1
    while (candidate in used) candidate++
    return candidate.toString()
}
