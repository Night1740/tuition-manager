package com.tuitionmanager.feature.batches

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuitionmanager.core.domain.dispatch.DispatcherProvider
import com.tuitionmanager.core.domain.error.DataError
import com.tuitionmanager.core.domain.error.DataResult
import com.tuitionmanager.core.domain.error.InvalidCode
import com.tuitionmanager.core.domain.model.Batch
import com.tuitionmanager.core.domain.model.NewBatch
import com.tuitionmanager.core.domain.repository.BatchRepository
import com.tuitionmanager.core.domain.repository.InstituteRepository
import com.tuitionmanager.core.domain.validation.BatchField
import com.tuitionmanager.core.domain.validation.BatchFieldError
import com.tuitionmanager.core.domain.validation.BatchFormValidation
import com.tuitionmanager.core.domain.validation.validateBatchForm
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BatchFormUiState(
    val editing: Boolean = false,
    val name: String = "",
    val subject: String = "",
    val days: Set<DayOfWeek> = emptySet(),
    val startMinute: Int = DEFAULT_START_MINUTE,
    val endMinute: Int = DEFAULT_END_MINUTE,
    val room: String = "",
    val capacity: String = "",
    val nameError: InvalidCode? = null,
    val subjectError: InvalidCode? = null,
    val daysError: InvalidCode? = null,
    val timeError: InvalidCode? = null,
    val roomError: InvalidCode? = null,
    val capacityError: InvalidCode? = null,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val loadError: Boolean = false,
    val storageError: Boolean = false,
    val dirty: Boolean = false,
    val saved: Boolean = false,
)

