/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.features

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import io.ktor.test.dispatcher.*
import org.junit.*
import org.junit.Test
import org.junit.rules.*
import org.junit.runner.*
import org.junit.runners.*
import java.io.*
import java.nio.file.*
import java.text.*
import java.time.*
import java.util.*
import kotlin.test.*

@RunWith(Parameterized::class)
class LastModifiedTest(@Suppress("UNUSED_PARAMETER") name: String, zone: ZoneId) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun zones(): List<Array<Any>> =
            listOf(arrayOf<Any>("GMT", ZoneId.of("GMT")), arrayOf<Any>("SomeLocal", ZoneId.of("GMT+1")))
    }

    private val date = ZonedDateTime.now(zone)!!

    private inline fun withConditionalApplication(body: TestApplicationEngine.() -> Unit) = withTestApplication {
        application.install(ConditionalHeaders) {
            version { listOf(LastModifiedVersion(date)) }
        }
        application.routing {
            handle {
                call.respondText("response")
            }
        }

        body()
    }

    @Test
    fun testNoHeaders() = testSuspend {
        withConditionalApplication {
            handleRequest(HttpMethod.Get, "/").let { result ->
                assertEquals(HttpStatusCode.OK, result.response.status())
                assertEquals("response", result.response.content)
            }
        }
    }

    @Test
    fun testIfModifiedSinceEq() = testSuspend {
        withConditionalApplication {
            handleRequest(HttpMethod.Get, "/") {
                addHeader(
                    HttpHeaders.IfModifiedSince,
                    date.toHttpDateString()
                )
            }.let { result ->
                assertEquals(HttpStatusCode.NotModified, result.response.status())
                assertNull(result.response.content)
            }
        }
    }

    @Test
    fun testIfModifiedSinceEqZoned() = testSuspend {
        withTestApplication {
            application.install(ConditionalHeaders) {
                version { listOf(LastModifiedVersion(date)) }
            }
            application.routing {
                handle {
                    call.respondText("response")
                }
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(
                    HttpHeaders.IfModifiedSince,
                    date.toHttpDateString()
                )
            }.let { result ->
                assertEquals(HttpStatusCode.NotModified, result.response.status())
                assertNull(result.response.content)
            }
        }
    }

    @Test
    fun testIfModifiedSinceLess() = testSuspend {
        withConditionalApplication {
            handleRequest(HttpMethod.Get, "/") {
                addHeader(
                    HttpHeaders.IfModifiedSince,
                    date.minusDays(1).toHttpDateString()
                )
            }.let { result ->
                assertEquals(HttpStatusCode.OK, result.response.status())
                assertEquals("response", result.response.content)
            }
        }
    }

    @Test
    fun testIfModifiedSinceGt() = testSuspend {
        withConditionalApplication {
            handleRequest(HttpMethod.Get, "/") {
                addHeader(
                    HttpHeaders.IfModifiedSince,
                    date.plusDays(1).toHttpDateString()
                )
            }.let { result ->
                assertEquals(HttpStatusCode.NotModified, result.response.status())
                assertNull(result.response.content)
            }
        }
    }

    @Test
    fun testIfUnModifiedSinceEq() = testSuspend {
        withConditionalApplication {
            handleRequest(HttpMethod.Get, "/") {
                addHeader(
                    HttpHeaders.IfUnmodifiedSince,
                    date.toHttpDateString()
                )
            }.let { result ->
                assertEquals(HttpStatusCode.OK, result.response.status())
                assertEquals("response", result.response.content)
            }
        }
    }

    @Test
    fun testIfUnModifiedSinceLess() = testSuspend {
        withConditionalApplication {
            handleRequest(HttpMethod.Get, "/") {
                addHeader(
                    HttpHeaders.IfUnmodifiedSince,
                    date.minusDays(1).toHttpDateString()
                )
            }.let { result ->
                assertEquals(HttpStatusCode.PreconditionFailed, result.response.status())
                assertNull(result.response.content)
            }
        }
    }

    @Test
    fun testIfUnModifiedSinceGt() = testSuspend {
        withConditionalApplication {
            handleRequest(HttpMethod.Get, "/") {
                addHeader(
                    HttpHeaders.IfUnmodifiedSince,
                    date.plusDays(1).toHttpDateString()
                )
            }.let { result ->
                assertEquals(HttpStatusCode.OK, result.response.status())
                assertEquals("response", result.response.content)
            }
        }
    }

    @Test
    fun testIfUnmodifiedSinceIllegal() = testSuspend {
        withConditionalApplication {
            handleRequest(HttpMethod.Get, "/") {
                addHeader(
                    HttpHeaders.IfUnmodifiedSince,
                    "zzz"
                )
            }.let { result ->
                assertEquals(HttpStatusCode.OK, result.response.status())
                assertEquals("response", result.response.content)
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(
                    HttpHeaders.IfUnmodifiedSince,
                    "zzz"
                )
                addHeader(
                    HttpHeaders.IfUnmodifiedSince,
                    date.toHttpDateString()
                )
            }.let { result ->
                assertEquals(HttpStatusCode.OK, result.response.status())
                assertEquals("response", result.response.content)
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(
                    HttpHeaders.IfUnmodifiedSince,
                    "zzz"
                )
                addHeader(
                    HttpHeaders.IfUnmodifiedSince,
                    date.plusDays(1).toHttpDateString()
                )
            }.let { result ->
                assertEquals(HttpStatusCode.OK, result.response.status())
                assertEquals("response", result.response.content)
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(
                    HttpHeaders.IfUnmodifiedSince,
                    "zzz"
                )
                addHeader(
                    HttpHeaders.IfUnmodifiedSince,
                    date.plusDays(-1).toHttpDateString()
                )
            }.let { result ->
                assertEquals(HttpStatusCode.PreconditionFailed, result.response.status())
                assertEquals(null, result.response.content)
            }
        }
    }

    @Test
    fun testIfUnmodifiedSinceMultiple() = testSuspend {
        withConditionalApplication {
            handleRequest(HttpMethod.Get, "/") {
                addHeader(
                    HttpHeaders.IfUnmodifiedSince,
                    ""
                )
            }.let { result ->
                assertEquals(HttpStatusCode.OK, result.response.status())
                assertEquals("response", result.response.content)
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(
                    HttpHeaders.IfUnmodifiedSince,
                    date.plusDays(1).toHttpDateString()
                )
                addHeader(
                    HttpHeaders.IfUnmodifiedSince,
                    date.plusDays(2).toHttpDateString()
                )
            }.let { result ->
                assertEquals(HttpStatusCode.OK, result.response.status())
                assertEquals("response", result.response.content)
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(
                    HttpHeaders.IfUnmodifiedSince,
                    date.plusDays(-1).toHttpDateString()
                )
                addHeader(
                    HttpHeaders.IfUnmodifiedSince,
                    date.plusDays(2).toHttpDateString()
                )
            }.let { result ->
            assertEquals(HttpStatusCode.PreconditionFailed, result.response.status())
                assertNull(result.response.content)
            }
        }
    }

    @Test
    fun testIfModifiedSinceMultiple() = testSuspend {
        withConditionalApplication {
            handleRequest(HttpMethod.Get, "/") {
                addHeader(
                    HttpHeaders.IfModifiedSince,
                    ""
                )
            }.let { result ->
                assertEquals(HttpStatusCode.OK, result.response.status())
                assertEquals("response", result.response.content)
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(
                    HttpHeaders.IfModifiedSince,
                    date.plusDays(-1).toHttpDateString()
                )
                addHeader(
                    HttpHeaders.IfModifiedSince,
                    date.plusDays(2).toHttpDateString()
                )
            }.let { result ->
                assertEquals(HttpStatusCode.OK, result.response.status())
                assertEquals("response", result.response.content)
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(
                    HttpHeaders.IfModifiedSince,
                    date.plusDays(1).toHttpDateString()
                )
                addHeader(
                    HttpHeaders.IfModifiedSince,
                    date.plusDays(2).toHttpDateString()
                )
            }.let { result ->
            assertEquals(HttpStatusCode.NotModified, result.response.status())
                assertNull(result.response.content)
            }
        }
    }

    @Test
    fun testBoth() = testSuspend {
        withConditionalApplication {
            handleRequest(HttpMethod.Get, "/") {
                addHeader(
                    HttpHeaders.IfModifiedSince,
                    ""
                )
                addHeader(
                    HttpHeaders.IfUnmodifiedSince,
                    ""
                )
            }.let { result ->
                assertEquals(HttpStatusCode.OK, result.response.status())
                assertEquals("response", result.response.content)
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(
                    HttpHeaders.IfModifiedSince,
                    date.plusDays(-1).toHttpDateString()
                )
                addHeader(
                    HttpHeaders.IfUnmodifiedSince,
                    date.plusDays(1).toHttpDateString()
                )
            }.let { result ->
                assertEquals(HttpStatusCode.OK, result.response.status())
                assertEquals("response", result.response.content)
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(
                    HttpHeaders.IfModifiedSince,
                    date.plusDays(-1).toHttpDateString()
                )
                addHeader(
                    HttpHeaders.IfUnmodifiedSince,
                    date.plusDays(-1).toHttpDateString()
                )
            }.let { result ->
                assertEquals(HttpStatusCode.PreconditionFailed, result.response.status())
                assertEquals(null, result.response.content)
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(
                    HttpHeaders.IfModifiedSince,
                    date.plusDays(1).toHttpDateString()
                )
                addHeader(
                    HttpHeaders.IfUnmodifiedSince,
                    date.plusDays(1).toHttpDateString()
                )
            }.let { result ->
                assertEquals(HttpStatusCode.NotModified, result.response.status())
                assertEquals(null, result.response.content)
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(
                    HttpHeaders.IfModifiedSince,
                    date.plusDays(1).toHttpDateString()
                )
                addHeader(
                    HttpHeaders.IfUnmodifiedSince,
                    date.plusDays(-1).toHttpDateString()
                )
            }.let { result ->
                // both conditions are not met but actually it is not clear which one should win
                // so we declare the order
                assertEquals(HttpStatusCode.NotModified, result.response.status())
                assertEquals(null, result.response.content)
            }
        }
    }
}

