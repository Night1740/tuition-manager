package com.tuitionmanager.core.id

import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

/**
 * Namespaces chosen once for this app. They are not secrets. Changing them would
 * change every deterministic id already stored on a device.
 */
internal object IdNamespaces {
    val attendance: UUID = UUID.fromString("7d931b7f-60fa-4e03-927c-feeaf2e1248c")
    val feeObligation: UUID = UUID.fromString("71dcac16-7822-4dfe-8790-a6bdfa64299d")
}

/**
 * Canonical names hashed into UUIDv5 ids. The format is part of the on-disk identity:
 * do not reorder fields or change separators.
 */
internal object IdNames {
    fun attendance(studentId: String, batchId: String, epochDay: Long): String =
        "$studentId|$batchId|$epochDay"

    fun feeObligation(studentId: String, feePlanId: String, periodKey: String): String =
        "$studentId|$feePlanId|$periodKey"
}

/** `YYYY-MM` period key used inside [DeterministicIds.feeObligation]. */
fun feePeriodKey(year: Int, month: Int): String {
    require(month in 1..12) { "month out of range" }
    require(year in 1..9999) { "year out of range" }
    return "%04d-%02d".format(year, month)
}

interface IdGenerator {
    fun newId(): String
}

interface DeterministicIds {
    fun attendance(studentId: String, batchId: String, date: LocalDate): String

    fun feeObligation(studentId: String, feePlanId: String, periodKey: String): String
}

class UuidV7IdGenerator @Inject constructor() : IdGenerator {
    private val generator = UuidV7Generator()

    override fun newId(): String = generator.generate().toString()
}

class Rfc9562Ids @Inject constructor() : DeterministicIds {
    override fun attendance(studentId: String, batchId: String, date: LocalDate): String =
        UuidV5.generate(
            IdNamespaces.attendance,
            IdNames.attendance(studentId, batchId, date.toEpochDay()),
        ).toString()

    override fun feeObligation(studentId: String, feePlanId: String, periodKey: String): String =
        UuidV5.generate(
            IdNamespaces.feeObligation,
            IdNames.feeObligation(studentId, feePlanId, periodKey),
        ).toString()
}
