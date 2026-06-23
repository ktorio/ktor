/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests.plugins

import io.ktor.client.plugins.cache.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.test.base.*
import io.ktor.http.*
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds

class CacheLegacyStorageTest : ClientLoader() {

    @Test
    fun testNoStore() = clientTests {
        val cache = CacheTestFixtures.legacy()
        config { install(cache) }
        test { client -> testNoStoreScenario(cache, client, Url("$TEST_SERVER/cache/no-store")) }
    }

    @Test
    fun testNoCache() = clientTests {
        val cache = CacheTestFixtures.legacy()
        config { install(cache) }
        test { client -> testNoCacheScenario(cache, client, Url("$TEST_SERVER/cache/no-cache")) }
    }

    @Test
    fun testETagCache() = clientTests(except("Js")) {
        val cache = CacheTestFixtures.legacy()
        config { install(cache) }
        test { client -> testETagCacheScenario(cache, client, Url("$TEST_SERVER/cache/etag")) }
    }

    @Test
    fun testLastModified() = clientTests(except("Js")) {
        val cache = CacheTestFixtures.legacy()
        config { install(cache) }
        test { client -> testETagCacheScenario(cache, client, Url("$TEST_SERVER/cache/last-modified")) }
    }

    @Test
    fun testVary() = clientTests(except("Js")) {
        config { install(cache = CacheTestFixtures.legacy()) }
        test { client -> testVaryScenario(client, Url("$TEST_SERVER/cache/vary")) }
    }

    @Test
    fun testVaryStale() = clientTests(except("Js")) {
        config { install(cache = CacheTestFixtures.legacy()) }
        test { client -> testVaryScenario(client, Url("$TEST_SERVER/cache/vary-stale")) }
    }

    @Test
    fun testNoVaryIn304() = clientTests(except("Js")) {
        config { install(cache = CacheTestFixtures.legacy()) }
        test { client -> testNoVaryIn304Scenario(client, Url("$TEST_SERVER/cache/vary-stale")) }
    }

    @Test
    fun testMaxAge() = clientTests(except("native:CIO")) {
        val cache = CacheTestFixtures.legacy()
        config { install(cache) }
        test { client -> testMaxAgeScenario(cache, client, Url("$TEST_SERVER/cache/max-age")) }
    }

    @Test
    fun testOnlyIfCached() = clientTests {
        config { install(cache = CacheTestFixtures.legacy()) }
        test { client -> testOnlyIfCachedScenario(client, Url("$TEST_SERVER/cache/etag?max-age=10")) }
    }

    @Test
    fun testMaxStale() = clientTests(retries = 3) {
        val cache = CacheTestFixtures.legacy()
        config { install(cache) }
        test { client -> testMaxStaleScenario(cache, client, Url("$TEST_SERVER/cache/max-age")) }
    }

    @Test
    fun testNoStoreRequest() = clientTests(except("Js")) {
        val cache = CacheTestFixtures.legacy()
        config { install(cache) }
        test { client -> testNoStoreRequestScenario(cache, client, Url("$TEST_SERVER/cache/etag")) }
    }

    @Test
    fun testNoCacheRequest() = clientTests(except("Js")) {
        val cache = CacheTestFixtures.legacy()
        config { install(cache) }
        test { client -> testNoCacheRequestScenario(cache, client, Url("$TEST_SERVER/cache/etag?max-age=30")) }
    }

    @Test
    fun testRequestWithMaxAge0() = clientTests(except("Js")) {
        val cache = CacheTestFixtures.legacy()
        config { install(cache) }
        test { client -> testRequestWithMaxAge0Scenario(cache, client, Url("$TEST_SERVER/cache/etag?max-age=30")) }
    }

    @Test
    fun testExpires() = clientTests {
        val cache = CacheTestFixtures.legacy()
        config { install(cache) }
        test { client ->
            testExpiresScenario(
                cache,
                client,
                Url("$TEST_SERVER/cache/expires"),
                expiresOffset = 4000.milliseconds,
                expireDelay = 5000.milliseconds,
            )
        }
    }

    @Test
    fun testPublicAndPrivateCache() = clientTests(except("native:*")) {
        val cache = CacheTestFixtures.legacy()
        config { install(cache) }
        test { client -> testPublicAndPrivateCacheScenario(cache, client) }
    }

    @Test
    fun testWithLogging() = clientTests {
        config {
            install(Logging) {
                level = LogLevel.ALL
                logger = Logger.EMPTY
            }
            install(HttpCache)
        }
        test { client -> testWithLoggingScenario(client) }
    }

    @Test
    fun test304WithoutVaryMatchesByEtag() = mockCacheTest(
        url = Url("https://example.com/x"),
        fixtures = CacheTestFixtures.legacy(),
    ) {
        revalidateHandler(
            initialHeaders = defaultRevalidateInitialHeaders(
                extra = headersOf(HttpHeaders.Vary to listOf("Origin")),
            ),
            notModifiedHeaders = headersOf(HttpHeaders.ETag, DEFAULT_ETAG),
        )
        testBody = { client -> test304WithoutVaryMatchesByEtagScenario(fixtures, client, url) }
    }
}
