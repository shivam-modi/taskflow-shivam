package com.taskflow.task.controller

import com.fasterxml.jackson.databind.JsonNode
import com.taskflow.auth.currentClaims
import com.taskflow.task.dto.CreateTaskRequest
import com.taskflow.task.dto.TaskListResponse
import com.taskflow.task.dto.TaskResponse
import com.taskflow.task.service.TaskService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
class TaskController(private val taskService: TaskService) {

    @GetMapping("/projects/{id}/tasks")
    fun list(
        @PathVariable id: UUID,
        @RequestParam status: String?,
        @RequestParam assignee: String?,
        @RequestParam page: Int?,
        @RequestParam limit: Int?,
    ): TaskListResponse {
        return taskService.list(currentClaims().userId, id, status, assignee, page, limit)
    }

    @PostMapping("/projects/{id}/tasks")
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @PathVariable id: UUID,
        @Valid @RequestBody request: CreateTaskRequest,
    ): TaskResponse {
        return taskService.create(currentClaims().userId, id, request)
    }

    @PatchMapping("/tasks/{id}")
    fun patch(
        @PathVariable id: UUID,
        @RequestBody body: Map<String, JsonNode>,
    ): TaskResponse {
        return taskService.patch(currentClaims().userId, id, body)
    }

    @DeleteMapping("/tasks/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: UUID) {
        taskService.delete(currentClaims().userId, id)
    }
}
