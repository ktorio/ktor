/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.features.json.*
import io.ktor.client.features.json.serializer.*
import io.ktor.client.request.*
import io.ktor.client.tests.utils.*
import io.ktor.client.utils.*
import io.ktor.http.*
import kotlin.test.*

class CommonContentTest : ClientLoader() {

    @Test
    fun testJsonPostWithEmptyBody() = clientTests {
        config {
            install(JsonFeature) {
                serializer = KotlinxSerializer()
            }
        }

        test { client ->
            val response = client.post<String>("$TEST_SERVER/echo") {
                contentType(ContentType.Application.Json)
            }

            assertEquals("{}", response)
        }
    }

    @Test
    fun testPostWithEmptyBody() = clientTests {
        config {
        }

        test { client ->
            val response = client.post<String>("$TEST_SERVER/echo") {
                body = EmptyContent
            }

            assertEquals("", response)
        }
    }
}
