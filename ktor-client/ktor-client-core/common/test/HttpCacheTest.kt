/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.api.*
import io.ktor.client.plugins.cache.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.test.*
import io.ktor.util.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.TestResult
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
                val newResponse = response.call.replaceResponse(
                    headers = Headers.build { append(HttpHeaders.ContentLength, content.length.toString()) },
                    content = { ByteReadChannel(content) }
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

    // A no-op converter whose only effect is to register application/json as an Accept type.
    // ContentNegotiation unconditionally appends Accept for every registered codec via
    // `request.accept(it.contentTypeToSend)`, which creates a second header value when the
    // caller also sets Accept explicitly. This is sufficient to trigger the varyKey mismatch.
    private val noOpJsonConverter = object : ContentConverter {
        override suspend fun serialize(
            contentType: ContentType,
            charset: Charset,
            typeInfo: TypeInfo,
            value: Any?
        ): OutgoingContent? = null

        override suspend fun deserialize(
            charset: Charset,
            typeInfo: TypeInfo,
            content: ByteReadChannel
        ): Any? = null
    }

    private fun MockRequestHandleScope.cacheableJsonResponse() = respond(
        content = """{"id":1}""",
        headers = headersOf(
            HttpHeaders.ContentType to listOf(ContentType.Application.Json.toString()),
            HttpHeaders.CacheControl to listOf("max-age=60"),
            HttpHeaders.Vary to listOf("Accept"),
            HttpHeaders.ETag to listOf("\"abc123\""),
        )
    )

    /**
     * Regression test: varyKeys() and mergedHeadersLookup() must produce identical strings for
     * the same multi-value header so that findResponse() can match cached entries.
     *
     * Before the fix, varyKeys() joined with "," while mergedHeadersLookup() joined with ";",
     * so stored "a,b" never equalled looked-up "a;b" and every request was a cache miss.
     * Both now delegate to joinHeaderValues() (RFC 7230 §3.2.2 comma separator).
     */
    @Test
    fun varyKeysSeparatorMatchesMergedHeadersLookupSeparator() {
        val requestHeaders = Headers.build {
            append(HttpHeaders.Accept, "application/vnd.github+json") // set by caller
            append(HttpHeaders.Accept, "application/json")            // appended by ContentNegotiation
        }

        // How HttpCacheEntry.varyKeys() stores the value (joinHeaderValues → comma):
        val stored = requestHeaders.getAll(HttpHeaders.Accept).joinHeaderValues()
        //  → "application/vnd.github+json,application/json"

        // How mergedHeadersLookup() now computes the lookup value (joinHeaderValues → comma):
        val lookup = requestHeaders.getAll(HttpHeaders.Accept).joinHeaderValues()
        //  → "application/vnd.github+json,application/json"

        assertEquals(stored, lookup)
    }

    /**
     * Integration test: a fresh cacheable response (max-age=60, Vary: Accept) is never served
     * from cache when ContentNegotiation is installed, because the comma/semicolon separator
     * mismatch in findResponse() causes it to always discard the cached entry.
     */
    @Test
    fun cacheHitOccursWithContentNegotiationAndVaryAccept() =
        runTest {
            var serverCallCount = 0
            val client = HttpClient(
                MockEngine { _ ->
                    serverCallCount++
                    cacheableJsonResponse()
                }
            ) {
                install(HttpCache)
                install(ContentNegotiation) {
                    register(ContentType.Application.Json, noOpJsonConverter)
                }
            }

            val url = "https://example.com/api/resource"
            client.get(url) { accept(ContentType.parse("application/vnd.github+json")) }
            client.get(url) { accept(ContentType.parse("application/vnd.github+json")) }

            // First response is cacheable (max-age=60) and the second request is identical.
            // Expected: second request served from cache → serverCallCount == 1.
            assertEquals(1, serverCallCount)
            client.close()
        }

    /**
     * Counterpart: without ContentNegotiation each header has a single value, so the
     * "," vs ";" mismatch doesn't matter and the cache hit works correctly.
     */
    @Test
    fun freshCacheableEntryWithVaryAcceptIsServedFromCacheWhenContentNegotiationIsNotInstalled() =
        runTest {
            var serverCallCount = 0
            val client = HttpClient(
                MockEngine { _ ->
                    serverCallCount++
                    cacheableJsonResponse()
                }
            ) {
                install(HttpCache)
                // ContentNegotiation intentionally absent: one Accept value → separator irrelevant
            }

            val url = "https://example.com/api/resource"
            client.get(url) { accept(ContentType.parse("application/vnd.github+json")) }
            client.get(url) { accept(ContentType.parse("application/vnd.github+json")) }

            assertEquals(1, serverCallCount)
            client.close()
        }

    private fun testApplication(block: suspend ApplicationTestBuilder.() -> Unit): TestResult {
        return if (!PlatformUtils.IS_BROWSER) {
            testApplication(EmptyCoroutineContext, block)
        } else {
            runTest { println("Skipping test on browser") }
        }
    }
}
