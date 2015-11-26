package org.jetbrains.ktor.tests.http

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.tests.*
import org.junit.*
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
            assertEquals(ApplicationRequestStatus.Handled, result.requestResult)
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
            assertEquals(ApplicationRequestStatus.Handled, result.requestResult)
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
            assertEquals(ApplicationRequestStatus.Handled, result.requestResult)
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
            assertEquals(ApplicationRequestStatus.Handled, result.requestResult)
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
            assertEquals(ApplicationRequestStatus.Handled, result.requestResult)
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
            assertEquals(ApplicationRequestStatus.Handled, result.requestResult)
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
            assertEquals(ApplicationRequestStatus.Handled, result.requestResult)
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
            assertEquals(ApplicationRequestStatus.Handled, result.requestResult)
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
            assertEquals(ApplicationRequestStatus.Handled, result.requestResult)
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
            assertEquals(ApplicationRequestStatus.Handled, result.requestResult)
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
            assertEquals(ApplicationRequestStatus.Handled, result.requestResult)
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
            assertEquals(ApplicationRequestStatus.Handled, result.requestResult)
            assertEquals(HttpStatusCode.PreconditionFailed, result.response.status())
        }
    }

}