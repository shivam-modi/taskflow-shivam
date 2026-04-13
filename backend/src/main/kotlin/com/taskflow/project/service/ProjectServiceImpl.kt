package com.taskflow.project.service

import com.taskflow.common.ForbiddenException
import com.taskflow.common.ResourceNotFoundException
import com.taskflow.common.UUIDv7
import com.taskflow.common.clampPagination
import com.taskflow.common.pageMeta
import com.taskflow.project.dto.AssigneeCount
import com.taskflow.project.dto.CreateProjectRequest
import com.taskflow.project.dto.MemberListResponse
import com.taskflow.project.dto.MemberResponse
import com.taskflow.project.dto.ProjectDetailResponse
import com.taskflow.project.dto.ProjectListResponse
import com.taskflow.project.dto.ProjectResponse
import com.taskflow.project.dto.ProjectStatsResponse
import com.taskflow.project.entity.ProjectEntity
import com.taskflow.project.entity.ProjectMemberRepository
import com.taskflow.project.entity.ProjectRepository
import com.taskflow.task.entity.TaskRepository
import mu.KotlinLogging
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Optional
import java.util.UUID

private val log = KotlinLogging.logger {}

@Service
class ProjectServiceImpl(
    private val projectRepo: ProjectRepository,
    private val projectMemberRepo: ProjectMemberRepository,
    private val taskRepo: TaskRepository,
) : ProjectService {

    @Transactional(readOnly = true)
    override fun list(userId: UUID, page: Int?, limit: Int?): ProjectListResponse {
        val (p, l) = clampPagination(page, limit)
        val result = projectRepo.findAccessibleByUser(userId, PageRequest.of(p - 1, l))
        return ProjectListResponse(
            projects = result.content.map { it.toResponse() },
            meta = pageMeta(p, l, result.totalElements),
        )
    }

    @Transactional
    override fun create(userId: UUID, request: CreateProjectRequest): ProjectResponse {
        val project = ProjectEntity(
            id = UUIDv7.generate(),
            name = request.name.trim(),
            description = request.description?.trim()?.ifBlank { null },
            ownerId = userId,
        )
        val saved = projectRepo.save(project)
        projectMemberRepo.addIfAbsent(saved.id, userId, "owner")
        log.info { "Project created: id=${saved.id}, owner=$userId" }
        return saved.toResponse()
    }

    @Transactional(readOnly = true)
    override fun getDetail(userId: UUID, projectId: UUID): ProjectDetailResponse {
        ensureAccess(projectId, userId)
        val project = projectRepo.findById(projectId)
            .orElseThrow { ResourceNotFoundException() }
        val tasks = taskRepo.findByProjectIdCapped(projectId)
        return ProjectDetailResponse(
            id = project.id,
            name = project.name,
            description = project.description,
            ownerId = project.ownerId,
            createdAt = project.createdAt,
            tasks = tasks.map { it.toResponse() },
        )
    }

    @Transactional
    override fun patch(
        userId: UUID,
        projectId: UUID,
        name: Optional<String>?,
        description: Optional<String>?,
    ): ProjectResponse {
        ensureAccess(projectId, userId)
        val project = projectRepo.findById(projectId)
            .orElseThrow { ResourceNotFoundException() }
        if (project.ownerId != userId) throw ForbiddenException()

        if (name != null && name.isPresent) {
            project.name = name.get().trim()
        }
        if (description != null) {
            project.description = if (description.isEmpty) null else description.get()
        }
        return projectRepo.save(project).toResponse()
    }

    @Transactional
    override fun delete(userId: UUID, projectId: UUID) {
        ensureAccess(projectId, userId)
        val project = projectRepo.findById(projectId)
            .orElseThrow { ResourceNotFoundException() }
        if (project.ownerId != userId) throw ForbiddenException()
        projectRepo.delete(project)
        log.info { "Project deleted: id=$projectId, by=$userId" }
    }

    @Transactional(readOnly = true)
    override fun stats(userId: UUID, projectId: UUID): ProjectStatsResponse {
        ensureAccess(projectId, userId)
        projectRepo.findById(projectId).orElseThrow { ResourceNotFoundException() }

        val byStatus = mutableMapOf("todo" to 0, "in_progress" to 0, "done" to 0)
        taskRepo.countByProjectIdGroupByStatus(projectId).forEach { row ->
            byStatus[row[0] as String] = (row[1] as Long).toInt()
        }

        val byAssignee = taskRepo.countByProjectIdGroupByAssignee(projectId).map { row ->
            AssigneeCount(
                userId = row[0] as? UUID,
                name = row[1] as? String,
                email = row[2] as? String,
                count = row[3] as Long,
            )
        }

        return ProjectStatsResponse(byStatus = byStatus, byAssignee = byAssignee)
    }

    @Transactional(readOnly = true)
    override fun members(userId: UUID, projectId: UUID): MemberListResponse {
        ensureAccess(projectId, userId)

        val rows = projectMemberRepo.findMembersWithDetails(projectId)
        return MemberListResponse(
            members = rows.map { row ->
                MemberResponse(id = row[0] as UUID, name = row[1] as String, email = row[2] as String)
            }
        )
    }

    override fun ensureAccess(projectId: UUID, userId: UUID) {
        if (!projectMemberRepo.existsByProjectIdAndUserId(projectId, userId)) {
            log.debug { "Access denied: project=$projectId, user=$userId" }
            throw ResourceNotFoundException()
        }
    }
}
