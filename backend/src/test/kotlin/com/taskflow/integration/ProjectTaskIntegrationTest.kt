package com.taskflow.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.jayway.jsonpath.JsonPath
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
class ProjectTaskIntegrationTest : TestContainerConfig() {

    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var mapper: ObjectMapper

    private fun registerAndGetToken(): String = registerUser().first

    private fun registerUser(): Pair<String, String> {
        val email = "pt-${UUID.randomUUID()}@example.com"
        val result = mvc.post("/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(mapOf("name" to "T", "email" to email, "password" to "password123"))
        }.andReturn()
        val token: String = JsonPath.read(result.response.contentAsString, "$.token")
        val userId: String = JsonPath.read(result.response.contentAsString, "$.user.id")
        return token to userId
    }

    @Test
    fun `full project and task lifecycle`() {
        val token = registerAndGetToken()

        // Create project
        val projResult = mvc.post("/projects") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $token")
            content = mapper.writeValueAsString(mapOf("name" to "Test Project", "description" to "desc"))
        }.andExpect {
            status { isCreated() }
            jsonPath("$.name") { value("Test Project") }
        }.andReturn()

        val projectId: String = JsonPath.read(projResult.response.contentAsString, "$.id")

        // List projects
        mvc.get("/projects") {
            header("Authorization", "Bearer $token")
        }.andExpect {
            status { isOk() }
            jsonPath("$.projects[0].id") { value(projectId) }
            jsonPath("$.meta.total_items") { value(1) }
        }

        // Get project detail
        mvc.get("/projects/$projectId") {
            header("Authorization", "Bearer $token")
        }.andExpect {
            status { isOk() }
            jsonPath("$.name") { value("Test Project") }
            jsonPath("$.tasks") { isArray() }
        }

