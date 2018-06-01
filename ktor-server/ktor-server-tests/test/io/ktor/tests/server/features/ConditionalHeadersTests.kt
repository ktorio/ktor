package io.ktor.tests.server.features

import io.ktor.application.*
import io.ktor.http.content.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import io.ktor.util.*
import org.junit.*
import org.junit.Test
import org.junit.rules.*
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
    fun testNoConditions() = withConditionalApplication {
        val result = handleRequest {}
        assertTrue(result.requestHandled)
        assertEquals(HttpStatusCode.OK, result.response.status())
        assertEquals("response", result.response.content)
        assertEquals("tag1", result.response.headers[HttpHeaders.ETag])
    }


    @Test
    fun testIfMatchConditionAccepted() = withConditionalApplication {
        val result = handleRequest {
            addHeader(HttpHeaders.IfMatch, "tag1")
        }
        assertTrue(result.requestHandled)
        assertEquals(HttpStatusCode.OK, result.response.status())
        assertEquals("response", result.response.content)
        assertEquals("tag1", result.response.headers[HttpHeaders.ETag])
    }

    @Test
    fun testIfMatchConditionFailed() = withConditionalApplication {
        val result = handleRequest {
            addHeader(HttpHeaders.IfMatch, "tag2")
        }
        assertTrue(result.requestHandled)
        assertEquals(HttpStatusCode.PreconditionFailed, result.response.status())
    }

    @Test
    fun testIfNoneMatchConditionAccepted() = withConditionalApplication {
        val result = handleRequest {
            addHeader(HttpHeaders.IfNoneMatch, "tag1")
        }
        assertTrue(result.requestHandled)
        assertEquals(HttpStatusCode.NotModified, result.response.status())
    }

    @Test
    fun testIfNoneMatchWeakConditionAccepted() = withConditionalApplication {
        val result = handleRequest {
            addHeader(HttpHeaders.IfNoneMatch, "W/tag1")
        }
        assertTrue(result.requestHandled)
        assertEquals(HttpStatusCode.NotModified, result.response.status())
    }

    @Test
    fun testIfNoneMatchConditionFailed() = withConditionalApplication {
        val result = handleRequest {
            addHeader(HttpHeaders.IfNoneMatch, "tag2")
        }
        assertTrue(result.requestHandled)
        assertEquals(HttpStatusCode.OK, result.response.status())
        assertEquals("response", result.response.content)
        assertEquals("tag1", result.response.headers[HttpHeaders.ETag])
    }

    @Test
    fun testIfMatchStar() = withConditionalApplication {
        val result = handleRequest {
            addHeader(HttpHeaders.IfMatch, "*")
        }
        assertTrue(result.requestHandled)
        assertEquals(HttpStatusCode.OK, result.response.status())
        assertEquals("response", result.response.content)
        assertEquals("tag1", result.response.headers[HttpHeaders.ETag])
    }

    @Test
    fun testIfNoneMatchStar() = withConditionalApplication {
        val result = handleRequest {
            addHeader(HttpHeaders.IfNoneMatch, "*")
        }
        assertTrue(result.requestHandled)
        assertEquals(HttpStatusCode.OK, result.response.status())
        // note: star for if-none-match is a special case
        // that should be handled separately
        // so we always pass it
    }

    @Test
    fun testIfNoneMatchListConditionFailed() = withConditionalApplication {
        val result = handleRequest {
            addHeader(HttpHeaders.IfNoneMatch, "tag0,tag1,tag3")
        }
        assertTrue(result.requestHandled)
        assertEquals(HttpStatusCode.NotModified, result.response.status())
    }

    @Test
    fun testIfNoneMatchListConditionSuccess() = withConditionalApplication {
        val result = handleRequest {
            addHeader(HttpHeaders.IfNoneMatch, "tag2")
        }
        assertTrue(result.requestHandled)
        assertEquals(HttpStatusCode.OK, result.response.status())
        assertEquals("response", result.response.content)
        assertEquals("tag1", result.response.headers[HttpHeaders.ETag])
    }

    @Test
    fun testIfMatchListConditionAccepted() = withConditionalApplication {
        val result = handleRequest {
            addHeader(HttpHeaders.IfMatch, "tag0,tag1,tag3")
        }
        assertTrue(result.requestHandled)
        assertEquals(HttpStatusCode.OK, result.response.status())
        assertEquals("response", result.response.content)
        assertEquals("tag1", result.response.headers[HttpHeaders.ETag])
    }

    @Test
    fun testIfMatchListConditionFailed() = withConditionalApplication {
        val result = handleRequest {
            addHeader(HttpHeaders.IfMatch, "tag0,tag2,tag3")
        }
        assertTrue(result.requestHandled)
        assertEquals(HttpStatusCode.PreconditionFailed, result.response.status())
    }

}

class LastModifiedTest {
    private val date = ZonedDateTime.now(GreenwichMeanTime)!!

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
    fun testNoHeaders() = withConditionalApplication {
        handleRequest(HttpMethod.Get, "/").let { result ->
            assertEquals(HttpStatusCode.OK, result.response.status())
            assertEquals("response", result.response.content)
        }
    }

    @Test
    fun testIfModifiedSinceEq() = withConditionalApplication {
        handleRequest(HttpMethod.Get, "/", { addHeader(HttpHeaders.IfModifiedSince, date.toHttpDateString()) }).let { result ->
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

            handleRequest(HttpMethod.Get, "/", { addHeader(HttpHeaders.IfModifiedSince, date.toHttpDateString()) }).let { result ->
                assertEquals(HttpStatusCode.NotModified, result.response.status())
                assertNull(result.response.content)
            }
        }
    }

