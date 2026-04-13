package com.taskflow.common

import mu.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

private val logger = KotlinLogging.logger {}

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<Map<String, Any>> {
        val fields = ex.bindingResult.fieldErrors.associate { it.field to (it.defaultMessage ?: "is invalid") }
        return ResponseEntity.badRequest().body(
            mapOf("error" to "validation failed", "fields" to fields)
        )
    }

    @ExceptionHandler(ValidationException::class)
    fun handleCustomValidation(ex: ValidationException): ResponseEntity<Map<String, Any>> {
        return ResponseEntity.badRequest().body(
            mapOf("error" to "validation failed", "fields" to ex.fields)
        )
    }

    @ExceptionHandler(ResourceNotFoundException::class)
    fun handleNotFound(ex: ResourceNotFoundException): ResponseEntity<Map<String, String>> {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("error" to "not found"))
    }

    @ExceptionHandler(ForbiddenException::class)
    fun handleForbidden(ex: ForbiddenException): ResponseEntity<Map<String, String>> {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf("error" to "forbidden"))
    }

    @ExceptionHandler(ConflictException::class)
    fun handleConflict(ex: ConflictException): ResponseEntity<Map<String, Any>> {
        return ResponseEntity.badRequest().body(
            mapOf("error" to "validation failed", "fields" to mapOf("email" to "already registered"))
        )
    }

    @ExceptionHandler(BadCredentialsException::class)
    fun handleBadCredentials(ex: BadCredentialsException): ResponseEntity<Map<String, String>> {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to "unauthorized"))
    }

    @ExceptionHandler(InvalidAssigneeException::class)
    fun handleInvalidAssignee(ex: InvalidAssigneeException): ResponseEntity<Map<String, Any>> {
        return ResponseEntity.badRequest().body(
            mapOf("error" to "validation failed", "fields" to mapOf("assignee_id" to "user not found"))
        )
    }

    @ExceptionHandler(Exception::class)
    fun handleGeneric(ex: Exception): ResponseEntity<Map<String, String>> {
        logger.error(ex) { "unhandled exception" }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf("error" to "server error"))
    }
}

class ValidationException(val fields: Map<String, String>) : RuntimeException("validation failed")
