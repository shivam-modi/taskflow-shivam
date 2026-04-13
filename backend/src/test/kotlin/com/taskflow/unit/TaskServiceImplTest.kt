package com.taskflow.unit

import com.fasterxml.jackson.databind.ObjectMapper
import com.taskflow.common.ForbiddenException
import com.taskflow.common.InvalidAssigneeException
import com.taskflow.common.ResourceNotFoundException
import com.taskflow.common.ValidationException
import com.taskflow.project.entity.ProjectEntity
import com.taskflow.project.entity.ProjectMemberRepository
import com.taskflow.project.entity.ProjectRepository
import com.taskflow.project.service.ProjectService
import com.taskflow.task.dto.CreateTaskRequest
import com.taskflow.task.entity.TaskEntity
import com.taskflow.task.entity.TaskRepository
import com.taskflow.task.service.TaskServiceImpl
import com.taskflow.user.entity.UserRepository
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.util.Optional
import java.util.UUID

@ExtendWith(MockKExtension::class)
class TaskServiceImplTest {

    @MockK lateinit var taskRepo: TaskRepository
    @MockK lateinit var projectRepo: ProjectRepository
    @MockK lateinit var projectService: ProjectService
    @MockK lateinit var projectMemberRepo: ProjectMemberRepository
    @MockK lateinit var userRepo: UserRepository

