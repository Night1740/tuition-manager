package com.tuitionmanager.core.data.mapper

import com.tuitionmanager.core.data.local.DaysOfWeekCodec
import com.tuitionmanager.core.data.local.entity.BatchEntity
import com.tuitionmanager.core.data.local.entity.InstituteEntity
import com.tuitionmanager.core.data.local.entity.StudentBatchEntity
import com.tuitionmanager.core.data.local.entity.StudentEntity
import com.tuitionmanager.core.domain.model.Batch
import com.tuitionmanager.core.domain.model.Institute
import com.tuitionmanager.core.domain.model.Student
import com.tuitionmanager.core.domain.model.StudentBatch

internal fun InstituteEntity.toDomain(): Institute = Institute(
    id = id,
    name = name,
    ownerName = ownerName,
    phone = phone,
    address = address,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun StudentEntity.toDomain(): Student = Student(
    id = id,
    instituteId = instituteId,
    name = name,
    studentCode = studentCode,
    guardianName = guardianName,
    guardianPhone = guardianPhone,
    phone = phone,
    photoUri = photoUri,
    admissionDate = admissionDate,
    notes = notes,
    archivedAt = archivedAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun BatchEntity.toDomain(): Batch = Batch(
    id = id,
    instituteId = instituteId,
    name = name,
    subject = subject,
    daysOfWeek = DaysOfWeekCodec.decode(daysOfWeek),
    startMinute = startMinute,
    endMinute = endMinute,
    room = room,
    capacity = capacity,
    archivedAt = archivedAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun StudentBatchEntity.toDomain(): StudentBatch = StudentBatch(
    id = id,
    studentId = studentId,
    batchId = batchId,
    startedOn = startedOn,
    endedOn = endedOn,
    createdAt = createdAt,
)
