package io.ktor.tests.ratelimits

import io.ktor.application.call
import io.ktor.application.install
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.ratelimits.RateLimits
import io.ktor.ratelimits.rateLimit
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.route
import io.ktor.routing.routing
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RateLimitsTest {
    @Test
    fun testRateLimiting() = withTestApplication {
        with(application) {
            install(RateLimits) {
                xRateLimitHeaders {
                    limit()
                    remaining()
                    reset()
                }
            }

            routing {
                rateLimit("/hello", limit = 3, seconds = 10) {
                    get {
                        call.response.status(HttpStatusCode.OK)
                        call.respond("Hello, World!")
                    }
                }
            }
        }

        repeat(3) {
            val call = handleRequest(HttpMethod.Get, "/hello")
            assertEquals(HttpStatusCode.OK, call.response.status())
            assertEquals(3 - (it + 1), call.response.headers["X-RateLimit-Remaining"]?.toInt())
            assertEquals(3, call.response.headers["X-RateLimit-Limit"]?.toInt())
        }

        val call = handleRequest(HttpMethod.Get, "/hello")
        assertEquals(HttpStatusCode.TooManyRequests, call.response.status())
        assertEquals(0, call.response.headers["X-RateLimit-Remaining"]?.toInt())
    }


    @Test // Note that Unit is explicitly specified otherwise JUnit fails the test
    fun testFailOnNestedRateLimitRoutes(): Unit = withTestApplication {
        with(application) {
            install(RateLimits)
            routing {
                assertFailsWith<IllegalArgumentException> {
                    rateLimit("/foo", 5, 5) {
                        rateLimit("/bar", 5, 10) {}
                    }
                }
                assertFailsWith<IllegalArgumentException> {
                    rateLimit("/foo", 5, 10) {
                        route("/bar") {
                            rateLimit("/baz", 10, 10) {}
                        }
                    }
                }
            }
        }
    }

    @Test
    fun testApplicationCallRateLimitExtension() = withTestApplication {
        with(application) {
            install(RateLimits)
            routing {
                rateLimit("/baz", 5, 5) {
                    get {
                        call.rateLimit // Check if it succeeds
                        call.response.status(HttpStatusCode.OK)
                        call.respond("Success")
                    }
                }
                get("/biz") {
                    assertFailsWith<IllegalStateException> { call.rateLimit }
                    call.response.status(HttpStatusCode.OK)
                    call.respond("Success")
                }
            }
        }

        val baz = handleRequest(HttpMethod.Get, "/baz")
        val biz = handleRequest(HttpMethod.Get, "/biz")
        assertEquals(HttpStatusCode.OK, baz.response.status())
        assertEquals(HttpStatusCode.OK, biz.response.status())
    }
}