    private lateinit var taskService: TaskServiceImpl
    private val mapper = ObjectMapper()
    private val userId = UUID.randomUUID()
    private val projectId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        taskService = TaskServiceImpl(taskRepo, projectRepo, projectService, projectMemberRepo, userRepo)
    }

    private fun task(
        id: UUID = UUID.randomUUID(),
        creator: UUID = userId,
        project: UUID = projectId,
    ) = TaskEntity(
        id = id, title = "Task", projectId = project, creatorId = creator,
    )

    @Suppress("UNCHECKED_CAST")
    private fun jsonBody(json: String): Map<String, com.fasterxml.jackson.databind.JsonNode> {
        val raw = mapper.readValue(json, Map::class.java) as Map<String, Any>
        return raw.mapValues { mapper.valueToTree(it.value) }
    }

    @Test
    fun `create with valid data returns task`() {
        every { projectService.ensureAccess(projectId, userId) } returns Unit
        val entitySlot = slot<TaskEntity>()
        every { taskRepo.save(capture(entitySlot)) } answers { entitySlot.captured }

        val result = taskService.create(userId, projectId, CreateTaskRequest("New task", priority = "high"))

        assertEquals("New task", result.title)
        assertEquals("todo", result.status)
        assertEquals("high", result.priority)
    }

    @Test
    fun `create with invalid priority throws ValidationException`() {
        every { projectService.ensureAccess(projectId, userId) } returns Unit

        val ex = assertThrows<ValidationException> {
            taskService.create(userId, projectId, CreateTaskRequest("Task", priority = "urgent"))
        }
        assertEquals("is invalid", ex.fields["priority"])
    }

    @Test
    fun `create with nonexistent assignee throws InvalidAssigneeException`() {
        val badId = UUID.randomUUID()
        every { projectService.ensureAccess(projectId, userId) } returns Unit
        every { userRepo.existsById(badId) } returns false

        assertThrows<InvalidAssigneeException> {
            taskService.create(userId, projectId, CreateTaskRequest("Task", assigneeId = badId))
        }
    }

    @Test
    fun `patch updates status correctly`() {
        val t = task()
        every { taskRepo.findById(t.id) } returns Optional.of(t)
        every { projectService.ensureAccess(t.projectId, userId) } returns Unit
        every { taskRepo.save(any<TaskEntity>()) } answers { firstArg() }

        val result = taskService.patch(userId, t.id, jsonBody("""{"status":"done"}"""))

        assertEquals("done", result.status)
    }

    @Test
    fun `patch with invalid status throws ValidationException`() {
        val t = task()
        every { taskRepo.findById(t.id) } returns Optional.of(t)
        every { projectService.ensureAccess(t.projectId, userId) } returns Unit

        assertThrows<ValidationException> {
            taskService.patch(userId, t.id, jsonBody("""{"status":"invalid"}"""))
        }
    }

    @Test
    fun `patch clears description with null`() {
        val t = task()
        t.description = "Old description"
        every { taskRepo.findById(t.id) } returns Optional.of(t)
        every { projectService.ensureAccess(t.projectId, userId) } returns Unit
        every { taskRepo.save(any<TaskEntity>()) } answers { firstArg() }

        val result = taskService.patch(userId, t.id, jsonBody("""{"description":null}"""))

        assertNull(result.description)
    }

    @Test
    fun `delete by project owner succeeds`() {
        val otherId = UUID.randomUUID()
        val t = task(creator = otherId)
        val project = ProjectEntity(id = t.projectId, name = "P", ownerId = userId)

        every { taskRepo.findById(t.id) } returns Optional.of(t)
        every { projectService.ensureAccess(t.projectId, userId) } returns Unit
        every { projectRepo.findById(t.projectId) } returns Optional.of(project)
        every { taskRepo.delete(t) } returns Unit

        taskService.delete(userId, t.id)

        verify { taskRepo.delete(t) }
    }

    @Test
    fun `delete by task creator succeeds`() {
        val ownerId = UUID.randomUUID()
        val t = task(creator = userId)
        val project = ProjectEntity(id = t.projectId, name = "P", ownerId = ownerId)

        every { taskRepo.findById(t.id) } returns Optional.of(t)
        every { projectService.ensureAccess(t.projectId, userId) } returns Unit
        every { projectRepo.findById(t.projectId) } returns Optional.of(project)
        every { taskRepo.delete(t) } returns Unit

        taskService.delete(userId, t.id)

        verify { taskRepo.delete(t) }
    }

    @Test
    fun `delete by non-owner non-creator throws ForbiddenException`() {
        val ownerId = UUID.randomUUID()
        val creatorId = UUID.randomUUID()
        val t = task(creator = creatorId)
        val project = ProjectEntity(id = t.projectId, name = "P", ownerId = ownerId)

        every { taskRepo.findById(t.id) } returns Optional.of(t)
        every { projectService.ensureAccess(t.projectId, userId) } returns Unit
        every { projectRepo.findById(t.projectId) } returns Optional.of(project)

        assertThrows<ForbiddenException> {
            taskService.delete(userId, t.id)
        }
    }

    @Test
    fun `create with assignee adds assignee as project member`() {
        val assigneeId = UUID.randomUUID()
        every { projectService.ensureAccess(projectId, userId) } returns Unit
        every { userRepo.existsById(assigneeId) } returns true
        val entitySlot = slot<TaskEntity>()
        every { taskRepo.save(capture(entitySlot)) } answers { entitySlot.captured }
        every { projectMemberRepo.addIfAbsent(any(), any(), any()) } returns Unit

        taskService.create(userId, projectId, CreateTaskRequest("Task", assigneeId = assigneeId))

        verify { projectMemberRepo.addIfAbsent(projectId, assigneeId, "member") }
    }

    @Test
    fun `create without assignee does not add member`() {
        every { projectService.ensureAccess(projectId, userId) } returns Unit
        val entitySlot = slot<TaskEntity>()
        every { taskRepo.save(capture(entitySlot)) } answers { entitySlot.captured }

        taskService.create(userId, projectId, CreateTaskRequest("Task"))

        verify(exactly = 0) { projectMemberRepo.addIfAbsent(any(), any(), any()) }
    }

    @Test
    fun `patch assignee adds new assignee as project member`() {
        val assigneeId = UUID.randomUUID()
        val t = task()
        every { taskRepo.findById(t.id) } returns Optional.of(t)
        every { projectService.ensureAccess(t.projectId, userId) } returns Unit
        every { userRepo.existsById(assigneeId) } returns true
        every { taskRepo.save(any<TaskEntity>()) } answers { firstArg() }
        every { projectMemberRepo.addIfAbsent(any(), any(), any()) } returns Unit

        taskService.patch(userId, t.id, jsonBody("""{"assignee_id":"$assigneeId"}"""))

        verify { projectMemberRepo.addIfAbsent(t.projectId, assigneeId, "member") }
    }

    @Test
    fun `list with invalid status throws ValidationException`() {
        every { projectService.ensureAccess(projectId, userId) } returns Unit

        val ex = assertThrows<ValidationException> {
            taskService.list(userId, projectId, "invalid", null, null, null)
        }
        assertEquals("is invalid", ex.fields["status"])
    }
}
