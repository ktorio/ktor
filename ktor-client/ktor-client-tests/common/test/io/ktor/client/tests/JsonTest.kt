/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.test.base.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals

class JsonTest : ClientLoader() {
    @Serializable
    data class User(val name: String)

    @Serializable
    @Polymorphic
    data class Result<T>(val message: String, val data: T)

    @Test
    fun testUserGenerics() = clientTests(except("Js")) {
        config {
            install(ContentNegotiation) { json() }
        }

        test { client ->
            val expected = Result<User>("ok", User("hello"))
            val response = client.get("$TEST_SERVER/json/user-generic").body<Result<User>>()

            assertEquals(expected, response)
        }
    }
}
