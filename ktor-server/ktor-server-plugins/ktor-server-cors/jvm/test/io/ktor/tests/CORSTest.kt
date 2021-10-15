/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.testing.*
import kotlinx.coroutines.*
import kotlin.test.*

class CORSTest {

    @Test
    fun allowAnyOrigin() = runBlocking {
        val pipeline = ApplicationCallPipeline().apply {
            install(CORS) {
                allowSameOrigin = false
                anyHost()
            }
        }

        for (origin in listOf(
            "hyp-hen://host",
            "plus+://host",
            "do.t://host",
            "digits11://host",
        )) {
            val call = pipeline.executeWith(HeadersBuilder().apply { append("Origin", origin) })
            assertEquals(
                HeadersBuilder().apply { append(HttpHeaders.AccessControlAllowOrigin, "*") }.build(),
                call.response.headers.allValues()
            )
        }
    }

    @Test
    fun corsSkipped() = runBlocking {
        val pipeline = ApplicationCallPipeline().apply {
            install(CORS) {
                allowSameOrigin = false
                anyHost()
            }
        }

        for (origin in listOf(
            "a()://host",
            "1abc://host",
        )) {
            val call = pipeline.executeWith(HeadersBuilder().apply { append("Origin", origin) })
            assertEquals(Headers.Empty.entries(), call.response.headers.allValues().entries())
        }
    }

    private suspend fun ApplicationCallPipeline.executeWith(headersBuilder: HeadersBuilder): ApplicationCall {
        val call = FakeCall().apply {
            request.local = FakeConnectionPoint(scheme = "scheme", host = "host", port = 123)
            request.headers = headersBuilder.build()
        }

        execute(call, Unit)
        return call
    }
}
