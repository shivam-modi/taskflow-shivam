package com.taskflow.auth.service

import com.taskflow.auth.dto.AuthResponse
import com.taskflow.auth.dto.LoginRequest
import com.taskflow.auth.dto.RegisterRequest

interface AuthService {
    fun register(request: RegisterRequest): AuthResponse
    fun login(request: LoginRequest): AuthResponse
}
