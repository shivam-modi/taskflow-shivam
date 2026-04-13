package com.taskflow.auth.service

import com.taskflow.auth.JwtService
import com.taskflow.auth.PasswordService
import com.taskflow.auth.dto.AuthResponse
import com.taskflow.auth.dto.LoginRequest
import com.taskflow.auth.dto.RegisterRequest
import com.taskflow.common.ConflictException
import com.taskflow.common.UUIDv7
import com.taskflow.user.entity.UserEntity
import com.taskflow.user.entity.UserRepository
import mu.KotlinLogging
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val log = KotlinLogging.logger {}

@Service
class AuthServiceImpl(
    private val userRepo: UserRepository,
    private val passwordService: PasswordService,
    private val jwtService: JwtService,
) : AuthService {

    @Transactional
    override fun register(request: RegisterRequest): AuthResponse {
        val email = request.email.trim().lowercase()
        val hash = passwordService.hash(request.password)
        val user = UserEntity(
            id = UUIDv7.generate(),
            name = request.name.trim(),
            email = email,
            passwordHash = hash,
        )
        val saved = try {
            userRepo.saveAndFlush(user)
        } catch (_: DataIntegrityViolationException) {
            log.warn { "Registration conflict: email=$email" }
            throw ConflictException()
        }
        log.info { "User registered: id=${saved.id}, email=$email" }
        val token = jwtService.sign(saved.id, saved.email)
        return AuthResponse(token = token, user = saved.toDto())
    }

    @Transactional(readOnly = true)
    override fun login(request: LoginRequest): AuthResponse {
        val email = request.email.trim().lowercase()
        val user = userRepo.findByEmail(email)
        if (user == null) {
            log.warn { "Login failed: email=$email (not found)" }
            throw BadCredentialsException("unauthorized")
        }
        if (!passwordService.matches(request.password, user.passwordHash)) {
            log.warn { "Login failed: email=$email (bad password)" }
            throw BadCredentialsException("unauthorized")
        }
        log.info { "Login success: id=${user.id}, email=$email" }
        val token = jwtService.sign(user.id, user.email)
        return AuthResponse(token = token, user = user.toDto())
    }
}
