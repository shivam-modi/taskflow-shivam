package com.taskflow.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app")
data class AppProperties(
    val jwt: JwtProperties,
    val cors: CorsProperties,
    val bcrypt: BcryptProperties,
    val seed: SeedProperties,
) {
    data class JwtProperties(
        val secret: String,
        val expiryHours: Long = 24,
    )

    data class CorsProperties(
        val origin: String = "http://localhost:3000",
    )

    data class BcryptProperties(
        val cost: Int = 12,
    )

    data class SeedProperties(
        val enabled: Boolean = false,
    )
}
