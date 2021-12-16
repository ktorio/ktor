/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.tests.utils.tests

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.*
import java.util.concurrent.atomic.*
import kotlin.time.*
import kotlin.time.Duration.Companion.seconds

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
                call.response.cacheControl(CacheControl.MaxAge(2.seconds))
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
                @Suppress("DEPRECATION_ERROR")
                call.withETag("0") {
                    call.respondText(current.toString())
                }
            }

            get("/last-modified") {
                val current = counter.incrementAndGet()
                val response = TextContent("$current", ContentType.Text.Plain)
                response.versions += LastModifiedVersion(Instant.fromEpochSeconds(0))

                call.respond(response)
            }

            get("/vary") {
                val current = counter.incrementAndGet()
                val response = TextContent("$current", ContentType.Text.Plain).apply {
                    caching = CachingOptions(CacheControl.MaxAge(60.seconds))
                }
                response.versions += LastModifiedVersion(Instant.fromEpochSeconds(0))

                call.response.header(HttpHeaders.Vary, HttpHeaders.ContentLanguage)
                call.respond(response)
            }

            get("/vary-stale") {
                val current = counter.incrementAndGet()
                val response = TextContent("$current", ContentType.Text.Plain).apply {
                    caching = CachingOptions(CacheControl.MaxAge(Duration.ZERO))
                }
                response.versions += LastModifiedVersion(Instant.fromEpochSeconds(0))

                call.response.header(HttpHeaders.Vary, HttpHeaders.ContentLanguage)
                call.respond(response)
            }

            get("/public") {
                call.response.cacheControl(CacheControl.MaxAge(60.seconds))
                call.respondText("public")
            }
            get("/private") {
                call.response.cacheControl(CacheControl.MaxAge(60.seconds, visibility = CacheControl.Visibility.Private))
                call.response.cacheControl(CacheControl.NoCache(CacheControl.Visibility.Private))
                call.respondText("private")
            }
        }
    }
}
