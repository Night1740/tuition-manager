package com.tuitionmanager.core

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchitectureConstraintsTest {
    @Test
    fun androidLogIsOnlyUsedByAppLog() {
        val offenders = productionKotlin()
            .filter { file -> file.name != "AppLog.kt" }
            .filter { file -> file.readText().contains("android.util.Log") }
            .map { it.path }
        assertTrue(offenders.toString(), offenders.isEmpty())
    }

    @Test
    fun appLogDoesNotRecordExceptionTextOrFieldValues() {
        val source = file("core/src/main/kotlin/com/tuitionmanager/core/logging/AppLog.kt").readText()
        val call = source.substringAfter("fun storageFailure")
        assertTrue(call.contains("error.javaClass.name"))
        assertFalse(call.contains("error.message"))
        assertFalse(call.contains(".message"))
        assertFalse(call.contains("phone"))
        assertFalse(call.contains("amount"))
    }

    @Test
    fun featureDoesNotReimplementPhoneOrCapacityRules() {
        val offenders = file("feature/src/main").walkTopDown()
            .filter { it.extension == "kt" }
            .filter { file ->
                val text = file.readText()
                text.contains("nationalPhone") ||
                    text.contains("MIN_BATCH_CAPACITY") ||
                    text.contains("MAX_BATCH_CAPACITY") ||
                    text.contains("removePrefix(\"+91\")")
            }
            .map { it.path }
            .toList()
        assertTrue(offenders.toString(), offenders.isEmpty())
    }

    @Test
    fun databaseDoesNotUseDestructiveMigration() {
        val database = file("core/src/main/kotlin/com/tuitionmanager/core/data/local/TuitionDatabase.kt").readText()
        assertFalse(database.contains(".fallbackToDestructiveMigration"))
        assertFalse(database.contains("fallbackToDestructiveMigration("))
    }

    @Test
    fun paymentDaoHasNoUpdateOrDelete() {
        val daos = file("core/src/main/kotlin/com/tuitionmanager/core/data/local/dao/Daos.kt").readText()
        val paymentDao = daos.substringAfter("interface PaymentDao").substringBefore("interface NoticeDao")
        assertFalse(paymentDao.contains("@Update"))
        assertFalse(paymentDao.contains("@Delete"))
        assertFalse(paymentDao.contains("fun update"))
        assertFalse(paymentDao.contains("fun delete"))
    }

    @Test
    fun featureDoesNotImportTheDataLayer() {
        val root = file("feature/src/main")
        val offenders = root.walkTopDown()
            .filter { it.extension == "kt" }
            .filter { it.readText().contains("com.tuitionmanager.core.data") }
            .map { it.path }
            .toList()
        assertTrue(offenders.toString(), offenders.isEmpty())
    }

    @Test
    fun dependenciesStayLocalFirst() {
        val text = listOf(
            "gradle/libs.versions.toml",
            "app/build.gradle.kts",
            "core/build.gradle.kts",
            "feature/build.gradle.kts",
        ).joinToString("\n") { file(it).readText() }
        assertFalse(text.contains("firebase", ignoreCase = true))
        assertFalse(text.contains("sqlcipher", ignoreCase = true))
        assertFalse(text.contains("firestore", ignoreCase = true))
    }

    private fun productionKotlin(): List<File> =
        listOf("app/src/main", "core/src/main", "feature/src/main")
            .flatMap { path ->
                file(path).walkTopDown().filter { it.extension == "kt" }.toList()
            }

    private fun file(relative: String): File {
        val roots = listOf(File("."), File(".."))
        return roots.firstNotNullOfOrNull { root ->
            root.resolve(relative).takeIf { it.exists() }
        } ?: error("Missing $relative from ${File(".").absolutePath}")
    }
}
