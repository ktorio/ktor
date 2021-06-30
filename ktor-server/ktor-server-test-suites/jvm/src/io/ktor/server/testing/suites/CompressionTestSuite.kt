/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.testing.suites

import io.ktor.application.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.testing.*
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import org.junit.*
import org.junit.Assert.*
import java.io.*
import java.util.zip.*

abstract class CompressionTestSuite<TEngine : ApplicationEngine, TConfiguration : ApplicationEngine.Configuration>(
    hostFactory: ApplicationEngineFactory<TEngine, TConfiguration>
) : EngineTestBase<TEngine, TConfiguration>(hostFactory) {

    @Test
    fun testLocalFileContentWithCompression() {
        val file = loadTestFile()
        testLog.trace("test file is $file")

        createAndStartServer {
            application.install(Compression)
            handle {
                call.respond(LocalFileContent(file))
            }
        }

        withUrl("/", { header(HttpHeaders.AcceptEncoding, "gzip") }) {
            assertEquals(200, status.value)
            assertEquals(file.readText(), GZIPInputStream(content.toInputStream()).reader().use { it.readText() })
            assertEquals("gzip", headers[HttpHeaders.ContentEncoding])
        }
    }

    @Test
    fun testStreamingContentWithCompression() {
        val file = loadTestFile()
        testLog.trace("test file is $file")

        createAndStartServer {
            application.install(Compression)
            handle {
                call.respond(
                    object : OutgoingContent.WriteChannelContent() {
                        override suspend fun writeTo(channel: ByteWriteChannel) {
                            channel.writeStringUtf8("Hello!")
                        }
                    }
                )
            }
        }

        withUrl("/", { header(HttpHeaders.AcceptEncoding, "gzip") }) {
            assertEquals(200, status.value)
            assertEquals("Hello!", GZIPInputStream(content.toInputStream()).reader().use { it.readText() })
            assertEquals("gzip", headers[HttpHeaders.ContentEncoding])
        }
    }

    @Test
    fun testLocalFileContentRangeWithCompression() {
        val file = loadTestFile()
        testLog.trace("test file is $file")

        createAndStartServer {
            application.install(Compression)
            application.install(PartialContent)

            handle {
                call.respond(LocalFileContent(file))
            }
        }

        withUrl(
            "/",
            {
                header(HttpHeaders.AcceptEncoding, "gzip")
                header(
                    HttpHeaders.Range,
                    RangesSpecifier(RangeUnits.Bytes, listOf(ContentRange.Bounded(0, 0))).toString()
                )
            }
        ) {
            assertEquals(HttpStatusCode.PartialContent.value, status.value)
            assertEquals(
                "It should be no compression if range requested",
                file.reader().use { it.read().toChar().toString() },
                readText()
            )
        }
    }

    @Test
    fun testCompressionWriteToLarge() {
        val count = 655350
        fun Appendable.produceText() {
            for (i in 1..count) {
                append("test $i\n".padStart(10, ' '))
            }
        }

        createAndStartServer {
            application.install(Compression)

            get("/") {
                call.respondTextWriter(contentType = ContentType.Text.Plain) {
                    produceText()
                }
            }
        }

        withUrl("/", { headers.append(HttpHeaders.AcceptEncoding, "gzip") }) {
            // ensure the server is running
            val expected = buildString {
                produceText()
            }
            assertTrue(HttpHeaders.ContentEncoding in headers)
            val array = receive<ByteArray>()
            val text = GZIPInputStream(ByteArrayInputStream(array)).readBytes().toString(Charsets.UTF_8)
            assertEquals(expected, text)
        }
    }
}
