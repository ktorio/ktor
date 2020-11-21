/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client

import io.ktor.client.features.*
import io.ktor.client.request.*
import kotlin.test.*
import kotlinx.coroutines.*

class DefaultRequestTest {
    private val client = HttpClient(TestEngine) {
        defaultRequest {
            baseURL("https://jsonplaceholder.typicode.com/todos")
        }
    }

    @Test
    fun testUsingBaseURL() = runBlocking {
        val todo = client.get<String>("/1")
        assertTrue(todo.isNotEmpty())
    }

    @Test
    fun overrideBaseURL() = runBlocking {
        val overridden = client.get<String>("https://ktor.io/docs")
        assertTrue(overridden.isNotEmpty())
    }

    @Test
    fun localhost() = runBlocking {
        val local = client.get<String>("http://localhost/")
        assertTrue(local.isNotEmpty())
    }
}
