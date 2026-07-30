/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.defaults

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.test.base.*
import io.ktor.http.*
import io.ktor.test.*
import io.ktor.utils.io.ExperimentalKtorApi
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalKtorApi::class)
class DefaultClientTest {

    @Test
    fun `default client resolves the curated engine for this platform`() {
        HttpClient(DefaultEngine).use { curated ->
            HttpClient().use { discovered ->
                assertEquals(
                    curated.engine::class,
                    discovered.engine::class,
                    "HttpClient() should resolve to the engine curated for this platform"
                )
            }
        }
    }

    @Test
    fun `curated engine performs a request`() = runTest {
        HttpClient(DefaultEngine).use { client ->
            val response = client.get("$TEST_SERVER/content/hello")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("hello", response.bodyAsText())
        }
    }

    @Test
    fun `discovered engine performs a request`() = runTest {
        HttpClient().use { client ->
            val response = client.get("$TEST_SERVER/content/hello")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("hello", response.bodyAsText())
        }
    }
}
