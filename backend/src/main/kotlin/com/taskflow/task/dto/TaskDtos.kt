package com.taskflow.task.dto

import com.taskflow.common.PageMeta
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class CreateTaskRequest(
    @field:NotBlank(message = "is required")
    @field:Size(max = 500, message = "is too long")
    val title: String,
    val description: String? = null,
    val priority: String? = null,
    val assigneeId: UUID? = null,
    val dueDate: LocalDate? = null,
)

data class TaskResponse(
    val id: UUID,
    val title: String,
    val description: String?,
    val status: String,
    val priority: String,
    val projectId: UUID,
    val assigneeId: UUID?,
    val dueDate: LocalDate?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class TaskListResponse(
    val tasks: List<TaskResponse>,
    val meta: PageMeta,
)
