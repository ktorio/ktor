/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.plugins

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.plugins.autohead.*
import io.ktor.server.plugins.cachingheaders.*
import io.ktor.server.plugins.conditionalheaders.*
import io.ktor.server.plugins.partialcontent.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.util.date.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlin.test.*

@Suppress("DEPRECATION")
class PartialContentTest {

    private val localPath = "plugins/StaticContentTest.kt"
    private val fileEtag = "etag-99"
    private val contentType = "Content-Type: application/octet-stream"
    private val content = "test_string".repeat(100).toByteArray()
    private val lastModifiedTime = getTimeMillis()

    private fun withRangeApplication(maxRangeCount: Int? = null, test: TestApplicationEngine.() -> Unit): Unit =
        withTestApplication {
            application.install(ConditionalHeaders)
            application.install(CachingHeaders)
            application.install(PartialContent) {
                maxRangeCount?.let { this.maxRangeCount = it }
            }
            application.install(AutoHeadResponse)
            application.routing {
                route(localPath) {
                    handle {
                        val channel = ByteReadChannel(content)
                        call.respond(
                            object : OutgoingContent.ReadChannelContent() {
                                override val contentType: ContentType = ContentType.Application.OctetStream
                                override val contentLength: Long = content.size.toLong()
                                override fun readFrom(): ByteReadChannel = channel
                            }.apply {
                                versions += LastModifiedVersion(GMTDate(lastModifiedTime))
                                versions += EntityTagVersion(fileEtag)
                            }
                        )
                    }
                }
            }

            test()
        }

    @Test
    fun testCustomMaxRangeCountNotAccepted() {
        assertFailsWith<IllegalArgumentException> {
            withRangeApplication(maxRangeCount = 0) {
            }
        }
    }

    @Test
    fun testSubrouteInstall(): Unit = withTestApplication {
        application.install(AutoHeadResponse)
        application.routing {
            application.routing {
                suspend fun respond(applicationCall: ApplicationCall) {
                    applicationCall.respond(
                        object : OutgoingContent.ReadChannelContent() {
                            override val contentType: ContentType = ContentType.Application.OctetStream
                            override val contentLength: Long = content.size.toLong()
                            override fun readFrom(): ByteReadChannel = ByteReadChannel(content)
                        }
                    )
                }

                route("1") {
                    install(PartialContent)
                    get {
                        respond(call)
                    }
                }
            }
        }

        handleRequest(HttpMethod.Get, "1") {
            addHeader(HttpHeaders.Range, "bytes=1-2")
        }.let { result ->
            assertEquals(HttpStatusCode.PartialContent, result.response.status())
            assertContentEquals(content.drop(1).take(2).toByteArray(), result.response.byteContent)
            assertEquals("bytes 1-2/${content.size}", result.response.headers[HttpHeaders.ContentRange])
            val contentType = ContentType.parse(result.response.headers[HttpHeaders.ContentType]!!)
            assertTrue(contentType.match(ContentType.Application.OctetStream))
            checkContentLength(result)
        }
    }

    @Test
    fun testCustomMaxRangeCountAcceptedRangeLimited(): Unit = withRangeApplication(maxRangeCount = 2) {
        handleRequest(HttpMethod.Get, localPath) {
            addHeader(HttpHeaders.Range, "bytes=0-0,2-2,4-4")
        }.let { result ->
            assertEquals(HttpStatusCode.PartialContent, result.response.status())
            assertEquals("bytes 0-4/${content.size}", result.response.headers[HttpHeaders.ContentRange])
            assertContentEquals(content.take(5).toByteArray(), result.response.byteContent)
            assertNotNull(result.response.headers[HttpHeaders.LastModified])
            checkContentLength(result)
        }
    }

    @Test
    fun testSingleByteRange(): Unit = withRangeApplication {
        handleRequest(HttpMethod.Get, localPath) {
            addHeader(HttpHeaders.Range, "bytes=0-0")
        }.let { result ->
            assertEquals(HttpStatusCode.PartialContent, result.response.status())
            assertEquals("bytes 0-0/${content.size}", result.response.headers[HttpHeaders.ContentRange])
            assertContentEquals(content.take(1).toByteArray(), result.response.byteContent)
            assertNotNull(result.response.headers[HttpHeaders.LastModified])
            val contentType = ContentType.parse(result.response.headers[HttpHeaders.ContentType]!!)
            assertTrue(contentType.match(ContentType.Application.OctetStream))
            checkContentLength(result)
        }
    }

