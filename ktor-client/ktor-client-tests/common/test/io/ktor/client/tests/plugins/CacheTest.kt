/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests.plugins

import io.ktor.client.call.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.cache.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.test.base.*
import io.ktor.client.utils.*
import io.ktor.http.*
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds

class CacheTest : ClientLoader() {

    @Test
    fun testNoStore() = clientTests {
        val cache = CacheTestFixtures.modern()
        config { install(cache) }
        test { client -> testNoStoreScenario(cache, client, Url("$TEST_SERVER/cache/no-store")) }
    }

    @Test
    fun testNoCache() = clientTests {
        val cache = CacheTestFixtures.modern()
        config { install(cache) }
        test { client -> testNoCacheScenario(cache, client, Url("$TEST_SERVER/cache/no-cache")) }
    }

    @Test
    fun testETagCache() = clientTests(except("Js")) {
        val cache = CacheTestFixtures.modern()
        config { install(cache) }
        test { client -> testETagCacheScenario(cache, client, Url("$TEST_SERVER/cache/etag")) }
    }

    @Test
    fun testReuseCacheStorage() = clientTests(except("Js")) {
        val cache = CacheTestFixtures.modern()
        config { install(cache) }

        test { client ->
            val client1 = client.config { }
            val client2 = client.config { }
            val url = Url("$TEST_SERVER/cache/etag-304")

            val first = client1.get(url)
            val second = client2.get(url)

            assertEquals(HttpStatusCode.OK, first.status)
            assertEquals(HttpStatusCode.OK, second.status)
            assertEquals(first.body<String>(), second.body<String>())
        }
    }

    @Test
    fun testLastModified() = clientTests(except("Js")) {
        val cache = CacheTestFixtures.modern()
        config { install(cache) }
        test { client -> testETagCacheScenario(cache, client, Url("$TEST_SERVER/cache/last-modified")) }
    }

    @Test
    fun testVary() = clientTests(except("Js")) {
        val cache = CacheTestFixtures.modern()
        config { install(cache) }
        test { client -> testVaryScenario(client, Url("$TEST_SERVER/cache/vary")) }
    }

    @Test
    fun testVaryStale() = clientTests(except("Js")) {
        val cache = CacheTestFixtures.modern()
        config { install(cache) }
        test { client -> testVaryScenario(client, Url("$TEST_SERVER/cache/vary-stale")) }
    }

    @Test
    fun testNoVaryIn304() = clientTests(except("Js")) {
        val cache = CacheTestFixtures.modern()
        config { install(cache) }
        test { client -> testNoVaryIn304Scenario(client, Url("$TEST_SERVER/cache/vary-stale")) }
    }

    @Test
    fun testSingleVaryAndMultipleVaryWithSameValues() = testWithEngine(MockEngine) {
        val fakeResponse = FakeResponse()
        val cache = CacheTestFixtures.modern()
        config {
            install(cache)
            engine {
                addHandler {
                    val headers = buildHeaders {
                        fakeResponse.headers.forEach(::appendAll)
                        append(HttpHeaders.ETag, "W/\"ETAG\"")
                    }
                    respond(fakeResponse.content, fakeResponse.status, headers)
                }
            }
        }

        test { client ->
            val url = Url("$TEST_SERVER/cache/vary-multiple")
            val vary = listOf(HttpHeaders.Origin, HttpHeaders.Accept)

            fakeResponse.content = "Cache"
            fakeResponse.status = HttpStatusCode.OK
            fakeResponse.headers = buildHeaders {
                vary.forEach { append(HttpHeaders.Vary, it) }
            }
            val okBody = client.get(url).body<String>()
            assertEquals("Cache", okBody)

            fakeResponse.content = ""
            fakeResponse.status = HttpStatusCode.NotModified
            fakeResponse.headers = headersOf(HttpHeaders.Vary, vary.joinToString(","))
            val notModifiedBody = client.get(url).body<String>()
            assertEquals(okBody, notModifiedBody)
        }
    }

    @Test
    fun testMaxAge() = clientTests {
        val cache = CacheTestFixtures.modern()
        config { install(cache) }
        test { client -> testMaxAgeScenario(cache, client, Url("$TEST_SERVER/cache/max-age")) }
    }

