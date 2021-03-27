/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.tests

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.tests.utils.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlin.test.*

class HttpClientFreezingTest : ClientLoader() {

    @Test
    fun testRequestWithFrozenClient() = clientTests {
        test { client ->
            client.makeShared()
            val response = client.get("$TEST_SERVER/content/hello").body<String>()
            assertEquals("hello", response)
        }
    }

    @Test
    fun testRequestWithDefaultDispatcher() = clientTests {
        test { client ->
            withContext(Dispatchers.Default) {
                val response = client.post("$TEST_SERVER/echo") {
                    setBody("hello")
                }.body<String>()
                assertEquals("hello", response)
            }
        }
    }
}