    @Test
    fun testTwoBytesRange(): Unit = withRangeApplication {
        handleRequest(HttpMethod.Get, localPath) {
            addHeader(HttpHeaders.Range, "bytes=1-2")
        }.let { result ->
            assertEquals(HttpStatusCode.PartialContent, result.response.status())
            assertContentEquals(content.drop(1).take(2).toByteArray(), result.response.byteContent)
            assertEquals("bytes 1-2/${content.size}", result.response.headers[HttpHeaders.ContentRange])
            assertNotNull(result.response.headers[HttpHeaders.LastModified])
            val contentType = ContentType.parse(result.response.headers[HttpHeaders.ContentType]!!)
            assertTrue(contentType.match(ContentType.Application.OctetStream))
            checkContentLength(result)
        }
    }

    @Test
    fun testUnsatisfiableTailRange(): Unit = withRangeApplication {
        handleRequest(HttpMethod.Get, localPath) {
            addHeader(HttpHeaders.Range, "bytes=-0") // unsatisfiable
        }.let { result ->
            assertEquals(HttpStatusCode.RequestedRangeNotSatisfiable.value, result.response.status()?.value)
            assertEquals("bytes */${content.size}", result.response.headers[HttpHeaders.ContentRange])
        }
    }

    @Test
    fun testUnsatisfiableRange(): Unit = withRangeApplication {
        handleRequest(HttpMethod.Get, localPath) {
            addHeader(HttpHeaders.Range, "bytes=1000000-1000004") // unsatisfiable
        }.let { result ->
            assertEquals(HttpStatusCode.RequestedRangeNotSatisfiable.value, result.response.status()?.value)
            assertEquals("bytes */${content.size}", result.response.headers[HttpHeaders.ContentRange])
        }
    }

    @Test
    fun testSyntacticallyIncorrectRange(): Unit = withRangeApplication {
        handleRequest(HttpMethod.Get, localPath) {
            addHeader(HttpHeaders.Range, "bytes=1000000-7") // syntactically incorrect
        }.let { result ->
            assertEquals(HttpStatusCode.OK, result.response.status())
        }
    }

    @Test
    fun testGoodAndBadTailRange(): Unit = withRangeApplication {
        handleRequest(HttpMethod.Get, localPath) {
            addHeader(HttpHeaders.Range, "bytes=0-0,-0") // good + unsatisfiable
        }.let { result ->
            assertEquals(HttpStatusCode.PartialContent, result.response.status())
            assertContentEquals(content.take(1).toByteArray(), result.response.byteContent)
            assertEquals("bytes 0-0/${content.size}", result.response.headers[HttpHeaders.ContentRange])
            assertNotNull(result.response.headers[HttpHeaders.LastModified])
            checkContentLength(result)
        }
    }

    @Test
    fun testGoodAndBadRange(): Unit = withRangeApplication {
        handleRequest(HttpMethod.Get, localPath) {
            addHeader(HttpHeaders.Range, "bytes=0-0,1000000-1000004") // good + unsatisfiable
        }.let { result ->
            assertEquals(HttpStatusCode.PartialContent, result.response.status())
            assertContentEquals(content.take(1).toByteArray(), result.response.byteContent)
            assertEquals("bytes 0-0/${content.size}", result.response.headers[HttpHeaders.ContentRange])
            assertNotNull(result.response.headers[HttpHeaders.LastModified])
            checkContentLength(result)
        }
    }

    @Test
    fun testHeadRequestRange(): Unit = withRangeApplication {
        // head request
        handleRequest(HttpMethod.Head, localPath) {
            addHeader(HttpHeaders.Range, "bytes=0-0")
        }.let { result ->
            assertEquals(HttpStatusCode.OK, result.response.status())
            assertNotNull(result.response.headers[HttpHeaders.LastModified])
            assertEquals(RangeUnits.Bytes.unitToken, result.response.headers[HttpHeaders.AcceptRanges])
            assertTrue { result.response.byteContent.let { it == null || it.isEmpty() } }
        }
    }

    @Test
    fun testPostRequestRange(): Unit = withRangeApplication {
        // post request
        handleRequest(HttpMethod.Post, localPath) {
            addHeader(HttpHeaders.Range, "bytes=0-0")
        }.let { result ->
            assertEquals(
                HttpStatusCode.MethodNotAllowed.description("Method POST is not allowed with range request"),
                result.response.status()
            )
        }
    }

    @Test
    fun testPostNoRange(): Unit = withRangeApplication {
        // post request with no range
        handleRequest(HttpMethod.Post, localPath) {
        }.let { result ->
            assertEquals(RangeUnits.Bytes.unitToken, result.response.headers[HttpHeaders.AcceptRanges])
            assertEquals(HttpStatusCode.OK, result.response.status())
        }
    }

