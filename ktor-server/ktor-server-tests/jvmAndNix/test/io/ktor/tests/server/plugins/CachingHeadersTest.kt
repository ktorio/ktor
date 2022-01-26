/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.tests.server.plugins

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cachingheaders.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.*

@Suppress("DEPRECATION")
class CachingHeadersTest {
    @Test
    fun testNoPluginInstalled(): Unit = test(
        configure = {},
        test = { call ->
            assertEquals(null, call.response.headers[HttpHeaders.CacheControl])
        }
    )

    @Test
    fun testByPass(): Unit = test(
        configure = {
            install(CachingHeaders)
        },
        test = { call ->
            assertEquals("no-cache", call.response.headers[HttpHeaders.CacheControl])
        }
    )

    @Test
    fun addNoStore(): Unit = test(
        configure = {
            install(CachingHeaders) {
                options { CachingOptions(CacheControl.NoStore(CacheControl.Visibility.Private)) }
            }
        },
        test = { call ->
            assertEquals("no-cache, private, no-store", call.response.headers[HttpHeaders.CacheControl])
        }
    )

    @Test
    fun testAddMaxAgeAndNoStore(): Unit = test(
        configure = {
            install(CachingHeaders) {
                options { CachingOptions(CacheControl.NoStore(CacheControl.Visibility.Private)) }
                options { CachingOptions(CacheControl.MaxAge(15)) }
            }
        },
        test = { call ->
            assertEquals(
                "no-cache, no-store, max-age=15, private",
                call.response.headers[HttpHeaders.CacheControl]
            )
        }
    )

    @Test
    fun testSubrouteInstall() = withTestApplication {
        application.routing {
            route("/1") {
                install(CachingHeaders) {
                    options { CachingOptions(CacheControl.NoStore(CacheControl.Visibility.Private)) }
                    options { CachingOptions(CacheControl.MaxAge(15)) }
                }
                get {
                    call.respondText("test") {
                        caching = CachingOptions(CacheControl.NoCache(null))
                    }
                }
            }
            get("/2") {
                call.respondText("test") {
                    caching = CachingOptions(CacheControl.NoCache(null))
                }
            }
        }

        handleRequest(HttpMethod.Get, "/1").let { call ->
            assertEquals(
                "no-cache, no-store, max-age=15, private",
                call.response.headers[HttpHeaders.CacheControl]
            )
        }

        handleRequest(HttpMethod.Get, "/2").let { call ->
            assertNull(call.response.headers[HttpHeaders.CacheControl])
        }
    }

    private fun test(
        configure: Application.() -> Unit,
        test: (ApplicationCall) -> Unit
    ): Unit = withTestApplication {
        configure(application)

        application.routing {
            get("/") {
                call.respondText("test") {
                    caching = CachingOptions(CacheControl.NoCache(null))
                }
            }
        }

        handleRequest(HttpMethod.Get, "/").let { call ->
            assertTrue(call.response.status()!!.isSuccess())
            assertEquals("test", call.response.content?.trim())
            test(call)
        }
    }
}