class LastModifiedVersionTest {
    private fun temporaryDefaultTimezone(timeZone: TimeZone, block: () -> Unit) {
        val originalTimeZone: TimeZone = TimeZone.getDefault()
        TimeZone.setDefault(timeZone)
        try {
            block()
        } finally {
            TimeZone.setDefault(originalTimeZone)
        }
    }

    private fun checkLastModifiedHeaderIsIndependentOfLocalTimezone(
        constructLastModifiedVersion: (Date) -> LastModifiedVersion
    ) {
        // setup: any non-zero-offset-Timezone will do
        temporaryDefaultTimezone(TimeZone.getTimeZone("GMT+08:00")) {
            // guard: local default timezone needs to be different from GMT for the problem to manifest
            assertTrue(
                TimeZone.getDefault().rawOffset != 0,
                "invalid test setup - local timezone is GMT: ${TimeZone.getDefault()}"
            )

            // setup: last modified for file
            val expectedLastModified: Date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z").parse("2018-03-04 15:12:23 GMT")

            // setup: object to test
            val lastModifiedVersion = constructLastModifiedVersion(expectedLastModified)

            // setup HeadersBuilder as test spy
            val headersBuilder = HeadersBuilder()

            // exercise
            lastModifiedVersion.appendHeadersTo(headersBuilder)

            // check
            assertEquals("Sun, 04 Mar 2018 15:12:23 GMT", headersBuilder["Last-Modified"])
        }
    }

//    @Test
//    fun lastModifiedHeaderFromDateIsIndependentOfLocalTimezone() {
//        checkLastModifiedHeaderIsIndependentOfLocalTimezone { input: Date ->
//            LastModifiedVersion(input)
//        }
//    }

    @Test
    fun lastModifiedHeaderFromLocalDateTimeIsIndependentOfLocalTimezone() {
        checkLastModifiedHeaderIsIndependentOfLocalTimezone { input: Date ->
            LastModifiedVersion(ZonedDateTime.ofInstant(input.toInstant(), ZoneId.systemDefault()))
        }
    }

    @get:Rule
    val temporaryFolder: TemporaryFolder = TemporaryFolder()

    @Test
    fun lastModifiedHeaderFromFileTimeIsIndependentOfLocalTimezone() {
        checkLastModifiedHeaderIsIndependentOfLocalTimezone { input: Date ->
            // setup: create file
            val file: File = temporaryFolder.newFile("foo.txt").apply {
                setLastModified(input.time)
            }

            // guard: file lastmodified is actually set as expected
            assertEquals(input.time, file.lastModified())

            // setup: object to test
            LastModifiedVersion(Files.getLastModifiedTime(file.toPath()))
        }
    }
}
