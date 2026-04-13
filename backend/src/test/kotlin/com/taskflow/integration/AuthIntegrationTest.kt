package com.taskflow.integration

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
class AuthIntegrationTest : TestContainerConfig() {

    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var mapper: ObjectMapper

    @Test
    fun `register returns 201 with token and user`() {
        val email = "auth-${UUID.randomUUID()}@example.com"
        mvc.post("/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(mapOf("name" to "Test", "email" to email, "password" to "password123"))
        }.andExpect {
            status { isCreated() }
            jsonPath("$.token") { isNotEmpty() }
            jsonPath("$.user.id") { isNotEmpty() }
            jsonPath("$.user.email") { value(email) }
        }
    }

    @Test
    fun `duplicate register returns 400`() {
        val email = "dup-${UUID.randomUUID()}@example.com"
        val body = mapper.writeValueAsString(mapOf("name" to "A", "email" to email, "password" to "password123"))

        mvc.post("/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = body
        }.andExpect { status { isCreated() } }

        mvc.post("/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = body
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.fields.email") { value("already registered") }
        }
    }

    @Test
    fun `login with wrong password returns 401`() {
        val email = "login-${UUID.randomUUID()}@example.com"
        mvc.post("/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(mapOf("name" to "A", "email" to email, "password" to "password123"))
        }.andExpect { status { isCreated() } }

        mvc.post("/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(mapOf("email" to email, "password" to "wrongpassword"))
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.error") { value("unauthorized") }
        }
    }

    @Test
    fun `login with correct password returns 200 with token`() {
        val email = "loginok-${UUID.randomUUID()}@example.com"
        mvc.post("/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(mapOf("name" to "B", "email" to email, "password" to "password123"))
        }.andExpect { status { isCreated() } }

        mvc.post("/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(mapOf("email" to email, "password" to "password123"))
        }.andExpect {
            status { isOk() }
            jsonPath("$.token") { isNotEmpty() }
            jsonPath("$.user.email") { value(email) }
        }
    }

    @Test
    fun `register with invalid fields returns 400 with field errors`() {
        mvc.post("/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(mapOf("name" to "", "email" to "bad", "password" to "short"))
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error") { value("validation failed") }
            jsonPath("$.fields") { isNotEmpty() }
        }
    }
}
