/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.client.plugins.cache.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.coroutines.*
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds

class HttpCacheTest {

    @Test
    fun `should not mix ETags when Authorization header is present`() = testApplication {
        application {
            routing {
                get("/me") {
                    val user = call.request.headers["Authorization"]!!
                    if (user == "user-a") {
                        // Simulate slower network for one of the requests
                        delay(100.milliseconds)
                    }
                    val etag = "etag-of-$user"
                    if (call.request.headers["If-None-Match"] == etag) {
                        call.respond(HttpStatusCode.NotModified)
                        return@get
                    }
                    call.response.header("Cache-Control", "no-cache")
                    call.response.header("ETag", etag)
                    call.respondText(user)
                }
            }
        }

        val client = createClient {
            install(HttpCache) {
                isShared = true
            }
        }

        assertEquals(
            client.get("/me") {
                headers["Authorization"] = "user-a"
            }.bodyAsText(),
            "user-a"
        )
        withContext(Dispatchers.Default) {
            listOf(
                launch {
                    val response = client.get("/me") {
                        headers["Authorization"] = "user-a"
                    }.bodyAsText()

                    assertEquals("user-a", response)
                },
                launch {
                    val response = client.get("/me") {
                        headers["Authorization"] = "user-b"
                    }.bodyAsText()

                    assertEquals("user-b", response)
                }
            ).joinAll()
        }
    }

    @Test
    fun `should mix ETags when Authorization header is present and client is not shared`() = testApplication {
        application {
            routing {
                get("/me") {
                    val user = call.request.headers["Authorization"]!!
                    if (user == "user-a") {
                        // Simulate slower network for one of the requests
                        delay(100.milliseconds)
                    }
                    val etag = "etag-of-$user"
                    if (call.request.headers["If-None-Match"] == etag) {
                        call.respond(HttpStatusCode.NotModified)
                        return@get
                    }
                    call.response.header("Cache-Control", "no-cache")
                    call.response.header("ETag", etag)
                    call.respondText(user)
                }
            }
        }

        val client = createClient {
            install(HttpCache)
        }

        assertEquals(
            client.get("/me") {
                headers["Authorization"] = "user-a"
            }.bodyAsText(),
            "user-a"
        )
        withContext(Dispatchers.Default) {
            listOf(
                launch {
                    val response = client.get("/me") {
                        headers["Authorization"] = "user-a"
                    }.bodyAsText()

                    assertEquals("user-b", response)
                },
                launch {
                    val response = client.get("/me") {
                        headers["Authorization"] = "user-b"
                    }.bodyAsText()

                    assertEquals("user-b", response)
                }
            ).joinAll()
        }
    }
}
