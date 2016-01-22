package org.jetbrains.ktor.tests.http

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.testing.*
import org.jetbrains.ktor.tests.*
import org.junit.*
import java.time.*
import java.util.*
import kotlin.test.*

class ETagsTest {
    @Test
    fun testNoConditions() {
        withTestApplication {
            application.routing {
                handle {
                    withETag("tag1") {
                        response.sendText("response")
                    }
                }
            }

            val result = handleRequest {}
            assertEquals(ApplicationCallResult.Handled, result.requestResult)
            assertEquals(HttpStatusCode.OK, result.response.status())
            assertEquals("response", result.response.content)
            assertEquals("tag1", result.response.headers[HttpHeaders.ETag])
        }
    }

    @Test
    fun testIfMatchConditionAccepted() {
        withTestApplication {
            application.routing {
                handle {
                    withETag("tag1") {
                        response.sendText("response")
                    }
                }
            }

            val result = handleRequest {
                addHeader(HttpHeaders.IfMatch, "tag1")
            }
            assertEquals(ApplicationCallResult.Handled, result.requestResult)
            assertEquals(HttpStatusCode.OK, result.response.status())
            assertEquals("response", result.response.content)
            assertEquals("tag1", result.response.headers[HttpHeaders.ETag])
        }
    }

    @Test
    fun testIfMatchConditionFailed() {
        withTestApplication {
            application.routing {
                handle {
                    withETag("tag1") {
                        response.sendText("response")
                    }
                }
            }

            val result = handleRequest {
                addHeader(HttpHeaders.IfMatch, "tag2")
            }
            assertEquals(ApplicationCallResult.Handled, result.requestResult)
            assertEquals(HttpStatusCode.PreconditionFailed, result.response.status())
        }
    }

    @Test
    fun testIfNoneMatchConditionAccepted() {
        withTestApplication {
            application.routing {
                handle {
                    withETag("tag1") {
                        response.sendText("response")
                    }
                }
            }

            val result = handleRequest {
                addHeader(HttpHeaders.IfNoneMatch, "tag1")
            }
            assertEquals(ApplicationCallResult.Handled, result.requestResult)
            assertEquals(HttpStatusCode.NotModified, result.response.status())
        }
    }

    @Test
    fun testIfNoneMatchWeakConditionAccepted() {
        withTestApplication {
            application.routing {
                handle {
                    withETag("tag1") {
                        response.sendText("response")
                    }
                }
            }

            val result = handleRequest {
                addHeader(HttpHeaders.IfNoneMatch, "W/tag1")
            }
            assertEquals(ApplicationCallResult.Handled, result.requestResult)
            assertEquals(HttpStatusCode.NotModified, result.response.status())
        }
    }

    @Test
    fun testIfNoneMatchConditionFailed() {
        withTestApplication {
            application.routing {
                handle {
                    withETag("tag1") {
                        response.sendText("response")
                    }
                }
            }

            val result = handleRequest {
                addHeader(HttpHeaders.IfNoneMatch, "tag2")
            }
            assertEquals(ApplicationCallResult.Handled, result.requestResult)
            assertEquals(HttpStatusCode.OK, result.response.status())
            assertEquals("response", result.response.content)
            assertEquals("tag1", result.response.headers[HttpHeaders.ETag])
        }
    }

    @Test
    fun testIfMatchStar() {
        withTestApplication {
            application.routing {
                handle {
                    withETag("tag1") {
                        response.sendText("response")
                    }
                }
            }

            val result = handleRequest {
                addHeader(HttpHeaders.IfMatch, "*")
            }
            assertEquals(ApplicationCallResult.Handled, result.requestResult)
            assertEquals(HttpStatusCode.OK, result.response.status())
            assertEquals("response", result.response.content)
            assertEquals("tag1", result.response.headers[HttpHeaders.ETag])
        }
    }

    @Test
    fun testIfNoneMatchStar() {
        withTestApplication {
            application.routing {
                handle {
                    withETag("tag1") {
                        response.sendText("response")
                    }
                }
            }

            val result = handleRequest {
                addHeader(HttpHeaders.IfNoneMatch, "*")
            }
            assertEquals(ApplicationCallResult.Handled, result.requestResult)
            assertEquals(HttpStatusCode.OK, result.response.status())
            // note: star for if-none-match is a special case
            // that should be handled separately
            // so we always pass it
        }
    }

