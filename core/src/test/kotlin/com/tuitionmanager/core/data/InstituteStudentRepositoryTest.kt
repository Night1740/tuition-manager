package com.tuitionmanager.core.data

import com.tuitionmanager.core.domain.error.ConflictCode
import com.tuitionmanager.core.domain.error.DataError
import com.tuitionmanager.core.domain.error.DataResult
import com.tuitionmanager.core.domain.error.EntityKind
import com.tuitionmanager.core.domain.error.InvalidCode
import com.tuitionmanager.core.domain.model.NewInstitute
import com.tuitionmanager.core.domain.model.NewStudent
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InstituteStudentRepositoryTest : RoomFixture() {
    @Test
    fun createsOneInstituteAndRejectsASecond() = runBlocking {
        val created = createInstitute()
        assertEquals("Morning Maths", created.name)
        assertEquals("9876543210", created.phone)
        val again = institutes.create(
            NewInstitute("Other", "Owner", null, null),
        )
        assertEquals(
            DataResult.Failure(DataError.Conflict(ConflictCode.InstituteAlreadyExists)),
            again,
        )
        assertEquals(created, institutes.observe().awaitSuccess())
    }

    @Test
    fun rejectsBlankInstituteNameAndBadPhone() = runBlocking {
        val blank = institutes.create(NewInstitute("  ", "Owner", null, null))
        assertEquals(DataResult.Failure(DataError.Invalid(InvalidCode.BlankName)), blank)
        val phone = institutes.create(NewInstitute("Class", "Owner", "12", null))
        assertEquals(DataResult.Failure(DataError.Invalid(InvalidCode.InvalidPhone)), phone)
        assertNull(institutes.get().success())
    }

    @Test
    fun studentCodeIsUniquePerInstituteIgnoringCase() = runBlocking {
        val institute = createInstitute()
        createStudent(institute.id, code = "A-01")
        val duplicate = students.create(newStudent(institute.id, code = "a-01"))
        assertEquals(
            DataResult.Failure(DataError.Conflict(ConflictCode.DuplicateStudentCode)),
            duplicate,
        )
        assertEquals(1, students.countActive(institute.id).success())
    }

    @Test
    fun archivedStudentsDropOutOfTheActiveListAndCanBeRestored() = runBlocking {
        val institute = createInstitute()
        val kept = createStudent(institute.id, name = "Kept", code = "K1")
        val archived = createStudent(institute.id, name = "Gone", code = "G1")
        students.archive(archived.id, LocalDate.of(2026, 9, 15)).success()
        assertEquals(listOf(kept.id), students.observeActive(institute.id).awaitSuccess().map { it.id })
        assertEquals(
            setOf(kept.id, archived.id),
            students.observe(institute.id, includeArchived = true).awaitSuccess().map { it.id }.toSet(),
        )
        assertTrue(students.get(archived.id).success().isArchived)
        students.restore(archived.id).success()
        assertFalse(students.get(archived.id).success().isArchived)
        assertEquals(2, students.countActive(institute.id).success())
    }

    @Test
    fun searchMatchesNameAndCodeWithoutTreatingWildcardsAsMatchAll() = runBlocking {
        val institute = createInstitute()
        createStudent(institute.id, name = "Annika", code = "N1")
        createStudent(institute.id, name = "100%", code = "P1")
        createStudent(institute.id, name = "Ravi", code = "R1")
        assertEquals(
            listOf("Annika"),
            students.observeActive(institute.id, "ann").awaitSuccess().map { it.name },
        )
        assertEquals(
            listOf("100%"),
            students.observeActive(institute.id, "%").awaitSuccess().map { it.name },
        )
        assertEquals(3, students.observeActive(institute.id, " ").awaitSuccess().size)
    }

    @Test
    fun invalidStudentIsNotStored() = runBlocking {
        val institute = createInstitute()
        val result = students.create(newStudent(institute.id, name = " "))
        assertEquals(DataResult.Failure(DataError.Invalid(InvalidCode.BlankName)), result)
        val phone = students.create(newStudent(institute.id, guardianPhone = "123"))
        assertEquals(DataResult.Failure(DataError.Invalid(InvalidCode.InvalidPhone)), phone)
        val noContact = students.create(newStudent(institute.id, guardianPhone = null, phone = " "))
        assertEquals(DataResult.Failure(DataError.Invalid(InvalidCode.MissingContactPhone)), noContact)
        assertTrue(students.observe(institute.id, includeArchived = true).awaitSuccess().isEmpty())
    }

    @Test
    fun studentCanBeStoredWithOnlyTheirOwnPhone() = runBlocking {
        val institute = createInstitute()
        val created = students.create(
            newStudent(
                institute.id,
                guardianName = "  ",
                guardianPhone = null,
                phone = "+91 98765 43210",
            ),
        ).success()
        assertNull(created.guardianName)
        assertNull(created.guardianPhone)
        assertEquals("9876543210", created.phone)
    }

    @Test
    fun suggestsTheNextFreeCodeAndKeepsArchivedCodesReserved() = runBlocking {
        val institute = createInstitute()
        assertEquals("1", students.suggestCode(institute.id).success())
        createStudent(institute.id, code = "A-01")
        assertEquals("1", students.suggestCode(institute.id).success())
        createStudent(institute.id, code = "1")
        createStudent(institute.id, code = "3")
        assertEquals("2", students.suggestCode(institute.id).success())
        val two = createStudent(institute.id, code = "2")
        students.archive(two.id, LocalDate.of(2026, 9, 15)).success()
        assertEquals("4", students.suggestCode(institute.id).success())
    }

    @Test
    fun searchMatchesPhoneDigitsAndEscapesUnderscore() = runBlocking {
        val institute = createInstitute()
        val matched = students.create(
            newStudent(
                institute.id,
                name = "A_B",
                code = "U1",
                guardianName = null,
                guardianPhone = null,
                phone = "9988776655",
            ),
        ).success()
        createStudent(institute.id, name = "AXB", code = "U2")
        assertEquals(listOf("A_B"), students.observeActive(institute.id, "_").awaitSuccess().map { it.name })
        assertEquals(
            listOf("A_B"),
            students.observeActive(institute.id, "99 88-77").awaitSuccess().map { it.name },
        )
        students.archive(matched.id, LocalDate.of(2026, 9, 15)).success()
        assertTrue(students.observeActive(institute.id, "A_B").awaitSuccess().isEmpty())
        assertEquals(
            listOf("A_B"),
            students.observeList(institute.id, archivedOnly = true, query = "A_B").awaitSuccess().map { it.name },
        )
    }

    @Test
    fun missingStudentIsNotFound() = runBlocking {
        assertEquals(
            DataResult.Failure(DataError.NotFound(EntityKind.Student)),
            students.get("missing"),
        )
    }

    @Test
    fun closedDatabaseSurfacesStorageFailureWithoutTheStudentName() = runBlocking {
        val institute = createInstitute()
        db.close()
        val result = students.create(newStudent(institute.id, name = "Secret Student Name"))
        val error = (result as DataResult.Failure).error
        assertEquals(DataError.Storage("create_student"), error)
        assertFalse(error.toString().contains("Secret"))
    }

    private fun newStudent(
        instituteId: String,
        name: String = "Ravi Kumar",
        code: String = "A-01",
        guardianName: String? = "Guardian",
        guardianPhone: String? = "9876543210",
        phone: String? = null,
    ) = NewStudent(
        instituteId = instituteId,
        name = name,
        studentCode = code,
        guardianName = guardianName,
        guardianPhone = guardianPhone,
        phone = phone,
        photoUri = null,
        admissionDate = LocalDate.of(2026, 4, 1),
        notes = null,
    )
}
