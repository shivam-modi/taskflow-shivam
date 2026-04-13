package com.taskflow.auth

import com.taskflow.config.AppProperties
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

@Service
class JwtService(private val props: AppProperties) {

    private val key: SecretKey by lazy {
        require(props.jwt.secret.length >= 32) { "JWT_SECRET must be at least 32 characters" }
        Keys.hmacShaKeyFor(props.jwt.secret.toByteArray())
    }

    private val expiry: Duration = Duration.ofHours(props.jwt.expiryHours)

    fun sign(userId: UUID, email: String): String {
        val now = Instant.now()
        return Jwts.builder()
            .subject(userId.toString())
            .claim("user_id", userId.toString())
            .claim("email", email)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(expiry)))
            .signWith(key, Jwts.SIG.HS256)
            .compact()
    }

    fun parse(token: String): JwtClaims? {
        return try {
            val claims: Claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .payload
            JwtClaims(
                userId = UUID.fromString(claims["user_id"] as String),
                email = claims["email"] as String,
            )
        } catch (_: Exception) {
            null
        }
    }
}

data class JwtClaims(val userId: UUID, val email: String)
