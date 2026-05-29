/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.engine

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.test.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.*
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MultiPartDataTest {
    private val mockContext = mockk<PipelineContext<*, PipelineCall>>(relaxed = true)
    private val mockRequest = mockk<PipelineRequest>(relaxed = true)
    private val testScope = TestScope()

    @Test
    fun givenRequest_whenNoContentTypeHeaderPresent_thenUnsupportedMediaTypeException() {
        // Given
        every { mockContext.call.request } returns mockRequest
        every { mockRequest.header(HttpHeaders.ContentType) } returns null

        // When & Then
        assertFailsWith<UnsupportedMediaTypeException> {
            runBlocking { mockContext.multiPartData(ByteReadChannel("sample data")) }
        }
    }

    @Test
    fun givenWrongContentType_whenProcessMultiPart_thenUnsupportedMediaTypeException() {
        // Given
        val rc = ByteReadChannel("sample data")
        val contentType = "test/plain; boundary=test"
        val contentLength = "123"
        every { mockContext.call.request } returns mockRequest
        every { mockContext.call.attributes.getOrNull<Long>(any()) } returns 0L
        every { mockRequest.header(HttpHeaders.ContentType) } returns contentType
        every { mockRequest.header(HttpHeaders.ContentLength) } returns contentLength

        // When & Then
        testScope.runTest {
            assertFailsWith<UnsupportedMediaTypeException> {
                mockContext.multiPartData(rc)
            }
        }
    }

    @Test
    fun testUnsupportedMediaTypeStatusCode() = testApplication {
        routing {
            post {
                call.receiveMultipart()
                call.respond(HttpStatusCode.OK)
            }
        }

        client.post {
            accept(ContentType.Text.Plain)
        }.apply {
            assertEquals(HttpStatusCode.UnsupportedMediaType, status)
        }

        client.post {}.apply {
            assertEquals(HttpStatusCode.UnsupportedMediaType, status)
        }
    }
}