    @Test
    fun testBypassContentLength(): Unit = withRangeApplication {
        handleRequest(HttpMethod.Get, localPath) {
        }.let { result ->
            assertEquals(content.size, result.response.headers[HttpHeaders.ContentLength]!!.toInt())
        }
    }

    @Test
    fun testMultipleMergedRanges(): Unit = withRangeApplication {
        // multiple ranges should be merged into one
        handleRequest(HttpMethod.Get, localPath) {
            addHeader(HttpHeaders.Range, "bytes=0-0,1-2")
        }.let { result ->
            assertEquals(HttpStatusCode.PartialContent, result.response.status())
            assertEquals("bytes 0-2/${content.size}", result.response.headers[HttpHeaders.ContentRange])
            assertContentEquals(content.take(3).toByteArray(), result.response.byteContent)
            assertNotNull(result.response.headers[HttpHeaders.LastModified])
            checkContentLength(result)
        }
    }

    @Test
    fun testDontCrashWithEmptyIfRange(): Unit = withRangeApplication {
        handleRequest(HttpMethod.Get, localPath) {
            addHeader(HttpHeaders.Range, "bytes=1-2")
            addHeader(HttpHeaders.IfRange, "")
        }.let { result ->
            assertEquals(HttpStatusCode.PartialContent, result.response.status())
            assertEquals("bytes 1-2/${content.size}", result.response.headers[HttpHeaders.ContentRange])
            checkContentLength(result)
        }
    }

    @Test
    fun testIfRangeETag(): Unit = withRangeApplication {
        handleRequest(HttpMethod.Get, localPath) {
            addHeader(HttpHeaders.Range, "bytes=1-2")
            addHeader(HttpHeaders.IfRange, "\"$fileEtag\"")
        }.let { result ->
            assertEquals(HttpStatusCode.PartialContent, result.response.status())
            assertEquals("bytes 1-2/${content.size}", result.response.headers[HttpHeaders.ContentRange])
        }

        handleRequest(HttpMethod.Get, localPath) {
            addHeader(HttpHeaders.Range, "bytes=1-2")
            addHeader(HttpHeaders.IfRange, "\"wrong-$fileEtag\"")
        }.let { result ->
            assertEquals(HttpStatusCode.OK, result.response.status())
            assertEquals(null, result.response.headers[HttpHeaders.ContentRange])
            checkContentLength(result)
        }
    }

    @Test
    fun testIfRangeDate(): Unit = withRangeApplication {
        val fileDate = GMTDate(lastModifiedTime)

        handleRequest(HttpMethod.Get, localPath) {
            addHeader(HttpHeaders.Range, "bytes=1-2")
            addHeader(HttpHeaders.IfRange, fileDate.toHttpDate())
        }.let { result ->
            assertEquals(HttpStatusCode.PartialContent, result.response.status())
            assertEquals("bytes 1-2/${content.size}", result.response.headers[HttpHeaders.ContentRange])
            checkContentLength(result)
        }

        handleRequest(HttpMethod.Get, localPath) {
            addHeader(HttpHeaders.Range, "bytes=1-2")
            addHeader(HttpHeaders.IfRange, fileDate.plus(10000).toHttpDate())
        }.let { result ->
            assertEquals(HttpStatusCode.PartialContent, result.response.status())
            assertEquals("bytes 1-2/${content.size}", result.response.headers[HttpHeaders.ContentRange])
            checkContentLength(result)
        }

        handleRequest(HttpMethod.Get, localPath) {
            addHeader(HttpHeaders.Range, "bytes=1-2")
            addHeader(HttpHeaders.IfRange, fileDate.minus(100000).toHttpDate())
        }.let { result ->
            assertEquals(HttpStatusCode.OK, result.response.status())
            assertEquals(null, result.response.headers[HttpHeaders.ContentRange])
        }
    }

    @Test
    fun testIfRangeWrongDate(): Unit = withRangeApplication {
        val fileDate = GMTDate(lastModifiedTime)

        handleRequest(HttpMethod.Get, localPath) {
            addHeader(HttpHeaders.Range, "bytes=1-2")
            addHeader(HttpHeaders.IfRange, fileDate.toHttpDate().drop(15))
        }.let { result ->
            assertEquals(HttpStatusCode.OK, result.response.status())
            assertEquals(null, result.response.headers[HttpHeaders.ContentRange])
        }
    }

    private fun checkContentLength(result: TestApplicationCall) {
        assertEquals(
            result.response.byteContent!!.size,
            result.response.headers[HttpHeaders.ContentLength]!!.toInt()
        )
    }
}