    @Test
    fun testIfNoneMatchListConditionFailed() {
        withTestApplication {
            application.routing {
                handle {
                    withETag("tag1") {
                        response.sendText("response")
                    }
                }
            }

            val result = handleRequest {
                addHeader(HttpHeaders.IfNoneMatch, "tag0,tag1,tag3")
            }
            assertEquals(ApplicationCallResult.Handled, result.requestResult)
            assertEquals(HttpStatusCode.NotModified, result.response.status())
        }
    }

    @Test
    fun testIfNoneMatchListConditionSuccess() {
        withTestApplication {
            application.routing {
                handle {
                    withETag("tag1") {
                        response.sendText("response")
                    }
                }
            }

            val result = handleRequest {
                addHeader(HttpHeaders.IfNoneMatch, "tag2")
            }
            assertEquals(ApplicationCallResult.Handled, result.requestResult)
            assertEquals(HttpStatusCode.OK, result.response.status())
            assertEquals("response", result.response.content)
            assertEquals("tag1", result.response.headers[HttpHeaders.ETag])
        }
    }

    @Test
    fun testIfMatchListConditionAccepted() {
        withTestApplication {
            application.routing {
                handle {
                    withETag("tag1") {
                        response.sendText("response")
                    }
                }
            }

            val result = handleRequest {
                addHeader(HttpHeaders.IfMatch, "tag0,tag1,tag3")
            }
            assertEquals(ApplicationCallResult.Handled, result.requestResult)
            assertEquals(HttpStatusCode.OK, result.response.status())
            assertEquals("response", result.response.content)
            assertEquals("tag1", result.response.headers[HttpHeaders.ETag])
        }
    }

    @Test
    fun testIfMatchListConditionFailed() {
        withTestApplication {
            application.routing {
                handle {
                    withETag("tag1") {
                        response.sendText("response")
                    }
                }
            }

            val result = handleRequest {
                addHeader(HttpHeaders.IfMatch, "tag0,tag2,tag3")
            }
            assertEquals(ApplicationCallResult.Handled, result.requestResult)
            assertEquals(HttpStatusCode.PreconditionFailed, result.response.status())
        }
    }

}

class LastModifiedTest {
    @Test
    fun testNoHeaders() {
        val date = Date()
        withTestApplication {
            application.routing {
                handle {
                    withLastModified(date) {
                        response.sendText("response")
                    }
                }
            }

            handleRequest(HttpMethod.Get, "/").let { result ->
                assertEquals(HttpStatusCode.OK, result.response.status())
                assertEquals("response", result.response.content)
            }
        }
    }

    @Test
    fun testIfModifiedSinceEq() {
        val date = Date()
        withTestApplication {
            application.routing {
                handle {
                    withLastModified(date) {
                        response.sendText("response")
                    }
                }
            }

            handleRequest(HttpMethod.Get, "/", { addHeader(HttpHeaders.IfModifiedSince, date.toLocalDateTime().toHttpDateString()) }).let { result ->
                assertEquals(HttpStatusCode.NotModified, result.response.status())
                assertNull(result.response.content)
            }
        }
    }

    @Test
    fun testIfModifiedSinceLess() {
        val date = Date()
        withTestApplication {
            application.routing {
                handle {
                    withLastModified(date) {
                        response.sendText("response")
                    }
                }
            }

            handleRequest(HttpMethod.Get, "/", { addHeader(HttpHeaders.IfModifiedSince, date.toLocalDateTime().minusDays(1).toHttpDateString()) }).let { result ->
                assertEquals(HttpStatusCode.OK, result.response.status())
                assertEquals("response", result.response.content)
            }
        }
    }

    @Test
    fun testIfModifiedSinceGt() {
        val date = Date()
        withTestApplication {
            application.routing {
                handle {
                    withLastModified(date) {
                        response.sendText("response")
                    }
                }
            }

            handleRequest(HttpMethod.Get, "/", { addHeader(HttpHeaders.IfModifiedSince, date.toLocalDateTime().plusDays(1).toHttpDateString()) }).let { result ->
                assertEquals(HttpStatusCode.NotModified, result.response.status())
                assertNull(result.response.content)
            }
        }
    }

    @Test
    fun testIfUnModifiedSinceEq() {
        val date = Date()
        withTestApplication {
            application.routing {
                handle {
                    withLastModified(date) {
                        response.sendText("response")
                    }
                }
            }

            handleRequest(HttpMethod.Get, "/", { addHeader(HttpHeaders.IfUnmodifiedSince, date.toLocalDateTime().toHttpDateString()) }).let { result ->
                assertEquals(HttpStatusCode.OK, result.response.status())
                assertEquals("response", result.response.content)
            }
        }
    }

