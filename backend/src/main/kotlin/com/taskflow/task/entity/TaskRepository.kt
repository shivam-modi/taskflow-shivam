package com.taskflow.task.entity

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface TaskRepository : JpaRepository<TaskEntity, UUID> {

    /**
     * Capped at 200 rows — used by getDetail to embed tasks in a project response.
     * For full listing use the paginated findFiltered* methods.
     */
    @Query(
        value = "SELECT * FROM tasks t WHERE t.project_id = :projectId ORDER BY t.created_at ASC LIMIT 200",
        nativeQuery = true
    )
    fun findByProjectIdCapped(projectId: UUID): List<TaskEntity>

    // ── Filtered task listing (one method per filter combination) ───────────
    // Split into separate queries so PostgreSQL picks the optimal index per case.
    // A single query with (:status IS NULL OR ...) prevents the planner from
    // using the composite index (project_id, status) in the generic plan.

    @Query(
        value = "SELECT * FROM tasks t WHERE t.project_id = :projectId ORDER BY t.created_at ASC",
        countQuery = "SELECT COUNT(*) FROM tasks t WHERE t.project_id = :projectId",
        nativeQuery = true
    )
    fun findByProject(projectId: UUID, pageable: Pageable): Page<TaskEntity>

    @Query(
        value = "SELECT * FROM tasks t WHERE t.project_id = :projectId AND t.status = CAST(:status AS task_status) ORDER BY t.created_at ASC",
        countQuery = "SELECT COUNT(*) FROM tasks t WHERE t.project_id = :projectId AND t.status = CAST(:status AS task_status)",
        nativeQuery = true
    )
    fun findByProjectAndStatus(projectId: UUID, status: String, pageable: Pageable): Page<TaskEntity>

    @Query(
        value = "SELECT * FROM tasks t WHERE t.project_id = :projectId AND t.assignee_id = :assigneeId ORDER BY t.created_at ASC",
        countQuery = "SELECT COUNT(*) FROM tasks t WHERE t.project_id = :projectId AND t.assignee_id = :assigneeId",
        nativeQuery = true
    )
    fun findByProjectAndAssignee(projectId: UUID, assigneeId: UUID, pageable: Pageable): Page<TaskEntity>

    @Query(
        value = "SELECT * FROM tasks t WHERE t.project_id = :projectId AND t.status = CAST(:status AS task_status) AND t.assignee_id = :assigneeId ORDER BY t.created_at ASC",
        countQuery = "SELECT COUNT(*) FROM tasks t WHERE t.project_id = :projectId AND t.status = CAST(:status AS task_status) AND t.assignee_id = :assigneeId",
        nativeQuery = true
    )
    fun findByProjectAndStatusAndAssignee(projectId: UUID, status: String, assigneeId: UUID, pageable: Pageable): Page<TaskEntity>

    // ── Aggregate queries for project stats ─────────────────────────────────

    @Query(
        value = "SELECT status::text, COUNT(*) FROM tasks WHERE project_id = :projectId GROUP BY status",
        nativeQuery = true
    )
    fun countByProjectIdGroupByStatus(projectId: UUID): List<Array<Any>>

    @Query(
        value = """
            SELECT t.assignee_id, u.name, u.email, COUNT(*)
            FROM tasks t LEFT JOIN users u ON u.id = t.assignee_id
            WHERE t.project_id = :projectId
            GROUP BY t.assignee_id, u.name, u.email
            ORDER BY COUNT(*) DESC
        """,
        nativeQuery = true
    )
    fun countByProjectIdGroupByAssignee(projectId: UUID): List<Array<Any?>>

}
