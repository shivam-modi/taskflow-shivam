package com.taskflow.project.entity

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface ProjectMemberRepository : JpaRepository<ProjectMemberEntity, ProjectMemberId> {

    fun existsByProjectIdAndUserId(projectId: UUID, userId: UUID): Boolean

    @Modifying
    @Query(
        value = "INSERT INTO project_members (project_id, user_id, role) VALUES (:projectId, :userId, :role) ON CONFLICT DO NOTHING",
        nativeQuery = true
    )
    fun addIfAbsent(projectId: UUID, userId: UUID, role: String)

    @Query(
        value = "SELECT u.id, u.name, u.email FROM users u JOIN project_members pm ON pm.user_id = u.id WHERE pm.project_id = :projectId",
        nativeQuery = true
    )
    fun findMembersWithDetails(projectId: UUID): List<Array<Any>>
}
