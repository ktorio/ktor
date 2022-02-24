/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.tests.utils.tests

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.util.date.*
import java.util.concurrent.atomic.*

internal val counter = AtomicInteger(0)

internal fun Application.cacheTestServer() {
    routing {
        route("/cache") {
            install(CachingHeaders)
            install(ConditionalHeaders)

            get("/no-cache") {
                val value = counter.incrementAndGet()
                call.response.cacheControl(CacheControl.NoCache(null))
                call.respondText("$value")
            }
            get("/no-store") {
                val value = counter.incrementAndGet()
                call.response.cacheControl(CacheControl.NoStore(null))
                call.respondText("$value")
            }
            get("/max-age") {
                val value = counter.incrementAndGet()
                call.response.cacheControl(CacheControl.MaxAge(2))
                call.respondText("$value")
            }
            get("/expires") {
                // note: we don't add X-Expires to Vary intentionally
                val value = counter.incrementAndGet()
                call.response.header(HttpHeaders.Expires, call.request.headers["X-Expires"] ?: "?")
                call.respondText("$value")
            }

            /**
             * Return same etag for first 2 responses.
             */
            get("/etag") {
                val current = counter.incrementAndGet()
                @Suppress("DEPRECATION")
                call.withETag("0") {
                    call.respondText(current.toString())
                }
            }

            get("/last-modified") {
                val current = counter.incrementAndGet()
                val response = TextContent("$current", ContentType.Text.Plain)
                response.versions += LastModifiedVersion(GMTDate.START)

                call.respond(response)
            }

            get("/vary") {
                val current = counter.incrementAndGet()
                val response = TextContent("$current", ContentType.Text.Plain).apply {
                    caching = CachingOptions(CacheControl.MaxAge(60))
                }
                response.versions += LastModifiedVersion(GMTDate.START)

                call.response.header(HttpHeaders.Vary, HttpHeaders.ContentLanguage)
                call.respond(response)
            }

            get("/vary-stale") {
                val current = counter.incrementAndGet()
                val response = TextContent("$current", ContentType.Text.Plain).apply {
                    caching = CachingOptions(CacheControl.MaxAge(0))
                }
                response.versions += LastModifiedVersion(GMTDate.START)

                call.response.header(HttpHeaders.Vary, HttpHeaders.ContentLanguage)
                call.respond(response)
            }

            get("/public") {
                call.response.cacheControl(CacheControl.MaxAge(60))
                call.respondText("public")
            }
            get("/private") {
                call.response.cacheControl(CacheControl.MaxAge(60, visibility = CacheControl.Visibility.Private))
                call.response.cacheControl(CacheControl.NoCache(CacheControl.Visibility.Private))
                call.respondText("private")
            }
        }
    }
}