    @Test
    fun testIfUnModifiedSinceLess() {
        val date = Date()
        withTestApplication {
            application.routing {
                handle {
                    withLastModified(date) {
                        response.sendText("response")
                    }
                }
            }

            handleRequest(HttpMethod.Get, "/", { addHeader(HttpHeaders.IfUnmodifiedSince, date.toLocalDateTime().minusDays(1).toHttpDateString()) }).let { result ->
                assertEquals(HttpStatusCode.PreconditionFailed, result.response.status())
                assertNull(result.response.content)
            }
        }
    }

    @Test
    fun testIfUnModifiedSinceGt() {
        val date = Date()
        withTestApplication {
            application.routing {
                handle {
                    withLastModified(date) {
                        response.sendText("response")
                    }
                }
            }

            handleRequest(HttpMethod.Get, "/", { addHeader(HttpHeaders.IfUnmodifiedSince, date.toLocalDateTime().plusDays(1).toHttpDateString()) }).let { result ->
                assertEquals(HttpStatusCode.OK, result.response.status())
                assertEquals("response", result.response.content)
            }
        }
    }
}

class IfRangeTest {
    @Test
    fun testNoHeadersDate() {
        val date = Date()

        withTestApplication {
            application.routing {
                handle {
                    withIfRange(date) { rangeOrNull ->
                        assertNull(rangeOrNull)
                        response.sendText("ok")
                    }
                }
            }

            handleRequest(HttpMethod.Get, "/").let { result ->
                assertEquals(ApplicationCallResult.Handled, result.requestResult)
                assertEquals(HttpStatusCode.OK, result.response.status())
                assertEquals("ok", result.response.content)
            }
        }
    }

    @Test
    fun testIfRangeOnly() {
        val date = Date()

        withTestApplication {
            application.routing {
                handle {
                    withIfRange(date) { rangeOrNull ->
                        assertNull(rangeOrNull)
                        response.sendText("ok")
                    }
                }
            }

            handleRequest(HttpMethod.Get, "/", { addHeader(HttpHeaders.IfRange, date.toLocalDateTime().toHttpDateString()) }).let { result ->
                assertEquals(ApplicationCallResult.Handled, result.requestResult)
                assertEquals(HttpStatusCode.OK, result.response.status())
                assertEquals("ok", result.response.content)
            }

            handleRequest(HttpMethod.Get, "/", { addHeader(HttpHeaders.IfRange, date.toLocalDateTime().plusDays(1).toHttpDateString()) }).let { result ->
                assertEquals(ApplicationCallResult.Handled, result.requestResult)
                assertEquals(HttpStatusCode.OK, result.response.status())
                assertEquals("ok", result.response.content)
            }

            handleRequest(HttpMethod.Get, "/", { addHeader(HttpHeaders.IfRange, date.toLocalDateTime().minusDays(1).toHttpDateString()) }).let { result ->
                assertEquals(ApplicationCallResult.Handled, result.requestResult)
                assertEquals(HttpStatusCode.OK, result.response.status())
                assertEquals("ok", result.response.content)
            }
        }
    }

    @Test
    fun testRangeOnly() {
        val date = Date()

        withTestApplication {
            application.routing {
                handle {
                    withIfRange(date) { rangeOrNull ->
                        assertNotNull(rangeOrNull)
                        response.sendText("ok")
                    }
                }
            }

            handleRequest(HttpMethod.Get, "/", { addHeader(HttpHeaders.Range, "bytes=0-1") }).let { result ->
                assertEquals(ApplicationCallResult.Handled, result.requestResult)
                assertEquals(HttpStatusCode.PartialContent, result.response.status())
                assertEquals("ok", result.response.content)
            }
        }
    }

    @Test
    fun testNoRangeOnlyWithIfModifiedSince() {
        val date = Date()

        withTestApplication {
            application.routing {
                handle {
                    withIfRange(date) { rangeOrNull ->
                        fail("should never reach here")
                    }
                }
            }

            handleRequest(HttpMethod.Get, "/", { addHeader(HttpHeaders.IfModifiedSince, date.toLocalDateTime().toHttpDateString()) }).let { result ->
                assertEquals(ApplicationCallResult.Handled, result.requestResult)
                assertEquals(HttpStatusCode.NotModified, result.response.status())
                assertNull(result.response.content)
            }
        }
    }

