package com.taskflow.unit

import com.taskflow.common.ForbiddenException
import com.taskflow.common.ResourceNotFoundException
import com.taskflow.project.dto.CreateProjectRequest
import com.taskflow.project.entity.ProjectEntity
import com.taskflow.project.entity.ProjectMemberRepository
import com.taskflow.project.entity.ProjectRepository
import com.taskflow.project.service.ProjectServiceImpl
import com.taskflow.task.entity.TaskEntity
import com.taskflow.task.entity.TaskRepository
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
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import java.util.Optional
import java.util.UUID

@ExtendWith(MockKExtension::class)
class ProjectServiceImplTest {

    @MockK lateinit var projectRepo: ProjectRepository
    @MockK lateinit var projectMemberRepo: ProjectMemberRepository
    @MockK lateinit var taskRepo: TaskRepository

    private lateinit var projectService: ProjectServiceImpl
    private val ownerId = UUID.randomUUID()
    private val otherId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        projectService = ProjectServiceImpl(projectRepo, projectMemberRepo, taskRepo)
    }

    private fun project(id: UUID = UUID.randomUUID(), owner: UUID = ownerId) = ProjectEntity(
        id = id, name = "Test", description = "Desc", ownerId = owner,
    )

    @Test
    fun `list returns paginated projects`() {
        val proj = project()
        every { projectRepo.findAccessibleByUser(ownerId, any<Pageable>()) } returns PageImpl(listOf(proj))

        val result = projectService.list(ownerId, 1, 20)

        assertEquals(1, result.projects.size)
        assertEquals(proj.name, result.projects[0].name)
    }

    @Test
    fun `create saves and returns project`() {
        val entitySlot = slot<ProjectEntity>()
        every { projectRepo.save(capture(entitySlot)) } answers { entitySlot.captured }
        every { projectMemberRepo.addIfAbsent(any(), any(), any()) } returns Unit

        val result = projectService.create(ownerId, CreateProjectRequest("New Project", "desc"))

        assertEquals("New Project", result.name)
        assertEquals(ownerId, result.ownerId)
        verify { projectMemberRepo.addIfAbsent(any(), ownerId, "owner") }
    }

    @Test
    fun `getDetail with access returns project and tasks`() {
        val proj = project()
        val task = TaskEntity(
            id = UUID.randomUUID(), title = "Task 1", projectId = proj.id, creatorId = ownerId,
        )
        every { projectMemberRepo.existsByProjectIdAndUserId(proj.id, ownerId) } returns true
        every { projectRepo.findById(proj.id) } returns Optional.of(proj)
        every { taskRepo.findByProjectIdCapped(proj.id) } returns listOf(task)

        val result = projectService.getDetail(ownerId, proj.id)

        assertEquals(proj.name, result.name)
        assertEquals(1, result.tasks.size)
    }

    @Test
    fun `getDetail without access throws ResourceNotFoundException`() {
        val projId = UUID.randomUUID()
        every { projectMemberRepo.existsByProjectIdAndUserId(projId, otherId) } returns false

        assertThrows<ResourceNotFoundException> {
            projectService.getDetail(otherId, projId)
        }
    }

    @Test
    fun `patch by owner succeeds`() {
        val proj = project()
        every { projectMemberRepo.existsByProjectIdAndUserId(proj.id, ownerId) } returns true
        every { projectRepo.findById(proj.id) } returns Optional.of(proj)
        every { projectRepo.save(any<ProjectEntity>()) } answers { firstArg() }

        val result = projectService.patch(ownerId, proj.id, Optional.of("Updated"), null)

        assertEquals("Updated", result.name)
    }

    @Test
    fun `patch by non-owner with access throws ForbiddenException`() {
        val proj = project()
        every { projectMemberRepo.existsByProjectIdAndUserId(proj.id, otherId) } returns true
        every { projectRepo.findById(proj.id) } returns Optional.of(proj)

        assertThrows<ForbiddenException> {
            projectService.patch(otherId, proj.id, Optional.of("Hacked"), null)
        }
    }

    @Test
    fun `patch clears description with Optional empty`() {
        val proj = project()
        every { projectMemberRepo.existsByProjectIdAndUserId(proj.id, ownerId) } returns true
        every { projectRepo.findById(proj.id) } returns Optional.of(proj)
        every { projectRepo.save(any<ProjectEntity>()) } answers { firstArg() }

        val result = projectService.patch(ownerId, proj.id, null, Optional.empty())

        assertNull(result.description)
    }

    @Test
    fun `delete by owner succeeds`() {
        val proj = project()
        every { projectMemberRepo.existsByProjectIdAndUserId(proj.id, ownerId) } returns true
        every { projectRepo.findById(proj.id) } returns Optional.of(proj)
        every { projectRepo.delete(proj) } returns Unit

        projectService.delete(ownerId, proj.id)

        verify { projectRepo.delete(proj) }
    }

    @Test
    fun `delete by non-owner throws ForbiddenException`() {
        val proj = project()
        every { projectMemberRepo.existsByProjectIdAndUserId(proj.id, otherId) } returns true
        every { projectRepo.findById(proj.id) } returns Optional.of(proj)

        assertThrows<ForbiddenException> {
            projectService.delete(otherId, proj.id)
        }
    }
}
