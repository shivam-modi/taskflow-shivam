package com.taskflow.task.service

import com.fasterxml.jackson.databind.JsonNode
import com.taskflow.common.ForbiddenException
import com.taskflow.common.InvalidAssigneeException
import com.taskflow.common.ResourceNotFoundException
import com.taskflow.common.UUIDv7
import com.taskflow.common.ValidationException
import com.taskflow.common.clampPagination
import com.taskflow.common.pageMeta
import com.taskflow.project.entity.ProjectMemberRepository
import com.taskflow.project.entity.ProjectRepository
import com.taskflow.project.service.ProjectService
import com.taskflow.task.dto.CreateTaskRequest
import com.taskflow.task.dto.TaskListResponse
import com.taskflow.task.dto.TaskResponse
import com.taskflow.task.entity.TaskEntity
import com.taskflow.task.entity.TaskRepository
import com.taskflow.user.entity.UserRepository
import mu.KotlinLogging
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.UUID

private val log = KotlinLogging.logger {}

private val VALID_STATUSES = setOf("todo", "in_progress", "done")
private val VALID_PRIORITIES = setOf("low", "medium", "high")

@Service
class TaskServiceImpl(
    private val taskRepo: TaskRepository,
    private val projectRepo: ProjectRepository,
    private val projectService: ProjectService,
    private val projectMemberRepo: ProjectMemberRepository,
    private val userRepo: UserRepository,
) : TaskService {

    @Transactional(readOnly = true)
    override fun list(
        userId: UUID,
        projectId: UUID,
        status: String?,
        assignee: String?,
        page: Int?,
        limit: Int?,
    ): TaskListResponse {
        projectService.ensureAccess(projectId, userId)

        if (status != null && status !in VALID_STATUSES) {
            throw ValidationException(mapOf("status" to "is invalid"))
        }
        val assigneeUuid = if (assignee != null) {
            try { UUID.fromString(assignee) } catch (_: Exception) {
                throw ValidationException(mapOf("assignee" to "must be a valid id"))
            }
        } else null

        val (p, l) = clampPagination(page, limit)
        val pageable = PageRequest.of(p - 1, l)
        val result = when {
            status != null && assigneeUuid != null -> taskRepo.findByProjectAndStatusAndAssignee(projectId, status, assigneeUuid, pageable)
            status != null -> taskRepo.findByProjectAndStatus(projectId, status, pageable)
            assigneeUuid != null -> taskRepo.findByProjectAndAssignee(projectId, assigneeUuid, pageable)
            else -> taskRepo.findByProject(projectId, pageable)
        }
        return TaskListResponse(
            tasks = result.content.map { it.toResponse() },
            meta = pageMeta(p, l, result.totalElements),
        )
    }

    @Transactional
    override fun create(userId: UUID, projectId: UUID, request: CreateTaskRequest): TaskResponse {
        projectService.ensureAccess(projectId, userId)

        val priority = request.priority ?: "medium"
        if (priority !in VALID_PRIORITIES) {
            throw ValidationException(mapOf("priority" to "is invalid"))
        }
        if (request.assigneeId != null && !userRepo.existsById(request.assigneeId)) {
            throw InvalidAssigneeException()
        }

        val task = TaskEntity(
            id = UUIDv7.generate(),
            title = request.title.trim(),
            description = request.description?.trim()?.ifBlank { null },
            status = "todo",
            priority = priority,
            projectId = projectId,
            assigneeId = request.assigneeId,
            creatorId = userId,
            dueDate = request.dueDate,
        )
        val saved = taskRepo.save(task)
        if (request.assigneeId != null) {
            projectMemberRepo.addIfAbsent(projectId, request.assigneeId, "member")
        }
        log.info { "Task created: id=${saved.id}, project=$projectId, creator=$userId" }
        return saved.toResponse()
    }

    @Transactional
    override fun patch(userId: UUID, taskId: UUID, body: Map<String, JsonNode>): TaskResponse {
        if (body.isEmpty()) {
            throw ValidationException(mapOf("body" to "no fields to update"))
        }

        val task = taskRepo.findById(taskId).orElseThrow { ResourceNotFoundException() }
        projectService.ensureAccess(task.projectId, userId)

        val errors = mutableMapOf<String, String>()

        if (body.containsKey("title")) {
            val node = body["title"]!!
            if (!node.isTextual) {
                errors["title"] = "must be a string"
            } else {
                val v = node.asText().trim()
                if (v.isBlank()) errors["title"] = "cannot be empty"
                else if (v.length > 500) errors["title"] = "is too long"
                else task.title = v
            }
        }

        if (body.containsKey("description")) {
            val node = body["description"]!!
            if (node.isNull) task.description = null
            else if (node.isTextual) task.description = node.asText()
            else errors["description"] = "must be a string or null"
        }

        if (body.containsKey("status")) {
            val node = body["status"]!!
            if (!node.isTextual) errors["status"] = "must be a string"
            else if (node.asText() !in VALID_STATUSES) errors["status"] = "is invalid"
            else task.status = node.asText()
        }

        if (body.containsKey("priority")) {
            val node = body["priority"]!!
            if (!node.isTextual) errors["priority"] = "must be a string"
            else if (node.asText() !in VALID_PRIORITIES) errors["priority"] = "is invalid"
            else task.priority = node.asText()
        }

        if (body.containsKey("assignee_id")) {
            val node = body["assignee_id"]!!
            if (node.isNull) {
                task.assigneeId = null
            } else if (node.isTextual) {
                val uid = try { UUID.fromString(node.asText()) } catch (_: Exception) {
                    throw ValidationException(mapOf("assignee_id" to "must be a valid id"))
                }
                if (!userRepo.existsById(uid)) throw InvalidAssigneeException()
                task.assigneeId = uid
            } else {
                errors["assignee_id"] = "must be a uuid or null"
            }
        }

        if (body.containsKey("due_date")) {
            val node = body["due_date"]!!
            if (node.isNull) {
                task.dueDate = null
            } else if (node.isTextual) {
                try {
                    task.dueDate = LocalDate.parse(node.asText())
                } catch (_: Exception) {
                    errors["due_date"] = "use YYYY-MM-DD"
                }
            } else {
                errors["due_date"] = "must be YYYY-MM-DD or null"
            }
        }

        if (errors.isNotEmpty()) throw ValidationException(errors)

        val saved = taskRepo.save(task)
        if (task.assigneeId != null) {
            projectMemberRepo.addIfAbsent(task.projectId, task.assigneeId!!, "member")
        }
        return saved.toResponse()
    }

    @Transactional
    override fun delete(userId: UUID, taskId: UUID) {
        val task = taskRepo.findById(taskId).orElseThrow { ResourceNotFoundException() }
        projectService.ensureAccess(task.projectId, userId)
        val project = projectRepo.findById(task.projectId).orElseThrow { ResourceNotFoundException() }

        if (project.ownerId != userId && task.creatorId != userId) {
            log.warn { "Task delete forbidden: id=$taskId, user=$userId" }
            throw ForbiddenException()
        }
        taskRepo.delete(task)
        log.info { "Task deleted: id=$taskId, by=$userId" }
    }
}