    @Test
    fun testNoRangeOnlyWithIfModifiedSinceLt() {
        val date = Date()

        withTestApplication {
            application.routing {
                handle {
                    withIfRange(date) { rangeOrNull ->
                        assertNull(rangeOrNull)
                        response.sendText("ok")
                    }
                }
            }

            handleRequest(HttpMethod.Get, "/", { addHeader(HttpHeaders.IfModifiedSince, date.toLocalDateTime().minusDays(1).toHttpDateString()) }).let { result ->
                assertEquals(ApplicationCallResult.Handled, result.requestResult)
                assertEquals(HttpStatusCode.OK, result.response.status())
                assertEquals(date.toLocalDateTime().toHttpDateString(), result.response.headers[HttpHeaders.LastModified])
                assertEquals("ok", result.response.content)
            }
        }
    }

    @Test
    fun testRangeOnlyWithIfModifiedSince() {
        val date = Date()

        withTestApplication {
            application.routing {
                handle {
                    withIfRange(date) { rangeOrNull ->
                        fail("should never reach here")
                    }
                }
            }

            handleRequest(HttpMethod.Get, "/", { addHeader(HttpHeaders.Range, "bytes=0-1"); addHeader(HttpHeaders.IfModifiedSince, date.toLocalDateTime().toHttpDateString()) }).let { result ->
                assertEquals(ApplicationCallResult.Handled, result.requestResult)
                assertEquals(HttpStatusCode.NotModified, result.response.status())
                assertNull(result.response.content)
            }
        }
    }

    @Test
    fun testRangeOnlyWithIfModifiedSinceLt() {
        val date = Date()

        withTestApplication {
            application.routing {
                handle {
                    withIfRange(date) { rangeOrNull ->
                        assertNotNull(rangeOrNull)
                        response.sendText("ok")
                    }
                }
            }

            handleRequest(HttpMethod.Get, "/", { addHeader(HttpHeaders.Range, "bytes=0-1"); addHeader(HttpHeaders.IfModifiedSince, date.toLocalDateTime().minusDays(1).toHttpDateString()) }).let { result ->
                assertEquals(ApplicationCallResult.Handled, result.requestResult)
                assertEquals(HttpStatusCode.PartialContent, result.response.status())
                assertEquals(date.toLocalDateTime().toHttpDateString(), result.response.headers[HttpHeaders.LastModified])
                assertEquals("ok", result.response.content)
            }
        }
    }

    @Test
    fun testRangeAndIfRangeEq() {
        val date = Date()

        withTestApplication {
            application.routing {
                handle {
                    withIfRange(date) { rangeOrNull ->
                        assertNotNull(rangeOrNull)
                        response.sendText("ok")
                    }
                }
            }

            handleRequest(HttpMethod.Get, "/", { addHeader(HttpHeaders.Range, "bytes=0-1"); addHeader(HttpHeaders.IfRange, date.toLocalDateTime().toHttpDateString()) }).let { result ->
                assertEquals(ApplicationCallResult.Handled, result.requestResult)
                assertEquals(HttpStatusCode.PartialContent, result.response.status())
                assertEquals("ok", result.response.content)
            }
        }
    }

    @Test
    fun testRangeAndIfRangeLt() {
        val date = Date()

        withTestApplication {
            application.routing {
                handle {
                    withIfRange(date) { rangeOrNull ->
                        assertNull(rangeOrNull)
                        response.sendText("ok")
                    }
                }
            }

            handleRequest(HttpMethod.Get, "/", { addHeader(HttpHeaders.Range, "bytes=0-1"); addHeader(HttpHeaders.IfRange, date.toLocalDateTime().minusDays(1).toHttpDateString()) }).let { result ->
                assertEquals(ApplicationCallResult.Handled, result.requestResult)
                assertEquals(HttpStatusCode.OK, result.response.status())
                assertEquals("ok", result.response.content)
            }
        }
    }

    @Test
    fun testRangeAndIfRangeGt() {
        val date = Date()

        withTestApplication {
            application.routing {
                handle {
                    withIfRange(date) { rangeOrNull ->
                        assertNotNull(rangeOrNull)
                        response.sendText("ok")
                    }
                }
            }

            handleRequest(HttpMethod.Get, "/", { addHeader(HttpHeaders.Range, "bytes=0-1"); addHeader(HttpHeaders.IfRange, date.toLocalDateTime().plusDays(1).toHttpDateString()) }).let { result ->
                assertEquals(ApplicationCallResult.Handled, result.requestResult)
                assertEquals(HttpStatusCode.PartialContent, result.response.status())
                assertEquals("ok", result.response.content)
            }
        }
    }

