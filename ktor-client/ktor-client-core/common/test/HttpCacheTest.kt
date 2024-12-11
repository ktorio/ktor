/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.client.plugins.api.*
import io.ktor.client.plugins.cache.*
import io.ktor.client.plugins.observer.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds

class HttpCacheTest {

    @Test
    fun shouldNotMixETagsWhenAuthorizationHeaderIsPresent() = testApplication {
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
    fun shouldMixETagsWhenAuthorizationHeaderIsPresentAndClientIsNotShared() = testApplication {
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

    @Test
    fun testCacheOfModifiedResponses() = testApplication {
        routing {
            get("/hello") {
                call.response.header(HttpHeaders.CacheControl, "max-age=10")
                call.respondText("Hello")
            }
        }

        fun createBodyTransformingPlugin(name: String, transform: (String) -> String) = createClientPlugin(name) {
            client.receivePipeline.intercept(HttpReceivePipeline.State) { response ->
                val content = transform(response.bodyAsText())
                val newResponse = response.call.wrap(
                    content = ByteReadChannel(content),
                    headers = Headers.build { append(HttpHeaders.ContentLength, content.length.toString()) },
                ).response
                proceedWith(newResponse)
            }
        }

        val client = createClient {
            install(createBodyTransformingPlugin("BeforeCache") { "before($it)" })
            install(HttpCache)
            install(createBodyTransformingPlugin("AfterCache") { "after($it)" })
        }

        val firstResponse = client.get("/hello").bodyAsText()
        val secondResponse = client.get("/hello").bodyAsText()

        assertEquals("after(before(Hello))", firstResponse)
        assertEquals(firstResponse, secondResponse)
    }

    private fun testApplication(block: suspend ApplicationTestBuilder.() -> Unit): TestResult {
        return if (!PlatformUtils.IS_BROWSER) {
            testApplication(EmptyCoroutineContext, block)
        } else {
            runTest { println("Skipping test on browser") }
        }
    }
}