@HiltViewModel
class BatchFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val institutes: InstituteRepository,
    private val batches: BatchRepository,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {
    private val batchId: String? = savedStateHandle.get<String>("batchId")
    private val _state = MutableStateFlow(BatchFormUiState(editing = batchId != null))
    val state: StateFlow<BatchFormUiState> = _state.asStateFlow()

    private var instituteId: String? = null
    private var archivedAt: Instant? = null
    private var createdAt: Instant? = null
    private var updatedAt: Instant? = null
    private var baseline: FormSnapshot? = null
    private var started = false

    fun start() {
        if (started) return
        started = true
        load()
    }

    fun retry() {
        load()
    }

    fun onName(value: String) = edit { it.copy(name = value, nameError = null) }

    fun onSubject(value: String) = edit { it.copy(subject = value, subjectError = null) }

    fun onRoom(value: String) = edit { it.copy(room = value, roomError = null) }

    fun onCapacity(value: String) = edit { it.copy(capacity = value, capacityError = null) }

    fun onToggleDay(day: DayOfWeek) = edit { current ->
        val days = if (day in current.days) current.days - day else current.days + day
        current.copy(days = days, daysError = null)
    }

    fun onStartMinute(minute: Int) = edit { it.copy(startMinute = minute, timeError = null) }

    fun onEndMinute(minute: Int) = edit { it.copy(endMinute = minute, timeError = null) }

    fun save() {
        val current = _state.value
        if (current.saving || current.loading || current.loadError) return
        val capacity = current.capacity.trim().toIntOrNull()
        when (
            val validated = validateBatchForm(
                name = current.name,
                subject = current.subject,
                days = current.days,
                startMinute = current.startMinute,
                endMinute = current.endMinute,
                room = current.room,
                capacity = capacity,
            )
        ) {
            is BatchFormValidation.Rejected -> _state.value = current.withErrors(validated.errors).copy(
                saving = false,
                storageError = false,
            )
            is BatchFormValidation.Accepted -> {
                val institute = instituteId ?: return
                _state.value = current.clearErrors().copy(saving = true, storageError = false, saved = false)
                viewModelScope.launch(dispatchers.io) {
                    val result = if (batchId == null) {
                        batches.create(
                            NewBatch(
                                instituteId = institute,
                                name = validated.batch.name,
                                subject = validated.batch.subject,
                                daysOfWeek = validated.batch.daysOfWeek,
                                startMinute = validated.batch.startMinute,
                                endMinute = validated.batch.endMinute,
                                room = validated.batch.room,
                                capacity = validated.batch.capacity,
                            ),
                        )
                    } else {
                        val existingCreated = createdAt
                        val existingUpdated = updatedAt
                        if (existingCreated == null || existingUpdated == null) {
                            _state.value = _state.value.copy(saving = false, loadError = true)
                            return@launch
                        }
                        batches.update(
                            Batch(
                                id = batchId,
                                instituteId = institute,
                                name = validated.batch.name,
                                subject = validated.batch.subject,
                                daysOfWeek = validated.batch.daysOfWeek,
                                startMinute = validated.batch.startMinute,
                                endMinute = validated.batch.endMinute,
                                room = validated.batch.room,
                                capacity = validated.batch.capacity,
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
            val id = batchId
            if (id == null) {
                val snapshot = freshSnapshot()
                baseline = snapshot
                _state.value = snapshot.toState(editing = false)
                return@launch
            }
            when (val result = batches.get(id)) {
                is DataResult.Failure -> _state.value = _state.value.copy(loading = false, loadError = true)
                is DataResult.Success -> {
                    val batch = result.value
                    archivedAt = batch.archivedAt
                    createdAt = batch.createdAt
                    updatedAt = batch.updatedAt
                    val snapshot = FormSnapshot(
                        name = batch.name,
                        subject = batch.subject,
                        days = batch.daysOfWeek,
                        startMinute = batch.startMinute,
                        endMinute = batch.endMinute,
                        room = batch.room.orEmpty(),
                        capacity = batch.capacity.toString(),
                    )
                    baseline = snapshot
                    _state.value = snapshot.toState(editing = true)
                }
            }
        }
    }

    private fun edit(transform: (BatchFormUiState) -> BatchFormUiState) {
        val next = transform(_state.value)
        _state.value = next.copy(dirty = next.snapshot() != baseline, saved = false)
    }
}

private const val DEFAULT_START_MINUTE = 16 * 60
private const val DEFAULT_END_MINUTE = 17 * 60

private data class FormSnapshot(
    val name: String,
    val subject: String,
    val days: Set<DayOfWeek>,
    val startMinute: Int,
    val endMinute: Int,
    val room: String,
    val capacity: String,
)

private fun freshSnapshot() = FormSnapshot(
    name = "",
    subject = "",
    days = emptySet(),
    startMinute = DEFAULT_START_MINUTE,
    endMinute = DEFAULT_END_MINUTE,
    room = "",
    capacity = "",
)

private fun FormSnapshot.toState(editing: Boolean) = BatchFormUiState(
    editing = editing,
    name = name,
    subject = subject,
    days = days,
    startMinute = startMinute,
    endMinute = endMinute,
    room = room,
    capacity = capacity,
    loading = false,
)

private fun BatchFormUiState.snapshot() = FormSnapshot(
    name = name,
    subject = subject,
    days = days,
    startMinute = startMinute,
    endMinute = endMinute,
    room = room,
    capacity = capacity,
)

private fun BatchFormUiState.clearErrors() = copy(
    nameError = null,
    subjectError = null,
    daysError = null,
    timeError = null,
    roomError = null,
    capacityError = null,
)

private fun BatchFormUiState.withErrors(errors: List<BatchFieldError>): BatchFormUiState {
    var next = clearErrors()
    for (error in errors) {
        next = when (error.field) {
            BatchField.Name -> next.copy(nameError = error.code)
            BatchField.Subject -> next.copy(subjectError = error.code)
            BatchField.Days -> next.copy(daysError = error.code)
            BatchField.Time -> next.copy(timeError = error.code)
            BatchField.Room -> next.copy(roomError = error.code)
            BatchField.Capacity -> next.copy(capacityError = error.code)
        }
    }
    return next
}

private fun BatchFormUiState.applyFailure(error: DataError): BatchFormUiState = when (error) {
    is DataError.Invalid -> withErrors(
        listOf(
            BatchFieldError(
                field = when (error.code) {
                    InvalidCode.BlankBatchName, InvalidCode.NameTooLong -> BatchField.Name
                    InvalidCode.BlankSubject, InvalidCode.SubjectTooLong -> BatchField.Subject
                    InvalidCode.NoDaysSelected -> BatchField.Days
                    InvalidCode.EndNotAfterStart, InvalidCode.InvalidSchedule -> BatchField.Time
                    InvalidCode.RoomTooLong -> BatchField.Room
                    InvalidCode.InvalidCapacity -> BatchField.Capacity
                    else -> BatchField.Name
                },
                code = error.code,
            ),
        ),
    )
    else -> copy(storageError = true)
}
