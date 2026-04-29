/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalKtorApi::class)

package io.ktor.tests.auth.typesafe

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.auth.typesafe.*
import io.ktor.server.response.*
import io.ktor.server.routing.get
import io.ktor.server.testing.*
import io.ktor.utils.io.ExperimentalKtorApi
import kotlin.test.Test
import kotlin.test.assertEquals

class MultipleSchemeTest {

    interface AppUser {
        val email: String
    }

    data class BasicUser(override val email: String) : AppUser
    data class BearerUser(override val email: String) : AppUser

    private val basicScheme = basic<BasicUser>("multi-basic") {
        validate { BasicUser("${it.name}@basic.com") }
    }

    private val bearerScheme = bearer<BearerUser>("multi-bearer") {
        authenticate { BearerUser("${it.token}@bearer.com") }
    }

    @Test
    fun `anyOf accepts matching schemes and rejects when none match`() = testApplication {
        routing {
            authenticateWithAnyOf(basicScheme, bearerScheme) {
                get("/profile") { call.respondText(principal.email) }
            }
        }

        // Basic credentials → 200
        val basicResp = client.get("/profile") {
            header(HttpHeaders.Authorization, basicAuthHeader("alice"))
        }
        assertEquals(HttpStatusCode.OK, basicResp.status)
        assertEquals("alice@basic.com", basicResp.bodyAsText())

        // Bearer credentials → 200
        val bearerResp = client.get("/profile") {
            header(HttpHeaders.Authorization, bearerAuthHeader("tok"))
        }
        assertEquals(HttpStatusCode.OK, bearerResp.status)
        assertEquals("tok@bearer.com", bearerResp.bodyAsText())

        // No credentials → 401
        assertEquals(HttpStatusCode.Unauthorized, client.get("/profile").status)
    }
}
