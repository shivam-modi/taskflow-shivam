package com.taskflow.auth.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.util.UUID

data class RegisterRequest(
    @field:NotBlank(message = "is required")
    @field:Size(max = 200, message = "is too long")
    val name: String,

    @field:NotBlank(message = "is required")
    @field:Email(message = "is invalid")
    val email: String,

    @field:NotBlank(message = "is required")
    @field:Size(min = 8, message = "must be at least 8 characters")
    @field:Size(max = 72, message = "is too long")
    val password: String,
)

data class LoginRequest(
    @field:NotBlank(message = "is required")
    val email: String,

    @field:NotBlank(message = "is required")
    val password: String,
)

data class AuthResponse(
    val token: String,
    val user: UserDto,
)

data class UserDto(
    val id: UUID,
    val name: String,
    val email: String,
)
