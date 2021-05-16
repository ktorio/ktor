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
import kotlin.test.*

class ETagsTest {
    private inline fun withConditionalApplication(body: TestApplicationEngine.() -> Unit) = withTestApplication {
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
    fun testNoConditions() = testSuspend {
        withConditionalApplication {
            val result = handleRequest {}
            assertTrue(result.requestHandled)
            assertEquals(HttpStatusCode.OK, result.response.status())
            assertEquals("response", result.response.content)
            assertEquals("\"tag1\"", result.response.headers[HttpHeaders.ETag])
        }
    }

    @Test
    fun testIfMatchConditionAccepted() = testSuspend {
        withConditionalApplication {
            val result = handleRequest {
                addHeader(HttpHeaders.IfMatch, "tag1")
            }
            assertTrue(result.requestHandled)
            assertEquals(HttpStatusCode.OK, result.response.status())
            assertEquals("response", result.response.content)
            assertEquals("\"tag1\"", result.response.headers[HttpHeaders.ETag])
        }
    }

    @Test
    fun testIfMatchConditionFailed() = testSuspend {
        withConditionalApplication {
            val result = handleRequest {
                addHeader(HttpHeaders.IfMatch, "tag2")
            }
            assertTrue(result.requestHandled)
            assertEquals(HttpStatusCode.PreconditionFailed, result.response.status())
        }
    }

    @Test
    fun testIfNoneMatchConditionAccepted() = testSuspend {
        withConditionalApplication {
            val result = handleRequest {
                addHeader(HttpHeaders.IfNoneMatch, "tag1")
            }
            assertTrue(result.requestHandled)
            assertEquals(HttpStatusCode.NotModified, result.response.status())
            assertEquals("\"tag1\"", result.response.headers[HttpHeaders.ETag])
        }
    }

    @Test
    fun testIfNoneMatchWeakConditionAccepted() = testSuspend {
        withConditionalApplication {
            val result = handleRequest {
                addHeader(HttpHeaders.IfNoneMatch, "W/tag1")
            }
            assertTrue(result.requestHandled)
            assertEquals(HttpStatusCode.NotModified, result.response.status())
        }
    }

    @Test
    fun testIfNoneMatchConditionFailed() = testSuspend {
        withConditionalApplication {
            val result = handleRequest {
                addHeader(HttpHeaders.IfNoneMatch, "tag2")
            }
            assertTrue(result.requestHandled)
            assertEquals(HttpStatusCode.OK, result.response.status())
            assertEquals("response", result.response.content)
            assertEquals("\"tag1\"", result.response.headers[HttpHeaders.ETag])
        }
    }

    @Test
    fun testIfMatchStar() = testSuspend {
        withConditionalApplication {
            val result = handleRequest {
                addHeader(HttpHeaders.IfMatch, "*")
            }
            assertTrue(result.requestHandled)
            assertEquals(HttpStatusCode.OK, result.response.status())
            assertEquals("response", result.response.content)
            assertEquals("\"tag1\"", result.response.headers[HttpHeaders.ETag])
        }
    }

    @Test
    fun testIfNoneMatchStar() = testSuspend {
        withConditionalApplication {
            val result = handleRequest {
                addHeader(HttpHeaders.IfNoneMatch, "*")
            }
            assertTrue(result.requestHandled)
            assertEquals(HttpStatusCode.OK, result.response.status())
            // note: star for if-none-match is a special case
            // that should be handled separately
            // so we always pass it
        }
    }

    @Test
    fun testIfNoneMatchListConditionFailed() = testSuspend {
        withConditionalApplication {
            val result = handleRequest {
                addHeader(HttpHeaders.IfNoneMatch, "tag0,tag1,tag3")
            }
            assertTrue(result.requestHandled)
            assertEquals(HttpStatusCode.NotModified, result.response.status())
        }
    }

    @Test
    fun testIfNoneMatchListConditionSuccess() = testSuspend {
        withConditionalApplication {
            val result = handleRequest {
                addHeader(HttpHeaders.IfNoneMatch, "tag2")
            }
            assertTrue(result.requestHandled)
            assertEquals(HttpStatusCode.OK, result.response.status())
            assertEquals("response", result.response.content)
            assertEquals("\"tag1\"", result.response.headers[HttpHeaders.ETag])
        }
    }

    @Test
    fun testIfMatchListConditionAccepted() = testSuspend {
        withConditionalApplication {
            val result = handleRequest {
                addHeader(HttpHeaders.IfMatch, "tag0,tag1,tag3")
            }
            assertTrue(result.requestHandled)
            assertEquals(HttpStatusCode.OK, result.response.status())
            assertEquals("response", result.response.content)
            assertEquals("\"tag1\"", result.response.headers[HttpHeaders.ETag])
        }
    }

    @Test
    fun testIfMatchListConditionFailed() = testSuspend {
        withConditionalApplication {
            val result = handleRequest {
                addHeader(HttpHeaders.IfMatch, "tag0,tag2,tag3")
            }
            assertTrue(result.requestHandled)
            assertEquals(HttpStatusCode.PreconditionFailed, result.response.status())
        }
    }
}
