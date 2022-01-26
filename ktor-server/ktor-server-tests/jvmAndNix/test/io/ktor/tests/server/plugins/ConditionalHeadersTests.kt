// ktlint-disable filename
/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.plugins

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.plugins.conditionalheaders.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.*

@Suppress("DEPRECATION")
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