        // Create task
        val taskResult = mvc.post("/projects/$projectId/tasks") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $token")
            content = mapper.writeValueAsString(mapOf("title" to "First task", "priority" to "high"))
        }.andExpect {
            status { isCreated() }
            jsonPath("$.title") { value("First task") }
            jsonPath("$.status") { value("todo") }
            jsonPath("$.priority") { value("high") }
        }.andReturn()

        val taskId: String = JsonPath.read(taskResult.response.contentAsString, "$.id")

        // Patch task status
        mvc.patch("/tasks/$taskId") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $token")
            content = """{"status":"done"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("done") }
        }

        // List tasks with filter
        mvc.get("/projects/$projectId/tasks?status=done") {
            header("Authorization", "Bearer $token")
        }.andExpect {
            status { isOk() }
            jsonPath("$.tasks.length()") { value(1) }
        }

        // Project stats
        mvc.get("/projects/$projectId/stats") {
            header("Authorization", "Bearer $token")
        }.andExpect {
            status { isOk() }
            jsonPath("$.by_status.done") { value(1) }
        }

        // Delete task
        mvc.delete("/tasks/$taskId") {
            header("Authorization", "Bearer $token")
        }.andExpect {
            status { isNoContent() }
        }

        // Patch project
        mvc.patch("/projects/$projectId") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $token")
            content = """{"name":"Updated","description":null}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.name") { value("Updated") }
            jsonPath("$.description") { doesNotExist() }
        }

        // Delete project
        mvc.delete("/projects/$projectId") {
            header("Authorization", "Bearer $token")
        }.andExpect {
            status { isNoContent() }
        }
    }

    @Test
    fun `protected endpoints return 401 without token`() {
        mvc.get("/projects").andExpect { status { isUnauthorized() } }
        mvc.post("/projects") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"x"}"""
        }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `delete task by non-owner non-creator returns 403`() {
        val ownerToken = registerAndGetToken()
        val otherToken = registerAndGetToken()

        // Owner creates project and task
        val projResult = mvc.post("/projects") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $ownerToken")
            content = mapper.writeValueAsString(mapOf("name" to "Owner Project"))
        }.andReturn()
        val projectId: String = JsonPath.read(projResult.response.contentAsString, "$.id")

        val taskResult = mvc.post("/projects/$projectId/tasks") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $ownerToken")
            content = mapper.writeValueAsString(mapOf("title" to "Owner task"))
        }.andReturn()
        val taskId: String = JsonPath.read(taskResult.response.contentAsString, "$.id")

        // Other user tries to delete — should get 404 (no access to project)
        mvc.delete("/tasks/$taskId") {
            header("Authorization", "Bearer $otherToken")
        }.andExpect {
            status { isNotFound() }
        }
    }

    @Test
    fun `patch project by non-owner returns 403`() {
        val ownerToken = registerAndGetToken()
        val otherToken = registerAndGetToken()

        val projResult = mvc.post("/projects") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $ownerToken")
            content = mapper.writeValueAsString(mapOf("name" to "Private"))
        }.andReturn()
        val projectId: String = JsonPath.read(projResult.response.contentAsString, "$.id")

        // Other user can't even see it — 404
        mvc.patch("/projects/$projectId") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $otherToken")
            content = """{"name":"Hacked"}"""
        }.andExpect {
            status { isNotFound() }
        }
    }

    @Test
    fun `filter tasks by assignee returns only matching tasks`() {
        val (ownerToken, _) = registerUser()
        val (_, assigneeId) = registerUser()

        // Create project
        val projResult = mvc.post("/projects") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $ownerToken")
            content = mapper.writeValueAsString(mapOf("name" to "Filter project"))
        }.andReturn()
        val projectId: String = JsonPath.read(projResult.response.contentAsString, "$.id")

        // Create assigned task
        mvc.post("/projects/$projectId/tasks") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $ownerToken")
            content = mapper.writeValueAsString(mapOf("title" to "Assigned", "assignee_id" to assigneeId))
        }.andExpect { status { isCreated() } }

        // Create unassigned task
        mvc.post("/projects/$projectId/tasks") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $ownerToken")
            content = mapper.writeValueAsString(mapOf("title" to "Unassigned"))
        }.andExpect { status { isCreated() } }

        // Filter by assignee — should return only the assigned task
        mvc.get("/projects/$projectId/tasks?assignee=$assigneeId") {
            header("Authorization", "Bearer $ownerToken")
        }.andExpect {
            status { isOk() }
            jsonPath("$.tasks.length()") { value(1) }
            jsonPath("$.tasks[0].title") { value("Assigned") }
            jsonPath("$.meta.total_items") { value(1) }
        }
    }

    @Test
    fun `assigning task adds user to project members`() {
        val (ownerToken, _) = registerUser()
        val (assigneeToken, assigneeId) = registerUser()

        // Create project
        val projResult = mvc.post("/projects") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $ownerToken")
            content = mapper.writeValueAsString(mapOf("name" to "Members project"))
        }.andReturn()
        val projectId: String = JsonPath.read(projResult.response.contentAsString, "$.id")

        // Assignee can't see project yet — 404
        mvc.get("/projects/$projectId") {
            header("Authorization", "Bearer $assigneeToken")
        }.andExpect { status { isNotFound() } }

        // Owner assigns a task to the assignee
        mvc.post("/projects/$projectId/tasks") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $ownerToken")
            content = mapper.writeValueAsString(mapOf("title" to "Do this", "assignee_id" to assigneeId))
        }.andExpect { status { isCreated() } }

        // Assignee is now a member — can see the project
        mvc.get("/projects/$projectId") {
            header("Authorization", "Bearer $assigneeToken")
        }.andExpect {
            status { isOk() }
            jsonPath("$.name") { value("Members project") }
        }

        // Members endpoint includes both users
        mvc.get("/projects/$projectId/members") {
            header("Authorization", "Bearer $ownerToken")
        }.andExpect {
            status { isOk() }
            jsonPath("$.members.length()") { value(2) }
        }
    }
}
