/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalKtorApi::class)

package io.ktor.tests.auth.typesafe

import io.ktor.server.auth.typesafe.*
import io.ktor.utils.io.ExperimentalKtorApi
import kotlinx.serialization.Serializable
import kotlin.io.encoding.Base64

data class TestUser(val name: String, val email: String)

@Serializable
data class TestSession(val name: String)

enum class TestRole : AuthRole {
    User,
    Admin,
    Moderator
}

fun basicAuthHeader(user: String, password: String = "pass"): String =
    "Basic ${Base64.encode("$user:$password".encodeToByteArray())}"

fun bearerAuthHeader(token: String): String = "Bearer $token"

fun testBasicScheme(name: String = "test-basic") = basic<TestUser>(name) {
    realm = "test"
    validate { credentials ->
        if (credentials.name == "user" && credentials.password == "pass") {
            TestUser(credentials.name, "user@test.com")
        } else {
            null
        }
    }
}

fun acceptAllBasicScheme(name: String = "accept-all-basic") = basic<TestUser>(name) {
    validate { credentials ->
        TestUser(credentials.name, "${credentials.name}@test.com")
    }
}

fun testBearerScheme(name: String = "test-bearer") = bearer<TestUser>(name) {
    authenticate { credential ->
        if (credential.token == "valid") {
            TestUser("bearer-user", "bearer@test.com")
        } else {
            null
        }
    }
}

fun testSessionScheme(name: String = "test-session") = session<TestSession>(name) {
    validate { it }
}
