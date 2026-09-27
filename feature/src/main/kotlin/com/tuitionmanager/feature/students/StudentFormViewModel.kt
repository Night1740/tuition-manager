package com.tuitionmanager.feature.students

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuitionmanager.core.domain.dispatch.DispatcherProvider
import com.tuitionmanager.core.domain.error.ConflictCode
import com.tuitionmanager.core.domain.error.DataError
import com.tuitionmanager.core.domain.error.DataResult
import com.tuitionmanager.core.domain.error.InvalidCode
import com.tuitionmanager.core.domain.model.NewStudent
import com.tuitionmanager.core.domain.model.Student
import com.tuitionmanager.core.domain.repository.InstituteRepository
import com.tuitionmanager.core.domain.repository.StudentRepository
import com.tuitionmanager.core.domain.time.LocalCalendar
import com.tuitionmanager.core.domain.validation.StudentField
import com.tuitionmanager.core.domain.validation.StudentFieldError
import com.tuitionmanager.core.domain.validation.StudentFormValidation
import com.tuitionmanager.core.domain.validation.validateStudentForm
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class StudentFormUiState(
    val editing: Boolean = false,
    val name: String = "",
    val code: String = "",
    val guardianName: String = "",
    val guardianPhone: String = "",
    val phone: String = "",
    val notes: String = "",
    val admissionDate: LocalDate? = null,
    val nameError: InvalidCode? = null,
    val codeError: InvalidCode? = null,
    val codeTaken: Boolean = false,
    val guardianNameError: InvalidCode? = null,
    val guardianPhoneError: InvalidCode? = null,
    val phoneError: InvalidCode? = null,
    val contactError: InvalidCode? = null,
    val notesError: InvalidCode? = null,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val loadError: Boolean = false,
    val storageError: Boolean = false,
    val dirty: Boolean = false,
    val saved: Boolean = false,
)

@HiltViewModel
class StudentFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val institutes: InstituteRepository,
    private val students: StudentRepository,
    private val calendar: LocalCalendar,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {
    private val studentId: String? = savedStateHandle.get<String>("studentId")
    private val _state = MutableStateFlow(StudentFormUiState(editing = studentId != null))
    val state: StateFlow<StudentFormUiState> = _state.asStateFlow()

    private var instituteId: String? = null
    private var photoUri: String? = null
    private var archivedAt: Instant? = null
    private var createdAt: Instant? = null
    private var updatedAt: Instant? = null
    private var baseline: FormSnapshot? = null
    private var started = false

    /** Loads once, when the screen is shown, so a missing institute is not cached from app start. */
    fun start() {
        if (started) return
        started = true
        load()
    }

    fun retry() {
        load()
    }

    fun onName(value: String) = edit { it.copy(name = value, nameError = null) }

    fun onCode(value: String) = edit { it.copy(code = value, codeError = null, codeTaken = false) }

    fun onGuardianName(value: String) = edit { it.copy(guardianName = value, guardianNameError = null) }

    fun onGuardianPhone(value: String) = edit {
        it.copy(guardianPhone = value, guardianPhoneError = null, contactError = null)
    }

    fun onPhone(value: String) = edit { it.copy(phone = value, phoneError = null, contactError = null) }

    fun onNotes(value: String) = edit { it.copy(notes = value, notesError = null) }

    fun onAdmissionDate(value: LocalDate) = edit { it.copy(admissionDate = value) }

    fun save() {
        val current = _state.value
        if (current.saving || current.loading || current.loadError) return
        val admissionDate = current.admissionDate ?: return
        when (
            val validated = validateStudentForm(
                name = current.name,
                studentCode = current.code,
                guardianName = current.guardianName,
                guardianPhone = current.guardianPhone,
                studentPhone = current.phone,
                notes = current.notes,
                photoUri = photoUri,
            )
        ) {
            is StudentFormValidation.Rejected -> _state.value = current.withErrors(validated.errors).copy(
                saving = false,
                storageError = false,
            )
            is StudentFormValidation.Accepted -> {
                val institute = instituteId ?: return
                _state.value = current.clearErrors().copy(saving = true, storageError = false, saved = false)
                viewModelScope.launch(dispatchers.io) {
                    val result = if (studentId == null) {
                        students.create(
                            NewStudent(
                                instituteId = institute,
                                name = validated.student.name,
                                studentCode = validated.student.studentCode,
                                guardianName = validated.student.guardianName,
                                guardianPhone = validated.student.guardianPhone,
                                phone = validated.student.phone,
                                photoUri = validated.student.photoUri,
                                admissionDate = admissionDate,
                                notes = validated.student.notes,
                            ),
                        )
                    } else {
                        val existingCreated = createdAt
                        val existingUpdated = updatedAt
                        if (existingCreated == null || existingUpdated == null) {
                            _state.value = _state.value.copy(saving = false, loadError = true)
                            return@launch
                        }
                        students.update(
                            Student(
                                id = studentId,
                                instituteId = institute,
                                name = validated.student.name,
                                studentCode = validated.student.studentCode,
                                guardianName = validated.student.guardianName,
                                guardianPhone = validated.student.guardianPhone,
                                phone = validated.student.phone,
                                photoUri = validated.student.photoUri,
                                admissionDate = admissionDate,
                                notes = validated.student.notes,
                                archivedAt = archivedAt,
                                createdAt = existingCreated,
                                updatedAt = existingUpdated,
                            ),
                        )
                    }
                    _state.value = when (result) {
                        is DataResult.Success -> {
                            baseline = _state.value.snapshot()
                            _state.value.copy(saving = false, saved = true, dirty = false)
                        }
                        is DataResult.Failure -> _state.value.copy(saving = false).applyFailure(result.error)
                    }
                }
            }
        }
    }

    private fun load() {
        _state.value = _state.value.copy(loading = true, loadError = false)
        viewModelScope.launch(dispatchers.io) {
            val institute = when (val result = institutes.get()) {
                is DataResult.Success -> result.value
                is DataResult.Failure -> null
            }
            if (institute == null) {
                _state.value = _state.value.copy(loading = false, loadError = true)
                return@launch
            }
            instituteId = institute.id
            if (studentId == null) {
                when (val suggested = students.suggestCode(institute.id)) {
                    is DataResult.Failure -> _state.value = _state.value.copy(loading = false, loadError = true)
                    is DataResult.Success -> {
                        val today = calendar.today()
                        val snapshot = FormSnapshot(
                            name = "",
                            code = suggested.value,
                            guardianName = "",
                            guardianPhone = "",
                            phone = "",
                            notes = "",
                            admissionDate = today,
                        )
                        baseline = snapshot
                        photoUri = null
                        archivedAt = null
                        createdAt = null
                        updatedAt = null
                        _state.value = snapshot.toState(editing = false)
                    }
                }
            } else {
                when (val loaded = students.get(studentId)) {
                    is DataResult.Failure -> _state.value = _state.value.copy(loading = false, loadError = true)
                    is DataResult.Success -> {
                        val student = loaded.value
                        photoUri = student.photoUri
                        archivedAt = student.archivedAt
                        createdAt = student.createdAt
                        updatedAt = student.updatedAt
                        val snapshot = FormSnapshot(
                            name = student.name,
                            code = student.studentCode,
                            guardianName = student.guardianName.orEmpty(),
                            guardianPhone = student.guardianPhone.orEmpty(),
                            phone = student.phone.orEmpty(),
                            notes = student.notes.orEmpty(),
                            admissionDate = student.admissionDate,
                        )
                        baseline = snapshot
                        _state.value = snapshot.toState(editing = true)
                    }
                }
            }
        }
    }

    private fun edit(transform: (StudentFormUiState) -> StudentFormUiState) {
        val next = transform(_state.value).copy(storageError = false)
        _state.value = next.copy(dirty = baseline != null && next.snapshot() != baseline)
    }
}

