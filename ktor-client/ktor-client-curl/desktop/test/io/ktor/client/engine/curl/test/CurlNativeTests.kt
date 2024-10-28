/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.curl.test

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.curl.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.*
import kotlin.test.*

class CurlNativeTests {

    private val TEST_SERVER: String = "http://127.0.0.1:8080"

    @Test
    fun testDownload() = runBlocking {
        HttpClient(Curl).use {
            val res = it.get("http://google.com").body<String>()
            assertTrue(res.isNotEmpty())
        }
    }

    @Test
    fun testDelete(): Unit = runBlocking {
        HttpClient(Curl).use {
            val response = it.delete("$TEST_SERVER/delete")
            assertEquals("OK ", response.bodyAsText())

            val responseWithBody = it.delete("$TEST_SERVER/delete") {
                setBody("1")
            }
            assertEquals("OK 1", responseWithBody.bodyAsText())
        }
    }
}
