/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.curl.test

import io.ktor.client.engine.curl.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.test.base.*
import kotlin.test.Test
import kotlin.test.assertEquals

class CurlNativeTests : ClientEngineTest<CurlClientEngineConfig>(Curl) {

    @Test
    fun testDownload() = testClient {
        test { client ->
            val response = client.get("$TEST_SERVER/content/hello")
            assertEquals("hello", response.bodyAsText())
        }
    }

    @Test
    fun testDelete(): Unit = testClient {
        test { client ->
            val response = client.delete("$TEST_SERVER/delete")
            assertEquals("OK ", response.bodyAsText())

            val responseWithBody = client.delete("$TEST_SERVER/delete") {
                setBody("1")
            }
            assertEquals("OK 1", responseWithBody.bodyAsText())
        }
    }
}