    @Test
    fun testIfModifiedSinceLess() = withConditionalApplication {
        handleRequest(HttpMethod.Get, "/", { addHeader(HttpHeaders.IfModifiedSince, date.minusDays(1).toHttpDateString()) }).let { result ->
            assertEquals(HttpStatusCode.OK, result.response.status())
            assertEquals("response", result.response.content)
        }
    }

    @Test
    fun testIfModifiedSinceGt() = withConditionalApplication {
        handleRequest(HttpMethod.Get, "/", { addHeader(HttpHeaders.IfModifiedSince, date.plusDays(1).toHttpDateString()) }).let { result ->
            assertEquals(HttpStatusCode.NotModified, result.response.status())
            assertNull(result.response.content)
        }
    }

    @Test
    fun testIfModifiedSinceTimeZoned() = withConditionalApplication {
        val expectedDate = date.toHttpDateString()
        val customFormat = httpDateFormat.withZone(ZoneId.of("Europe/Moscow"))!!

        handleRequest(HttpMethod.Get, "/", { addHeader(HttpHeaders.IfModifiedSince, customFormat.format(date).replace("MT", "MSK")) }).let { result ->
            assertEquals(HttpStatusCode.NotModified, result.response.status())
            assertNull(result.response.content)
        }

        handleRequest(HttpMethod.Get, "/", { addHeader(HttpHeaders.IfModifiedSince, customFormat.format(date.plusDays(1)).replace("MT", "MSK")) }).let { result ->
            assertEquals(HttpStatusCode.NotModified, result.response.status())
            assertNull(result.response.content)
        }

        handleRequest(HttpMethod.Get, "/", { addHeader(HttpHeaders.IfModifiedSince, customFormat.format(date.minusDays(1)).replace("MT", "MSK")) }).let { result ->
            assertEquals(HttpStatusCode.OK, result.response.status())
            assertEquals("response", result.response.content)
            assertEquals(expectedDate, result.response.headers[HttpHeaders.LastModified])
        }
    }

    @Test
    fun testIfUnModifiedSinceEq() = withConditionalApplication {
        handleRequest(HttpMethod.Get, "/", { addHeader(HttpHeaders.IfUnmodifiedSince, date.toHttpDateString()) }).let { result ->
            assertEquals(HttpStatusCode.OK, result.response.status())
            assertEquals("response", result.response.content)
        }
    }

    @Test
    fun testIfUnModifiedSinceLess() = withConditionalApplication {
        handleRequest(HttpMethod.Get, "/", { addHeader(HttpHeaders.IfUnmodifiedSince, date.minusDays(1).toHttpDateString()) }).let { result ->
            assertEquals(HttpStatusCode.PreconditionFailed, result.response.status())
            assertNull(result.response.content)
        }
    }

    @Test
    fun testIfUnModifiedSinceGt() = withConditionalApplication {
        handleRequest(HttpMethod.Get, "/", { addHeader(HttpHeaders.IfUnmodifiedSince, date.plusDays(1).toHttpDateString()) }).let { result ->
            assertEquals(HttpStatusCode.OK, result.response.status())
            assertEquals("response", result.response.content)
        }
    }
}


class LastModifiedVersionTest {
    private fun temporaryDefaultTimezone(timeZone : TimeZone, block : () -> Unit) {
        val originalTimeZone : TimeZone = TimeZone.getDefault()
        TimeZone.setDefault(timeZone)
        try {
            block()
        } finally {
            TimeZone.setDefault(originalTimeZone)
        }
    }

    private fun checkLastModifiedHeaderIsIndependentOfLocalTimezone(constructLastModifiedVersion : (Date) -> LastModifiedVersion) {
        // setup: any non-zero-offset-Timezone will do
        temporaryDefaultTimezone(TimeZone.getTimeZone("GMT+08:00")) {

            // guard: local default timezone needs to be different from GMT for the problem to manifest
            assertTrue(TimeZone.getDefault().rawOffset != 0, "invalid test setup - local timezone is GMT: ${TimeZone.getDefault()}")

            // setup: last modified for file
            val expectedLastModified : Date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z").parse("2018-03-04 15:12:23 GMT")

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
        checkLastModifiedHeaderIsIndependentOfLocalTimezone { input : Date -> LastModifiedVersion(input) }
    }

    @Test
    fun lastModifiedHeaderFromLocalDateTimeIsIndependentOfLocalTimezone() {
        checkLastModifiedHeaderIsIndependentOfLocalTimezone { input : Date ->
            LastModifiedVersion(ZonedDateTime.ofInstant(input.toInstant(), ZoneId.systemDefault()))
        }
    }

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun lastModifiedHeaderFromFileTimeIsIndependentOfLocalTimezone() {
        checkLastModifiedHeaderIsIndependentOfLocalTimezone { input : Date ->
            // setup: create file
            val file : File = temporaryFolder.newFile("foo.txt").apply {
                setLastModified(input.time)
            }

            // guard: file lastmodified is actually set as expected
            Assert.assertEquals(input.time, file.lastModified())

            // setup: object to test
            LastModifiedVersion(Files.getLastModifiedTime(file.toPath()))
        }
    }
}