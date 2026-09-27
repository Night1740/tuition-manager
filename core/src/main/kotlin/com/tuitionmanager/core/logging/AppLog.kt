package com.tuitionmanager.core.logging

import android.util.Log

/**
 * The only production logger. Pass a stable operation token, never an entity, name, phone,
 * note, or amount. Exception messages are dropped because SQLite text can echo bound values.
 */
object AppLog {
    private const val TAG = "TuitionManager"

    fun storageFailure(operation: String, error: Throwable) {
        Log.e(TAG, "storage failure during $operation (${error.javaClass.name})")
    }
}