    @Test
    fun testSMaxAgeWhenIsSharedTrue() = testWithEngine(MockEngine) {
        val fakeResponse = FakeResponse()
        val cache = CacheTestFixtures.modern()
        config {
            install(cache) { isShared = true }
            engine {
                addHandler {
                    respond(
                        content = fakeResponse.content,
                        status = fakeResponse.status,
                        headers = buildHeaders {
                            append(HttpHeaders.ETag, "W/\"ETAG\"")
                            append(HttpHeaders.CacheControl, "max-age=0,s-maxage=1")
                        },
                    )
                }
            }
        }

        test { client ->
            val url = Url("$TEST_SERVER/cache/vary-s-maxage")

            fakeResponse.content = "First"
            fakeResponse.status = HttpStatusCode.OK
            val firstBody = client.get(url).body<String>()
            assertEquals("First", firstBody)

            fakeResponse.content = "Second"
            fakeResponse.status = HttpStatusCode.OK
            val secondBody = client.get(url).body<String>()
            assertEquals(firstBody, secondBody)
        }
    }

    @Test
    fun testOnlyIfCached() = clientTests {
        val cache = CacheTestFixtures.modern()
        config { install(cache) }
        test { client -> testOnlyIfCachedScenario(client, Url("$TEST_SERVER/cache/etag?max-age=10")) }
    }

    @Test
    fun testMaxStale() = clientTests {
        val cache = CacheTestFixtures.modern()
        config { install(cache) }
        test { client -> testMaxStaleScenario(cache, client, Url("$TEST_SERVER/cache/max-age")) }
    }

    @Test
    fun testNoStoreRequest() = clientTests(except("Js")) {
        val cache = CacheTestFixtures.modern()
        config { install(cache) }
        test { client -> testNoStoreRequestScenario(cache, client, Url("$TEST_SERVER/cache/etag")) }
    }

    @Test
    fun testNoCacheRequest() = clientTests(except("Js")) {
        val cache = CacheTestFixtures.modern()
        config { install(cache) }
        test { client -> testNoCacheRequestScenario(cache, client, Url("$TEST_SERVER/cache/etag?max-age=30")) }
    }

    @Test
    fun testRequestWithMaxAge0() = clientTests(except("Js")) {
        val cache = CacheTestFixtures.modern()
        config { install(cache) }
        test { client -> testRequestWithMaxAge0Scenario(cache, client, Url("$TEST_SERVER/cache/etag?max-age=30")) }
    }

    @Test
    fun testExpires() = clientTests {
        val cache = CacheTestFixtures.modern()
        config { install(cache) }
        test { client ->
            testExpiresScenario(
                cache,
                client,
                Url("$TEST_SERVER/cache/expires"),
                expiresOffset = 2000.milliseconds,
                expireDelay = 2500.milliseconds,
            )
        }
    }

    @Test
    fun testPublicAndPrivateCache() = clientTests {
        val cache = CacheTestFixtures.modern()
        config { install(cache) }
        test { client -> testPublicAndPrivateCacheScenario(cache, client) }
    }

    @Test
    fun testWithLogging() = clientTests {
        val cache = CacheTestFixtures.modern()
        config {
            install(Logging) {
                level = LogLevel.ALL
                logger = Logger.EMPTY
            }
            install(cache)
        }
        test { client -> testWithLoggingScenario(client) }
    }

    @Test
    fun testCachesPrivateByDefault() = clientTests {
        val cache = CacheTestFixtures.modern()
        config { install(cache) }
        test { client ->
            val privateUrl = Url("$TEST_SERVER/cache/private")

            val firstPrivate = client.get(privateUrl).body<String>()
            assertEquals("private", firstPrivate)
            assertEquals(1, cache.privateEntries(privateUrl).size)
        }
    }

    @Test
    fun testDoesntCachesPrivateWhenShared() = clientTests {
        val cache = CacheTestFixtures.modern()
        config { install(cache) { isShared = true } }
        test { client ->
            val privateUrl = Url("$TEST_SERVER/cache/private")

            val firstPrivate = client.get(privateUrl).body<String>()
            assertEquals("private", firstPrivate)
            assertEquals(0, cache.privateEntries(privateUrl).size)
        }
    }

    @Test
    fun testMaxAgeMoreThanMaxValue() = clientTests {
        config {
            install(HttpCache)
        }
        test { client ->
            client.get("$TEST_SERVER/cache/set-max-age").apply {
                assertEquals(HttpStatusCode.OK, status)
            }
        }
    }