    @Test
    fun testRangeAndIfRangeEtagOK() {
        withTestApplication {
            application.routing {
                handle {
                    withIfRange("etag1") { rangeOrNull ->
                        assertNotNull(rangeOrNull)
                        response.sendText("ok")
                    }
                }
            }

            handleRequest(HttpMethod.Get, "/", { addHeader(HttpHeaders.Range, "bytes=0-1"); addHeader(HttpHeaders.IfRange, "etag1") }).let { result ->
                assertEquals(ApplicationCallResult.Handled, result.requestResult)
                assertEquals(HttpStatusCode.PartialContent, result.response.status())
                assertEquals("ok", result.response.content)
            }
        }
    }

    @Test
    fun testRangeAndIfRangeEtagDiffer() {
        withTestApplication {
            application.routing {
                handle {
                    withIfRange("etag1") { rangeOrNull ->
                        assertNull(rangeOrNull)
                        response.sendText("ok")
                    }
                }
            }

            handleRequest(HttpMethod.Get, "/", { addHeader(HttpHeaders.Range, "bytes=0-1"); addHeader(HttpHeaders.IfRange, "etag2") }).let { result ->
                assertEquals(ApplicationCallResult.Handled, result.requestResult)
                assertEquals(HttpStatusCode.OK, result.response.status())
                assertEquals("ok", result.response.content)
            }
        }
    }

    @Test
    fun testRangeNoIfRangeEtag() {
        withTestApplication {
            application.routing {
                handle {
                    withIfRange("etag1") { rangeOrNull ->
                        assertNotNull(rangeOrNull)
                        response.sendText("ok")
                    }
                }
            }

            handleRequest(HttpMethod.Get, "/", { addHeader(HttpHeaders.Range, "bytes=0-1") }).let { result ->
                assertEquals(ApplicationCallResult.Handled, result.requestResult)
                assertEquals(HttpStatusCode.PartialContent, result.response.status())
                assertEquals("ok", result.response.content)
            }
        }
    }

    @Test
    fun testRangeNoIfRangeEtagIfNoneMatch() {
        withTestApplication {
            application.routing {
                handle {
                    withIfRange("etag1") { rangeOrNull ->
                        assertNotNull(rangeOrNull)
                        response.sendText("ok")
                    }
                }
            }

            handleRequest(HttpMethod.Get, "/", { addHeader(HttpHeaders.Range, "bytes=0-1"); addHeader(HttpHeaders.IfNoneMatch, "etag1") }).let { result ->
                assertEquals(ApplicationCallResult.Handled, result.requestResult)
                assertEquals(HttpStatusCode.NotModified, result.response.status())
                assertNull(result.response.content)
            }

            handleRequest(HttpMethod.Get, "/", { addHeader(HttpHeaders.Range, "bytes=0-1"); addHeader(HttpHeaders.IfNoneMatch, "etag2") }).let { result ->
                assertEquals(ApplicationCallResult.Handled, result.requestResult)
                assertEquals(HttpStatusCode.PartialContent, result.response.status())
                assertEquals("ok", result.response.content)
            }
        }
    }

    @Test
    fun testRangeNoIfRangeEtagIfMatch() {
        withTestApplication {
            application.routing {
                handle {
                    withIfRange("etag1") { rangeOrNull ->
                        assertNotNull(rangeOrNull)
                        response.sendText("ok")
                    }
                }
            }

            handleRequest(HttpMethod.Get, "/", { addHeader(HttpHeaders.Range, "bytes=0-1"); addHeader(HttpHeaders.IfMatch, "etag2") }).let { result ->
                assertEquals(ApplicationCallResult.Handled, result.requestResult)
                assertEquals(HttpStatusCode.PreconditionFailed, result.response.status())
                assertNull(result.response.content)
            }

            handleRequest(HttpMethod.Get, "/", { addHeader(HttpHeaders.Range, "bytes=0-1"); addHeader(HttpHeaders.IfMatch, "etag1") }).let { result ->
                assertEquals(ApplicationCallResult.Handled, result.requestResult)
                assertEquals(HttpStatusCode.PartialContent, result.response.status())
                assertEquals("ok", result.response.content)
            }
        }
    }

    @Test
    fun testNoRangeNoIfRangeEtag() {
        withTestApplication {
            application.routing {
                handle {
                    withIfRange("etag1") { rangeOrNull ->
                        assertNull(rangeOrNull)
                        response.sendText("ok")
                    }
                }
            }

            handleRequest(HttpMethod.Get, "/", {  }).let { result ->
                assertEquals(ApplicationCallResult.Handled, result.requestResult)
                assertEquals(HttpStatusCode.OK, result.response.status())
                assertEquals("ok", result.response.content)
            }
        }
    }
}

private fun Date.toLocalDateTime() = LocalDateTime.ofInstant(toInstant(), ZoneId.systemDefault())