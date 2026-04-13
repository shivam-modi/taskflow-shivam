package com.taskflow.unit

import com.taskflow.auth.JwtService
import com.taskflow.auth.PasswordService
import com.taskflow.auth.dto.LoginRequest
import com.taskflow.auth.dto.RegisterRequest
import com.taskflow.auth.service.AuthServiceImpl
import com.taskflow.common.ConflictException
import com.taskflow.user.entity.UserEntity
import com.taskflow.user.entity.UserRepository
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.security.authentication.BadCredentialsException
import java.util.UUID

@ExtendWith(MockKExtension::class)
class AuthServiceImplTest {

    @MockK lateinit var userRepo: UserRepository
    @MockK lateinit var passwordService: PasswordService
    @MockK lateinit var jwtService: JwtService

    private lateinit var authService: AuthServiceImpl

    @BeforeEach
    fun setUp() {
        authService = AuthServiceImpl(userRepo, passwordService, jwtService)
    }

    @Test
    fun `register succeeds and returns token`() {
        val entitySlot = slot<UserEntity>()
        every { passwordService.hash("password123") } returns "\$2b\$12\$hash"
        every { userRepo.saveAndFlush(capture(entitySlot)) } answers { entitySlot.captured }
        every { jwtService.sign(any(), any()) } returns "jwt-token"

        val result = authService.register(RegisterRequest("Alice", "Alice@Example.COM", "password123"))

        assertEquals("jwt-token", result.token)
        assertEquals("alice@example.com", result.user.email)
        assertEquals("Alice", result.user.name)
        assertEquals("alice@example.com", entitySlot.captured.email)
    }

    @Test
    fun `register with duplicate email throws ConflictException`() {
        every { passwordService.hash(any()) } returns "\$2b\$12\$hash"
        every { userRepo.saveAndFlush(any()) } throws DataIntegrityViolationException("duplicate")

        assertThrows<ConflictException> {
            authService.register(RegisterRequest("Bob", "bob@example.com", "password123"))
        }
    }

    @Test
    fun `login succeeds with correct password`() {
        val user = UserEntity(
            id = UUID.randomUUID(), name = "Alice",
            email = "alice@example.com", passwordHash = "\$2b\$12\$hash",
        )
        every { userRepo.findByEmail("alice@example.com") } returns user
        every { passwordService.matches("password123", "\$2b\$12\$hash") } returns true
        every { jwtService.sign(user.id, user.email) } returns "jwt-token"

        val result = authService.login(LoginRequest("alice@example.com", "password123"))

        assertEquals("jwt-token", result.token)
        assertEquals("alice@example.com", result.user.email)
    }

    @Test
    fun `login with wrong password throws BadCredentialsException`() {
        val user = UserEntity(
            id = UUID.randomUUID(), name = "Alice",
            email = "alice@example.com", passwordHash = "\$2b\$12\$hash",
        )
        every { userRepo.findByEmail("alice@example.com") } returns user
        every { passwordService.matches("wrongpass", "\$2b\$12\$hash") } returns false

        assertThrows<BadCredentialsException> {
            authService.login(LoginRequest("alice@example.com", "wrongpass"))
        }
    }

    @Test
    fun `login with unknown email throws BadCredentialsException`() {
        every { userRepo.findByEmail("nobody@example.com") } returns null

        assertThrows<BadCredentialsException> {
            authService.login(LoginRequest("nobody@example.com", "password123"))
        }
    }
}
