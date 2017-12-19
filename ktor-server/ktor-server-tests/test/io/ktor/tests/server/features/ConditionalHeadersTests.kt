package io.ktor.tests.server.features

import io.ktor.application.*
import io.ktor.content.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import org.junit.Test
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
    val date = Date()
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
        handleRequest(HttpMethod.Get, "/", { addHeader(HttpHeaders.IfModifiedSince, date.toLocalDateTime().toHttpDateString()) }).let { result ->
            assertEquals(HttpStatusCode.NotModified, result.response.status())
            assertNull(result.response.content)
        }
    }

    @Test
    fun testIfModifiedSinceEqZoned() {
        val date = ZonedDateTime.now()
        withTestApplication {
            application.install(ConditionalHeaders) {
                version { listOf(LastModifiedVersion(date.toLocalDateTime())) }
            }
            application.routing {
                handle {
                    call.respondText("response")
                }
            }

            handleRequest(HttpMethod.Get, "/", { addHeader(HttpHeaders.IfModifiedSince, date.toLocalDateTime().toHttpDateString()) }).let { result ->
                assertEquals(HttpStatusCode.NotModified, result.response.status())
                assertNull(result.response.content)
            }
        }
    }

    @Test
    fun testIfModifiedSinceLess() = withConditionalApplication {
        handleRequest(HttpMethod.Get, "/", { addHeader(HttpHeaders.IfModifiedSince, date.toLocalDateTime().minusDays(1).toHttpDateString()) }).let { result ->
            assertEquals(HttpStatusCode.OK, result.response.status())
            assertEquals("response", result.response.content)
        }
    }

    @Test
    fun testIfModifiedSinceGt() = withConditionalApplication {
        handleRequest(HttpMethod.Get, "/", { addHeader(HttpHeaders.IfModifiedSince, date.toLocalDateTime().plusDays(1).toHttpDateString()) }).let { result ->
            assertEquals(HttpStatusCode.NotModified, result.response.status())
            assertNull(result.response.content)
        }
    }

    @Test
    fun testIfModifiedSinceTimeZoned() = withConditionalApplication {
        val expectedDate = date.toLocalDateTime().toHttpDateString()
        val customFormat = httpDateFormat.withZone(ZoneId.of("Europe/Moscow"))!!

        handleRequest(HttpMethod.Get, "/", { addHeader(HttpHeaders.IfModifiedSince, customFormat.format(date.toLocalDateTime()).replace("MT", "MSK")) }).let { result ->
            assertEquals(HttpStatusCode.NotModified, result.response.status())
            assertNull(result.response.content)
        }

        handleRequest(HttpMethod.Get, "/", { addHeader(HttpHeaders.IfModifiedSince, customFormat.format(date.toLocalDateTime().plusDays(1)).replace("MT", "MSK")) }).let { result ->
            assertEquals(HttpStatusCode.NotModified, result.response.status())
            assertNull(result.response.content)
        }

        handleRequest(HttpMethod.Get, "/", { addHeader(HttpHeaders.IfModifiedSince, customFormat.format(date.toLocalDateTime().minusDays(1)).replace("MT", "MSK")) }).let { result ->
            assertEquals(HttpStatusCode.OK, result.response.status())
            assertEquals("response", result.response.content)
            assertEquals(expectedDate, result.response.headers[HttpHeaders.LastModified])
        }
    }

    @Test
    fun testIfUnModifiedSinceEq() = withConditionalApplication {
        handleRequest(HttpMethod.Get, "/", { addHeader(HttpHeaders.IfUnmodifiedSince, date.toLocalDateTime().toHttpDateString()) }).let { result ->
            assertEquals(HttpStatusCode.OK, result.response.status())
            assertEquals("response", result.response.content)
        }
    }

    @Test
    fun testIfUnModifiedSinceLess() = withConditionalApplication {
        handleRequest(HttpMethod.Get, "/", { addHeader(HttpHeaders.IfUnmodifiedSince, date.toLocalDateTime().minusDays(1).toHttpDateString()) }).let { result ->
            assertEquals(HttpStatusCode.PreconditionFailed, result.response.status())
            assertNull(result.response.content)
        }
    }

    @Test
    fun testIfUnModifiedSinceGt() = withConditionalApplication {
        handleRequest(HttpMethod.Get, "/", { addHeader(HttpHeaders.IfUnmodifiedSince, date.toLocalDateTime().plusDays(1).toHttpDateString()) }).let { result ->
            assertEquals(HttpStatusCode.OK, result.response.status())
            assertEquals("response", result.response.content)
        }
    }

    private fun Date.toLocalDateTime() = LocalDateTime.ofInstant(toInstant(), ZoneId.systemDefault())
}
