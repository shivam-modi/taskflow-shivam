package com.taskflow.project.service

import com.taskflow.project.dto.CreateProjectRequest
import com.taskflow.project.dto.MemberListResponse
import com.taskflow.project.dto.ProjectDetailResponse
import com.taskflow.project.dto.ProjectListResponse
import com.taskflow.project.dto.ProjectResponse
import com.taskflow.project.dto.ProjectStatsResponse
import java.util.Optional
import java.util.UUID

interface ProjectService {
    fun list(userId: UUID, page: Int?, limit: Int?): ProjectListResponse
    fun create(userId: UUID, request: CreateProjectRequest): ProjectResponse
    fun getDetail(userId: UUID, projectId: UUID): ProjectDetailResponse
    fun patch(userId: UUID, projectId: UUID, name: Optional<String>?, description: Optional<String>?): ProjectResponse
    fun delete(userId: UUID, projectId: UUID)
    fun stats(userId: UUID, projectId: UUID): ProjectStatsResponse
    fun members(userId: UUID, projectId: UUID): MemberListResponse
    fun ensureAccess(projectId: UUID, userId: UUID)
}
