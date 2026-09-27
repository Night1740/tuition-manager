package com.tuitionmanager.core.data.local

import androidx.room3.RoomDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Payments are an append-only ledger. The DAO exposes insert only; these triggers also reject
 * UPDATE and DELETE if some other code reaches the table with raw SQL.
 */
object PaymentLedgerGuard : RoomDatabase.Callback() {
    override suspend fun onCreate(connection: SQLiteConnection) {
        install(connection)
    }

    override suspend fun onOpen(connection: SQLiteConnection) {
        install(connection)
    }

    private fun install(connection: SQLiteConnection) {
        connection.execSQL(UPDATE_TRIGGER)
        connection.execSQL(DELETE_TRIGGER)
    }

    private val UPDATE_TRIGGER =
        """
        CREATE TRIGGER IF NOT EXISTS payments_forbid_update
        BEFORE UPDATE ON payments
        BEGIN
            SELECT RAISE(ABORT, 'payments_append_only');
        END
        """.trimIndent()

    private val DELETE_TRIGGER =
        """
        CREATE TRIGGER IF NOT EXISTS payments_forbid_delete
        BEFORE DELETE ON payments
        BEGIN
            SELECT RAISE(ABORT, 'payments_append_only');
        END
        """.trimIndent()
}
