package com.taskflow.project.dto

import com.taskflow.common.PageMeta
import com.taskflow.task.dto.TaskResponse
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class CreateProjectRequest(
    @field:NotBlank(message = "is required")
    @field:Size(max = 200, message = "is too long")
    val name: String,
    val description: String? = null,
)

data class ProjectResponse(
    val id: UUID,
    val name: String,
    val description: String?,
    val ownerId: UUID,
    val createdAt: Instant,
)

data class ProjectDetailResponse(
    val id: UUID,
    val name: String,
    val description: String?,
    val ownerId: UUID,
    val createdAt: Instant,
    val tasks: List<TaskResponse>,
)

data class ProjectListResponse(
    val projects: List<ProjectResponse>,
    val meta: PageMeta,
)

data class ProjectStatsResponse(
    val byStatus: Map<String, Int>,
    val byAssignee: List<AssigneeCount>,
)

data class AssigneeCount(
    val userId: UUID?,
    val name: String?,
    val email: String?,
    val count: Long,
)

data class MemberResponse(
    val id: UUID,
    val name: String,
    val email: String,
)

data class MemberListResponse(
    val members: List<MemberResponse>,
)