    @Test
    fun testInvalidMaxAge() = clientTests {
        config {
            install(HttpCache)
        }

        test { client ->
            val url = Url("$TEST_SERVER/cache/invalid-max-age")

            client.get(url).apply {
                assertEquals(HttpStatusCode.OK, status)
            }
        }
    }

    @Test
    fun testDifferentVaryHeaders() = clientTests {
        val cache = CacheTestFixtures.modern()
        config { install(cache) }

        test { client ->
            val url = "$TEST_SERVER/cache/different-vary"
            val first = client.get(url) {
                header("200", "true")
            }
            val second = client.get(url)

            assertEquals(first.bodyAsText(), second.bodyAsText())
            assertEquals(1, cache.publicEntries(Url(url)).size)
        }
    }

    @Test
    fun test304WithNewlyAddedVaryHeader() = mockCacheTest(url = Url("https://example.com/x")) {
        val etags = listOf(DEFAULT_ETAG)
        revalidateHandler(
            initialHeaders = headersOf(
                HttpHeaders.ETag to etags,
                HttpHeaders.CacheControl to listOf(REVALIDATE_CACHE_CONTROL),
            ),
            notModifiedHeaders = headersOf(
                HttpHeaders.ETag to etags,
                HttpHeaders.Vary to listOf("Origin"),
            ),
        )

        testBody = { client ->
            val first = client.get(url).bodyAsText()
            assertEquals("body", first)

            val second = client.get(url).bodyAsText()
            assertEquals("body", second)

            val cached = fixtures.publicEntries(url)
            assertEquals(1, cached.size)
            assertEquals(mapOf("origin" to ""), cached.single().varyKeys)
            assertEquals("Origin", cached.single().headers[HttpHeaders.Vary])
        }
    }

    @Test
    fun test304HopByHopHeadersAreNotMerged() = mockCacheTest(url = Url("https://example.com/hop-by-hop")) {
        revalidateHandler(
            initialBody = "data",
            initialHeaders = defaultRevalidateInitialHeaders(
                extra = headersOf("X-Custom" to listOf("original")),
            ),
            notModifiedHeaders = hopByHop304Headers(),
        )
        testBody = { client ->
            val first = client.get(url)
            assertEquals("data", first.bodyAsText())
            assertEquals(REVALIDATE_CACHE_CONTROL, first.headers[HttpHeaders.CacheControl])
            assertEquals("original", first.headers["X-Custom"])

            val second = client.get(url)
            assertEquals("data", second.bodyAsText())
            assertEquals("max-age=120", second.headers[HttpHeaders.CacheControl])
            assertEquals("original", second.headers["X-Custom"])
            assertHopByHopAbsent(second.headers)

            val cached = fixtures.publicEntries(url)
            assertEquals(1, cached.size)
            assertEquals("max-age=120", cached.single().headers[HttpHeaders.CacheControl])
            assertNull(cached.single().headers[HttpHeaders.Connection])
            assertNull(cached.single().headers["Foo"])
        }
    }

    @Test
    fun testHopByHopHeadersAreNotStoredOnInitialCache() = mockCacheTest(
        url = Url("https://example.com/store-hop-by-hop")
    ) {
        handler = {
            respond(
                "data",
                HttpStatusCode.OK,
                hopByHopOkHeaders(extra = headersOf("X-Custom" to listOf("original")), includeProxyAuth = true),
            )
        }
        testBody = { client ->
            val first = client.get(url)
            assertEquals("data", first.bodyAsText())
            assertEquals("original", first.headers["X-Custom"])
            assertEquals("4", first.headers[HttpHeaders.ContentLength])
            assertHopByHopAbsent(first.headers, includeProxyAuth = true)

            val cached = fixtures.publicEntries(url)
            assertEquals(1, cached.size)
            assertEquals("original", cached.single().headers["X-Custom"])
            assertEquals("4", cached.single().headers[HttpHeaders.ContentLength])
            assertNull(cached.single().headers[HttpHeaders.Connection])
            assertNull(cached.single().headers["Foo"])

            val second = client.get(url)
            assertEquals("data", second.bodyAsText())
            assertNull(second.headers[HttpHeaders.Connection])
            assertEquals("4", second.headers[HttpHeaders.ContentLength])
        }
    }