private data class FormSnapshot(
    val name: String,
    val code: String,
    val guardianName: String,
    val guardianPhone: String,
    val phone: String,
    val notes: String,
    val admissionDate: LocalDate,
)

private fun FormSnapshot.toState(editing: Boolean) = StudentFormUiState(
    editing = editing,
    name = name,
    code = code,
    guardianName = guardianName,
    guardianPhone = guardianPhone,
    phone = phone,
    notes = notes,
    admissionDate = admissionDate,
    loading = false,
)

private fun StudentFormUiState.snapshot() = FormSnapshot(
    name = name,
    code = code,
    guardianName = guardianName,
    guardianPhone = guardianPhone,
    phone = phone,
    notes = notes,
    admissionDate = admissionDate ?: LocalDate.of(1970, 1, 1),
)

private fun StudentFormUiState.clearErrors() = copy(
    nameError = null,
    codeError = null,
    codeTaken = false,
    guardianNameError = null,
    guardianPhoneError = null,
    phoneError = null,
    contactError = null,
    notesError = null,
)

private fun StudentFormUiState.withErrors(errors: List<StudentFieldError>): StudentFormUiState {
    var next = clearErrors()
    for (error in errors) {
        next = when (error.field) {
            StudentField.Name -> next.copy(nameError = error.code)
            StudentField.Code -> next.copy(codeError = error.code)
            StudentField.GuardianName -> next.copy(guardianNameError = error.code)
            StudentField.GuardianPhone -> next.copy(guardianPhoneError = error.code)
            StudentField.StudentPhone -> next.copy(phoneError = error.code)
            StudentField.EitherPhone -> next.copy(contactError = error.code)
            StudentField.Notes -> next.copy(notesError = error.code)
            StudentField.Photo -> next.copy(storageError = true)
        }
    }
    return next
}

private fun StudentFormUiState.applyFailure(error: DataError): StudentFormUiState = when (error) {
    is DataError.Invalid -> withErrors(
        listOf(
            StudentFieldError(
                field = when (error.code) {
                    InvalidCode.BlankName, InvalidCode.NameTooLong -> StudentField.Name
                    InvalidCode.BlankStudentCode, InvalidCode.InvalidStudentCode -> StudentField.Code
                    InvalidCode.InvalidPhone -> StudentField.StudentPhone
                    InvalidCode.MissingContactPhone -> StudentField.EitherPhone
                    InvalidCode.NotesTooLong -> StudentField.Notes
                    else -> StudentField.Name
                },
                code = error.code,
            ),
        ),
    )
    is DataError.Conflict -> if (error.code == ConflictCode.DuplicateStudentCode) {
        copy(codeTaken = true, codeError = null, storageError = false)
    } else {
        copy(storageError = true)
    }
    else -> copy(storageError = true)
}
