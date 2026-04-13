package com.taskflow.project.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import java.io.Serializable
import java.time.Instant
import java.util.UUID

data class ProjectMemberId(
    val projectId: UUID = UUID(0, 0),
    val userId: UUID = UUID(0, 0),
) : Serializable

@Entity
@Table(name = "project_members")
@IdClass(ProjectMemberId::class)
class ProjectMemberEntity(
    @Id @Column(name = "project_id") val projectId: UUID,
    @Id @Column(name = "user_id") val userId: UUID,
    val role: String = "member",
    @Column(name = "created_at", updatable = false) val createdAt: Instant = Instant.now(),
)
