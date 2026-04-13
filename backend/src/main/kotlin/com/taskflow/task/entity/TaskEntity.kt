package com.taskflow.task.entity

import com.taskflow.task.dto.TaskResponse
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "tasks")
class TaskEntity(
    @Id
    val id: UUID,

    @Column(nullable = false)
    var title: String,

    var description: String? = null,

    @Column(nullable = false)
    var status: String = "todo",

    @Column(nullable = false)
    var priority: String = "medium",

    @Column(name = "project_id", nullable = false)
    val projectId: UUID,

    @Column(name = "assignee_id")
    var assigneeId: UUID? = null,

    @Column(name = "creator_id", nullable = false)
    val creatorId: UUID,

    @Column(name = "due_date")
    var dueDate: LocalDate? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
) {
    fun toResponse() = TaskResponse(
        id = id,
        title = title,
        description = description,
        status = status,
        priority = priority,
        projectId = projectId,
        assigneeId = assigneeId,
        dueDate = dueDate,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
