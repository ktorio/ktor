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

class ETagsTest {
    private fun withConditionalApplication(body: TestApplicationEngine.() -> Unit) = withTestApplication {
        application.install(ConditionalHeaders) {
            version { listOf(EntityTagVersion("tag1")) }
        }
        application.routing {
            handle {
                call.respondText("response")
            }
        }

        body()
    }

    @Test
    fun testNoConditions(): Unit = withConditionalApplication {
        val result = handleRequest {}
        assertEquals(HttpStatusCode.OK, result.response.status())
        assertEquals("response", result.response.content)
        assertEquals("\"tag1\"", result.response.headers[HttpHeaders.ETag])
    }

    @Test
    fun testIfMatchConditionAccepted(): Unit = withConditionalApplication {
        val result = handleRequest {
            addHeader(HttpHeaders.IfMatch, "tag1")
        }
        assertEquals(HttpStatusCode.OK, result.response.status())
        assertEquals("response", result.response.content)
        assertEquals("\"tag1\"", result.response.headers[HttpHeaders.ETag])
    }

    @Test
    fun testIfMatchConditionFailed(): Unit = withConditionalApplication {
        val result = handleRequest {
            addHeader(HttpHeaders.IfMatch, "tag2")
        }
        assertEquals(HttpStatusCode.PreconditionFailed, result.response.status())
    }

    @Test
    fun testIfNoneMatchConditionAccepted(): Unit = withConditionalApplication {
        val result = handleRequest {
            addHeader(HttpHeaders.IfNoneMatch, "tag1")
        }
        assertEquals(HttpStatusCode.NotModified, result.response.status())
        assertEquals("\"tag1\"", result.response.headers[HttpHeaders.ETag])
    }

    @Test
    fun testIfNoneMatchWeakConditionAccepted(): Unit = withConditionalApplication {
        val result = handleRequest {
            addHeader(HttpHeaders.IfNoneMatch, "W/tag1")
        }
        assertEquals(HttpStatusCode.NotModified, result.response.status())
    }

    @Test
    fun testIfNoneMatchConditionFailed(): Unit = withConditionalApplication {
        val result = handleRequest {
            addHeader(HttpHeaders.IfNoneMatch, "tag2")
        }
        assertEquals(HttpStatusCode.OK, result.response.status())
        assertEquals("response", result.response.content)
        assertEquals("\"tag1\"", result.response.headers[HttpHeaders.ETag])
    }

    @Test
    fun testIfMatchStar(): Unit = withConditionalApplication {
        val result = handleRequest {
            addHeader(HttpHeaders.IfMatch, "*")
        }
        assertEquals(HttpStatusCode.OK, result.response.status())
        assertEquals("response", result.response.content)
        assertEquals("\"tag1\"", result.response.headers[HttpHeaders.ETag])
    }

    @Test
    fun testIfNoneMatchStar(): Unit = withConditionalApplication {
        val result = handleRequest {
            addHeader(HttpHeaders.IfNoneMatch, "*")
        }
        assertEquals(HttpStatusCode.OK, result.response.status())
        // note: star for if-none-match is a special case
        // that should be handled separately
        // so we always pass it
    }

    @Test
    fun testIfNoneMatchListConditionFailed(): Unit = withConditionalApplication {
        val result = handleRequest {
            addHeader(HttpHeaders.IfNoneMatch, "tag0,tag1,tag3")
        }
        assertEquals(HttpStatusCode.NotModified, result.response.status())
    }

    @Test
    fun testIfNoneMatchListConditionSuccess(): Unit = withConditionalApplication {
        val result = handleRequest {
            addHeader(HttpHeaders.IfNoneMatch, "tag2")
        }
        assertEquals(HttpStatusCode.OK, result.response.status())
        assertEquals("response", result.response.content)
        assertEquals("\"tag1\"", result.response.headers[HttpHeaders.ETag])
    }

    @Test
    fun testIfMatchListConditionAccepted(): Unit = withConditionalApplication {
        val result = handleRequest {
            addHeader(HttpHeaders.IfMatch, "tag0,tag1,tag3")
        }
        assertEquals(HttpStatusCode.OK, result.response.status())
        assertEquals("response", result.response.content)
        assertEquals("\"tag1\"", result.response.headers[HttpHeaders.ETag])
    }

    @Test
    fun testIfMatchListConditionFailed(): Unit = withConditionalApplication {
        val result = handleRequest {
            addHeader(HttpHeaders.IfMatch, "tag0,tag2,tag3")
        }
        assertEquals(HttpStatusCode.PreconditionFailed, result.response.status())
    }
}

@RunWith(Parameterized::class)
class LastModifiedTest(@Suppress("UNUSED_PARAMETER") name: String, zone: ZoneId) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun zones(): List<Array<Any>> =
            listOf(arrayOf<Any>("GMT", ZoneId.of("GMT")), arrayOf<Any>("SomeLocal", ZoneId.of("GMT+1")))
    }

    private val date = ZonedDateTime.now(zone)!!

    private fun withConditionalApplication(body: TestApplicationEngine.() -> Unit) = withTestApplication {
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
    fun testNoHeaders(): Unit = withConditionalApplication {
        handleRequest(HttpMethod.Get, "/").let { result ->
            assertEquals(HttpStatusCode.OK, result.response.status())
            assertEquals("response", result.response.content)
        }
    }

    @Test
    fun testIfModifiedSinceEq(): Unit = withConditionalApplication {
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

    @Test
    fun testIfModifiedSinceEqZoned() {
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
    fun testIfModifiedSinceLess(): Unit = withConditionalApplication {
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

    @Test
    fun testIfModifiedSinceGt(): Unit = withConditionalApplication {
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

    @Test
    fun testIfUnModifiedSinceEq(): Unit = withConditionalApplication {
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

    @Test
    fun testIfUnModifiedSinceLess(): Unit = withConditionalApplication {
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

    @Test
    fun testIfUnModifiedSinceGt(): Unit = withConditionalApplication {
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

    @Test
    fun testIfUnmodifiedSinceIllegal(): Unit = withConditionalApplication {
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

    @Test
    fun testIfUnmodifiedSinceMultiple(): Unit = withConditionalApplication {
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

    @Test
    fun testIfModifiedSinceMultiple(): Unit = withConditionalApplication {
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

    @Test
    fun testBoth(): Unit = withConditionalApplication {
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

    @Test
    fun lastModifiedHeaderFromDateIsIndependentOfLocalTimezone() {
        checkLastModifiedHeaderIsIndependentOfLocalTimezone { input: Date ->
            LastModifiedVersion(input)
        }
    }

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
