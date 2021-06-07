/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.tests

import io.ktor.client.features.json.*
import io.ktor.client.features.json.serializer.*
import io.ktor.client.request.*
import io.ktor.client.tests.utils.*
import kotlinx.serialization.*
import kotlin.test.*

class JsonTest : ClientLoader() {
    @Serializable
    data class User(val name: String)

    @Serializable
    @Polymorphic
    data class Result<T>(val message: String, val data: T)

    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun testUserGenerics() = clientTests(listOf("js")) {
        config {
            install(JsonFeature) {
                serializer = KotlinxSerializer()
            }
        }

        test { client ->
            val expected = Result<User>("ok", User("hello"))
            val response = client.get<Result<User>>("$TEST_SERVER/json/user-generic")

            assertEquals(expected, response)
        }
    }
}
