package com.taskflow.project.entity

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface ProjectRepository : JpaRepository<ProjectEntity, UUID> {

    @Query(
        value = """
            SELECT p.* FROM projects p
            JOIN project_members pm ON pm.project_id = p.id
            WHERE pm.user_id = :userId
            ORDER BY p.created_at DESC
        """,
        countQuery = """
            SELECT COUNT(*) FROM projects p
            JOIN project_members pm ON pm.project_id = p.id
            WHERE pm.user_id = :userId
        """,
        nativeQuery = true
    )
    fun findAccessibleByUser(userId: UUID, pageable: Pageable): Page<ProjectEntity>
}
