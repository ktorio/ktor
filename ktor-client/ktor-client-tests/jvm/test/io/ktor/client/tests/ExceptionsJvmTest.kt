/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.client.request.*
import io.ktor.client.statement.HttpResponse
import io.ktor.client.tests.utils.*
import kotlinx.coroutines.*
import org.apache.http.*
import org.junit.*

class ExceptionsJvmTest {

    @Test
    fun testConnectionCloseException(): Unit = runBlocking {
        val client = HttpClient(Apache)

        client.use {
            assertFailsWith<ConnectionClosedException> {
                it.get<HttpResponse>("$TCP_SERVER/errors/few-bytes")
            }
        }
    }
}
