/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(InternalAPI::class)

package io.ktor.tests.auth

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.server.testing.*
import io.ktor.utils.io.*
import kotlin.test.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class AuthenticationStateCookieTest {

    @Test
    fun `written cookie is short-lived and not readable by scripts`() = testApplication {
        val cookie = stateCookie()
        routing { get("/write") { cookie.write(call, "value-1").also { call.respondText("ok") } } }

        val written = client.get("/write").setCookie().single()

        assertEquals("STATE", written.name)
        assertEquals("value-1", written.value)
        assertEquals(600, written.maxAge)
        assertEquals("/", written.path)
        assertTrue(written.httpOnly)
        assertFalse(written.secure)
        assertEquals("Lax", written.extensions["SameSite"])
    }

    @Test
    fun `cookie is secure when the request is served over HTTPS`() = testApplication {
        val cookie = stateCookie()
        routing { get("/write") { cookie.write(call, "value-1").also { call.respondText("ok") } } }

        assertTrue(client.get("https://localhost/write").setCookie().single().secure)
    }

    @Test
    fun `cross-site cookie is always secure`() = testApplication {
        val cookie = stateCookie(sameSite = SameSite.None)
        routing { get("/write") { cookie.write(call, "value-1").also { call.respondText("ok") } } }

        val written = client.get("/write").setCookie().single()

        assertEquals("None", written.extensions["SameSite"])
        assertTrue(written.secure)
    }

    @Test
    fun `reads the cookie sent with the request`() = testApplication {
        val cookie = stateCookie()
        routing { get("/read") { call.respondText(cookie.read(call) ?: "absent") } }

        assertEquals("value-1", client.get("/read") { header(HttpHeaders.Cookie, "STATE=value-1") }.bodyAsText())
        assertEquals("absent", client.get("/read") { header(HttpHeaders.Cookie, "OTHER=value-1") }.bodyAsText())
    }

    @Test
    fun `clear expires the cookie with the same attributes`() = testApplication {
        val cookie = stateCookie(sameSite = SameSite.None)
        routing { get("/clear") { cookie.clear(call).also { call.respondText("ok") } } }

        val cleared = client.get("/clear").setCookie().single()

        assertEquals("STATE", cleared.name)
        assertEquals("", cleared.value)
        assertEquals(0, cleared.maxAge)
        assertEquals("/", cleared.path)
        assertTrue(cleared.secure)
        assertEquals("None", cleared.extensions["SameSite"])
    }

    @Test
    fun `rejects invalid max age and SameSite`() {
        assertFailsWith<IllegalArgumentException> { stateCookie(maxAge = Duration.ZERO) }
        assertFailsWith<IllegalArgumentException> { stateCookie(maxAge = Duration.INFINITE) }
        assertFailsWith<IllegalArgumentException> { stateCookie(sameSite = "lax") }
    }

    private fun stateCookie(maxAge: Duration = 10.minutes, sameSite: String = SameSite.Lax) =
        AuthenticationStateCookie("STATE", maxAge, sameSite)
}
