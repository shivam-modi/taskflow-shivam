package com.taskflow.task.service

import com.fasterxml.jackson.databind.JsonNode
import com.taskflow.task.dto.CreateTaskRequest
import com.taskflow.task.dto.TaskListResponse
import com.taskflow.task.dto.TaskResponse
import java.util.UUID

interface TaskService {
    fun list(userId: UUID, projectId: UUID, status: String?, assignee: String?, page: Int?, limit: Int?): TaskListResponse
    fun create(userId: UUID, projectId: UUID, request: CreateTaskRequest): TaskResponse
    fun patch(userId: UUID, taskId: UUID, body: Map<String, JsonNode>): TaskResponse
    fun delete(userId: UUID, taskId: UUID)
}
