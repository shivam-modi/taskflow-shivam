package com.taskflow.project.entity

import com.taskflow.project.dto.ProjectResponse
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "projects")
class ProjectEntity(
    @Id
    val id: UUID,

    @Column(nullable = false)
    var name: String,

    var description: String? = null,

    @Column(name = "owner_id", nullable = false)
    val ownerId: UUID,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
) {
    fun toResponse() = ProjectResponse(
        id = id,
        name = name,
        description = description,
        ownerId = ownerId,
        createdAt = createdAt,
    )
}
