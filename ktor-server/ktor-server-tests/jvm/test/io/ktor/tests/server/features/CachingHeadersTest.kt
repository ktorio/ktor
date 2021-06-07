/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.tests.server.features

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import kotlin.test.*

class CachingHeadersTest {
    @Test
    fun testNoFeatureInstalled(): Unit = test(
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
