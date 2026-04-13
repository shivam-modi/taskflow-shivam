package com.taskflow.project.controller

import com.fasterxml.jackson.databind.JsonNode
import com.taskflow.auth.currentClaims
import com.taskflow.common.ValidationException
import com.taskflow.project.dto.CreateProjectRequest
import com.taskflow.project.dto.MemberListResponse
import com.taskflow.project.dto.ProjectDetailResponse
import com.taskflow.project.dto.ProjectListResponse
import com.taskflow.project.dto.ProjectResponse
import com.taskflow.project.dto.ProjectStatsResponse
import com.taskflow.project.service.ProjectService
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
import java.util.Optional
import java.util.UUID

@RestController
class ProjectController(private val projectService: ProjectService) {

    @GetMapping("/projects")
    fun list(
        @RequestParam page: Int?,
        @RequestParam limit: Int?,
    ): ProjectListResponse {
        return projectService.list(currentClaims().userId, page, limit)
    }

    @PostMapping("/projects")
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody request: CreateProjectRequest): ProjectResponse {
        return projectService.create(currentClaims().userId, request)
    }

    @GetMapping("/projects/{id}")
    fun get(@PathVariable id: UUID): ProjectDetailResponse {
        return projectService.getDetail(currentClaims().userId, id)
    }

    @PatchMapping("/projects/{id}")
    fun patch(@PathVariable id: UUID, @RequestBody body: Map<String, JsonNode>): ProjectResponse {
        if (body.isEmpty()) {
            throw ValidationException(mapOf("body" to "no fields to update"))
        }

        var name: Optional<String>? = null
        var description: Optional<String>? = null

        if (body.containsKey("name")) {
            val node = body["name"]!!
            if (node.isNull || !node.isTextual) {
                throw ValidationException(mapOf("name" to "must be a non-null string"))
            }
            val v = node.asText().trim()
            if (v.isBlank()) throw ValidationException(mapOf("name" to "is required"))
            if (v.length > 200) throw ValidationException(mapOf("name" to "is too long"))
            name = Optional.of(v)
        }

        if (body.containsKey("description")) {
            val node = body["description"]!!
            if (node.isNull) {
                description = Optional.empty()
            } else {
                description = Optional.of(node.asText())
            }
        }

        if (name == null && description == null) {
            throw ValidationException(mapOf("body" to "no valid fields to update"))
        }

        return projectService.patch(currentClaims().userId, id, name, description)
    }

    @DeleteMapping("/projects/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: UUID) {
        projectService.delete(currentClaims().userId, id)
    }

    @GetMapping("/projects/{id}/stats")
    fun stats(@PathVariable id: UUID): ProjectStatsResponse {
        return projectService.stats(currentClaims().userId, id)
    }

    @GetMapping("/projects/{id}/members")
    fun members(@PathVariable id: UUID): MemberListResponse {
        return projectService.members(currentClaims().userId, id)
    }
}
