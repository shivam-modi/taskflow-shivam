package com.taskflow.auth

import com.taskflow.config.AppProperties
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Service

@Service
class PasswordService(props: AppProperties) {

    private val encoder = BCryptPasswordEncoder(props.bcrypt.cost)

    fun hash(raw: String): String = encoder.encode(raw)

    fun matches(raw: String, hash: String): Boolean = encoder.matches(raw, hash)
}
