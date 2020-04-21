/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.client.call.*
import io.ktor.client.engine.mock.*
import io.ktor.client.features.logging.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.client.tests.utils.*
import io.ktor.content.TextContent
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlin.test.*


class CommonLoggingTest {

    @Test
    fun testLogRequestWithException() = testWithEngine(MockEngine) {
        val testLogger = TestLogger()

        config {
            engine {
                addHandler {
                    throw CustomError("BAD REQUEST")
                }
            }
            install(Logging) {
                level = LogLevel.ALL
                logger = testLogger
            }
        }

        test { client ->
            var failed = false
            try {
                client.get<HttpResponse>()
            } catch (_: Throwable) {
                failed = true
            }

            assertTrue(failed, "Exception is missing.")

            /**
             * Note: no way to join logger context => unpredictable logger output.
             */
        }
    }

    @Test
    fun testLogResponseWithException() = testWithEngine(MockEngine) {
        val testLogger = TestLogger()

        config {
            engine {
                addHandler { request ->
                    respondOk("Hello")
                }
            }
            install("BadInterceptor") {
                responsePipeline.intercept(HttpResponsePipeline.Parse) {
                    throw CustomError("PARSE ERROR")
                }
            }

            install(Logging) {
                level = LogLevel.ALL
                logger = testLogger
            }
        }

        test { client ->
            var failed = false
            client.get<HttpStatement>().execute {
                try {
                    it.receive<String>()
                } catch (_: CustomError) {
                    failed = true
                }
            }

            assertTrue(failed, "Exception is missing.")

            val dump = testLogger.dump()
            assertTrue(
                dump.contains("RESPONSE http://localhost/ failed with exception: CustomError: PARSE ERROR"),
                dump
            )
        }
    }

    @Test
    fun testLoggingWithForm() = testWithEngine(MockEngine) {
        val testLogger = TestLogger()

        config {
            engine {
                addHandler {
                    val body = it.body.toByteReadPacket().readText()
                    assertTrue { body.contains("Hello") }
                    respondOk()
                }
            }

            Logging {
                level = LogLevel.ALL
                logger = testLogger
            }
        }

        test { client ->
            val input = buildPacket { writeText("Hello") }
            client.submitFormWithBinaryData<String>(
                formData {
                    appendInput(
                        "file",
                        headersOf(
                            HttpHeaders.ContentDisposition,
                            ContentDisposition.File.withParameter(ContentDisposition.Parameters.Name, "")
                                .withParameter(ContentDisposition.Parameters.FileName, "")
                                .toString()
                        )
                    ) { input }
                }
            )

            val dump = testLogger.dump()
            assertTrue { dump.contains("Hello") }
        }
    }

    @Test
    fun testFilterRequest() = testWithEngine(MockEngine) {
        val testLogger = TestLogger()

        config {
            engine {
                addHandler { respondOk() }
            }
            install(Logging) {
                level = LogLevel.ALL
                logger = testLogger
                filter { it.url.encodedPath == "/filtered_path" }
            }
        }

        test { client ->
            client.get<String>(urlString = "http://somewhere/filtered_path")
            client.get<String>(urlString = "http://somewhere/not_filtered_path")

            val dump = testLogger.dump()
            assertTrue { dump.contains("REQUEST: http://somewhere/filtered_path") }
            assertFalse { dump.contains("REQUEST: http://somewhere/not_filtered_path") }
        }
    }

    @Test
    fun testLogRequestByteArrayContent() = testLogRequestBody(TextContent("Test body", ContentType.Text.Plain))

    @Test
    fun testLogRequestReadChannelContent() = testLogRequestBody(object : OutgoingContent.ReadChannelContent() {
        override fun readFrom(): ByteReadChannel = ByteReadChannel("Test body")
    })

    @Test
    fun testLogRequestWriteChannelContent() = testLogRequestBody(object : OutgoingContent.WriteChannelContent() {
        override suspend fun writeTo(channel: ByteWriteChannel) {
            channel.writeStringUtf8("Test body")
        }
    })

    private fun testLogRequestBody(content: OutgoingContent) = testWithEngine(MockEngine) {
        val testLogger = TestLogger()

        config {
            engine {
                addHandler { respondOk() }
            }
            install(Logging) {
                level = LogLevel.ALL
                logger = testLogger
            }
        }

        test { client ->
            client.get<String> {
                body = content
            }

            val dump = testLogger.dump()

            val requestDump = dump.slice(0 until dump.indexOf("RESPONSE"))
            assertTrue { requestDump.contains("BODY START") }
            assertTrue { requestDump.contains("BODY END") }
        }
    }
}

internal class CustomError(override val message: String) : Throwable()
