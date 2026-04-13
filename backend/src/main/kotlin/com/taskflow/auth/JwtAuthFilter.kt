package com.taskflow.auth

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthFilter(private val jwtService: JwtService) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val header = request.getHeader("Authorization")
        if (header != null && header.startsWith("Bearer ", ignoreCase = true)) {
            val token = header.substring(7).trim()
            if (token.isNotEmpty()) {
                val claims = jwtService.parse(token)
                if (claims != null) {
                    val auth = UsernamePasswordAuthenticationToken(claims, null, emptyList())
                    SecurityContextHolder.getContext().authentication = auth
                }
            }
        }
        filterChain.doFilter(request, response)
    }
}

fun currentClaims(): JwtClaims {
    return SecurityContextHolder.getContext().authentication.principal as JwtClaims
}
