/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.apache

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.HttpHeaders
import io.ktor.http.content.*
import io.ktor.util.*
import kotlinx.coroutines.*
import org.apache.http.*
import org.junit.Test
import kotlin.coroutines.*
import kotlin.test.*

class RequestProducerTest {

    @Test
    fun testHeadersMerge() {
        val request = ApacheRequestProducer(
            HttpRequestData(
                Url("http://127.0.0.1/"),
                HttpMethod.Post,
                Headers.build {
                    append(HttpHeaders.ContentType, ContentType.Text.Plain)
                    append(HttpHeaders.ContentLength, "1")
                },
                TextContent("{}", ContentType.Application.Json),
                Job(),
                Attributes()
            ),
            ApacheEngineConfig(),
            EmptyCoroutineContext
        ).generateRequest() as HttpEntityEnclosingRequest

        assertEquals(ContentType.Application.Json.toString(), request.entity.contentType.value)
        assertEquals(2, request.entity.contentLength)
    }
}