    @Test
    fun test304WithoutVaryMatchesByEtag() = mockCacheTest(url = Url("https://example.com/x")) {
        revalidateHandler(
            initialHeaders = defaultRevalidateInitialHeaders(
                extra = headersOf(HttpHeaders.Vary to listOf("Origin")),
            ),
            notModifiedHeaders = headersOf(HttpHeaders.ETag, DEFAULT_ETAG),
        )
        testBody = { client -> test304WithoutVaryMatchesByEtagScenario(fixtures, client, url) }
    }

    @Test
    fun test304WithoutValidatorMatchesSingleStoredResponse() = mockCacheTest(url = Url("https://example.com/x")) {
        revalidateHandler(
            match = RevalidationMatch.LastModified,
            initialHeaders = defaultRevalidateInitialHeaders(match = RevalidationMatch.LastModified),
            notModifiedHeaders = headersOf(HttpHeaders.CacheControl, "max-age=120"),
        )
        testBody = { client ->
            val first = client.get(url)
            assertEquals("body", first.bodyAsText())
            assertEquals(REVALIDATE_CACHE_CONTROL, first.headers[HttpHeaders.CacheControl])

            val second = client.get(url)
            assertEquals("body", second.bodyAsText())
            assertEquals("max-age=120", second.headers[HttpHeaders.CacheControl])

            val cached = fixtures.publicEntries(url)
            assertEquals(1, cached.size)
            assertEquals("max-age=120", cached.single().headers[HttpHeaders.CacheControl])
        }
    }

    @Test
    fun test304WithoutValidatorIsNotMatchedWhenMultipleStoredResponses() = mockCacheTest(
        url = Url("https://example.com/x")
    ) {
        handler = { request ->
            if (HttpHeaders.IfModifiedSince !in request.headers) {
                val variant = request.headers[HttpHeaders.AcceptLanguage] ?: "default"
                val headers = headersOf(
                    HttpHeaders.LastModified to listOf(DEFAULT_LAST_MODIFIED),
                    HttpHeaders.Vary to listOf(HttpHeaders.AcceptLanguage),
                    HttpHeaders.CacheControl to listOf(REVALIDATE_CACHE_CONTROL),
                )
                respond(variant, HttpStatusCode.OK, headers)
            } else {
                respond("", HttpStatusCode.NotModified)
            }
        }
        testBody = { client ->
            val en = client.get(url) { header(HttpHeaders.AcceptLanguage, "en") }.bodyAsText()
            val es = client.get(url) { header(HttpHeaders.AcceptLanguage, "es") }.bodyAsText()
            assertEquals("en", en)
            assertEquals("es", es)
            assertEquals(2, fixtures.publicEntries(url).size)

            val enAgain = client.get(url) { header(HttpHeaders.AcceptLanguage, "en") }.bodyAsText()
            assertEquals("en", enAgain)

            val esAgain = client.get(url) { header(HttpHeaders.AcceptLanguage, "es") }.bodyAsText()
            assertEquals("es", esAgain)
        }
    }

    @Test
    fun testVaryHeader() = clientTests {
        val cache = CacheTestFixtures.modern()
        config { install(cache) }

        test { client ->
            val url = Url("$TEST_SERVER/cache/vary-header")

            client.get(url) {
                header("Accept", "application/json")
            }
            client.get(url) {
                header("Accept", "application/json")
                header("Accept", "application/cbor")
            }

            assertEquals(2, cache.publicEntries(url).size)
        }
    }

    @Test
    fun testCaseSensitiveInVary() = clientTests {
        config { install(cache = CacheTestFixtures.modern()) }

        test { client ->
            val url = Url("$TEST_SERVER/cache/vary-header-case-sensitive")

            var count = 1
            val response1 = client.get(url) {
                header(HttpHeaders.AcceptLanguage, "en-US")
                header("Count", count++)
            }.bodyAsText()

            val response2 = client.get(url) {
                header(HttpHeaders.AcceptLanguage, "en-US")
                header("Count", count++)
            }.bodyAsText()

            val response3 = client.get(url) {
                headers {
                    append(HttpHeaders.CacheControl, "only-if-cached, max-stale=10000")
                    append(HttpHeaders.AcceptLanguage, "en-US")
                    header("Count", count++)
                }
            }.bodyAsText()

            assertEquals("1", response1)
            assertEquals("2", response2)
            assertEquals("2", response3)
        }
    }
}
