/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("DEPRECATION", "DEPRECATION_ERROR")

package io.ktor.client.tests.plugins

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.cache.*
import io.ktor.client.plugins.cache.storage.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.test.base.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.util.*
import io.ktor.util.date.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlin.test.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import io.ktor.client.plugins.cache.HttpCache.Config as HttpCacheConfig

internal const val REVALIDATE_CACHE_CONTROL = "max-age=0, must-revalidate"
internal const val DEFAULT_ETAG = "\"v1\""
internal const val DEFAULT_LAST_MODIFIED = "Mon, 01 Jan 2024 00:00:00 GMT"

enum class CacheStorageKind {
    Modern,
    Legacy,
}

enum class RevalidationMatch {
    ETag,
    LastModified,
}

data class CacheEntryProbe(
    val headers: Headers,
    val varyKeys: Map<String, String>,
    val raw: Any,
)

class CacheEntriesProbe(
    private val entries: List<CacheEntryProbe>,
) {
    val size: Int get() = entries.size

    fun isEmpty(): Boolean = entries.isEmpty()

    fun single(): CacheEntryProbe = entries.single()

    fun first(): CacheEntryProbe = entries.first()
}

class CacheTestFixtures private constructor(
    val kind: CacheStorageKind,
    internal val publicModern: CacheStorage?,
    internal val privateModern: CacheStorage?,
    internal val publicLegacy: HttpCacheStorage?,
    internal val privateLegacy: HttpCacheStorage?,
) {
    companion object {
        fun modern(): CacheTestFixtures = CacheTestFixtures(
            kind = CacheStorageKind.Modern,
            publicModern = CacheStorage.Unlimited(),
            privateModern = CacheStorage.Unlimited(),
            publicLegacy = null,
            privateLegacy = null,
        )

        fun legacy(): CacheTestFixtures = CacheTestFixtures(
            kind = CacheStorageKind.Legacy,
            publicModern = null,
            privateModern = null,
            publicLegacy = HttpCacheStorage.Unlimited(),
            privateLegacy = HttpCacheStorage.Unlimited(),
        )
    }

    suspend fun publicEntries(url: Url): CacheEntriesProbe = when (kind) {
        CacheStorageKind.Modern -> CacheEntriesProbe(
            publicModern!!.findAll(url).map { it.toProbe() },
        )

        CacheStorageKind.Legacy -> CacheEntriesProbe(
            publicLegacy!!.findByUrl(url).map { it.toProbe() },
        )
    }

    suspend fun privateEntries(url: Url): CacheEntriesProbe = when (kind) {
        CacheStorageKind.Modern -> CacheEntriesProbe(
            privateModern!!.findAll(url).map { it.toProbe() },
        )

        CacheStorageKind.Legacy -> CacheEntriesProbe(
            privateLegacy!!.findByUrl(url).map { it.toProbe() },
        )
    }
}

fun HttpClientConfig<*>.install(cache: CacheTestFixtures, configure: HttpCacheConfig.() -> Unit = {}) {
    install(HttpCache) {
        when (cache.kind) {
            CacheStorageKind.Modern -> {
                publicStorage(cache.publicModern!!)
                privateStorage(cache.privateModern!!)
            }

            CacheStorageKind.Legacy -> {
                publicStorage = cache.publicLegacy!!
                privateStorage = cache.privateLegacy!!
            }
        }
        configure()
    }
}

private fun CachedResponseData.toProbe(): CacheEntryProbe = CacheEntryProbe(headers, varyKeys, raw = this)

private fun HttpCacheEntry.toProbe(): CacheEntryProbe = CacheEntryProbe(response.headers, varyKeys, raw = this)

internal class FakeResponse {
    var content: String = ""
    var status: HttpStatusCode = HttpStatusCode.OK
    var headers: Headers = headersOf()
}

internal class MockCacheTestScope(
    val url: Url,
    val fixtures: CacheTestFixtures,
) {
    lateinit var handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData
    var testBody: suspend CoroutineScope.(HttpClient) -> Unit = {}
}

internal fun mockCacheTest(
    url: Url = Url("https://example.com/test"),
    fixtures: CacheTestFixtures = CacheTestFixtures.modern(),
    configureCache: HttpCacheConfig.() -> Unit = {},
    block: MockCacheTestScope.() -> Unit,
) = testWithEngine(MockEngine) {
    val scope = MockCacheTestScope(url, fixtures).apply(block)

    config {
        install(fixtures, configureCache)
        engine {
            addHandler { request -> scope.handler(this, request) }
        }
    }

    test { client -> scope.testBody(this, client) }
}

internal fun defaultRevalidateInitialHeaders(
    etag: String = DEFAULT_ETAG,
    lastModified: String = DEFAULT_LAST_MODIFIED,
    match: RevalidationMatch = RevalidationMatch.ETag,
    extra: Headers = headersOf(),
): Headers = buildHeaders {
    when (match) {
        RevalidationMatch.ETag -> append(HttpHeaders.ETag, etag)
        RevalidationMatch.LastModified -> append(HttpHeaders.LastModified, lastModified)
    }
    append(HttpHeaders.CacheControl, REVALIDATE_CACHE_CONTROL)
    extra.forEach { name, values -> appendAll(name, values) }
}

internal fun MockCacheTestScope.revalidateHandler(
    match: RevalidationMatch = RevalidationMatch.ETag,
    etag: String = DEFAULT_ETAG,
    lastModified: String = DEFAULT_LAST_MODIFIED,
    initialBody: String = "body",
    initialHeaders: Headers = defaultRevalidateInitialHeaders(etag, lastModified, match),
    notModifiedHeaders: Headers = headersOf(),
) {
    handler = { request ->
        val isRevalidation = when (match) {
            RevalidationMatch.ETag -> HttpHeaders.IfNoneMatch in request.headers
            RevalidationMatch.LastModified -> HttpHeaders.IfModifiedSince in request.headers
        }
        if (isRevalidation) {
            respond("", HttpStatusCode.NotModified, notModifiedHeaders)
        } else {
            respond(initialBody, HttpStatusCode.OK, initialHeaders)
        }
    }
}

internal fun hopByHopHeaderValues(includeProxyAuth: Boolean = false): Headers = buildHeaders {
    append(HttpHeaders.Connection, "close, Foo")
    append(HttpHeaders.TransferEncoding, "chunked")
    append("Keep-Alive", "timeout=5")
    append("Proxy-Connection", "keep-alive")
    append(HttpHeaders.TE, "trailers")
    append(HttpHeaders.Upgrade, "websocket")
    append(HttpHeaders.ContentRange, "bytes 0-0/100")
    append("Foo", "bar")
    if (includeProxyAuth) {
        append(HttpHeaders.ProxyAuthenticate, "Basic")
    }
}

internal fun hopByHop304Headers(etag: String = DEFAULT_ETAG): Headers = buildHeaders {
    append(HttpHeaders.ETag, etag)
    append(HttpHeaders.CacheControl, "max-age=120")
    hopByHopHeaderValues().forEach { name, values -> appendAll(name, values) }
}

internal fun hopByHopOkHeaders(
    etag: String = DEFAULT_ETAG,
    cacheControl: String = "max-age=60",
    contentLength: String? = "4",
    extra: Headers = headersOf(),
    includeProxyAuth: Boolean = false,
): Headers = buildHeaders {
    append(HttpHeaders.ETag, etag)
    append(HttpHeaders.CacheControl, cacheControl)
    if (contentLength != null) {
        append(HttpHeaders.ContentLength, contentLength)
    }
    hopByHopHeaderValues(includeProxyAuth).forEach { name, values -> appendAll(name, values) }
    extra.forEach { name, values -> appendAll(name, values) }
}

internal fun assertHopByHopAbsent(headers: Headers, includeProxyAuth: Boolean = false) {
    assertNull(headers[HttpHeaders.Connection])
    assertNull(headers[HttpHeaders.TransferEncoding])
    assertNull(headers["Keep-Alive"])
    assertNull(headers["Proxy-Connection"])
    assertNull(headers[HttpHeaders.TE])
    assertNull(headers[HttpHeaders.Upgrade])
    assertNull(headers[HttpHeaders.ContentRange])
    assertNull(headers["Foo"])
    if (includeProxyAuth) {
        assertNull(headers[HttpHeaders.ProxyAuthenticate])
    }
}

/**
 * Does delay and ensures that the [GMTDate] measurements report at least
 * the specified number of [milliseconds].
 */
internal suspend fun delayGMTDate(milliseconds: Long) {
    var delayValue = milliseconds

    do {
        val start = GMTDate()
        delay(delayValue.milliseconds)
        val end = GMTDate()
        if (end > start + milliseconds) {
            break
        }
        if (delayValue != 1L) {
            delayValue = 1L
        }
    } while (true)
}

internal suspend fun testNoStoreScenario(cache: CacheTestFixtures, client: HttpClient, url: Url) {
    val first = client.get(url).body<String>()
    assertTrue(cache.privateEntries(url).isEmpty())
    assertTrue(cache.publicEntries(url).isEmpty())

    val second = client.get(url).body<String>()
    assertTrue(cache.privateEntries(url).isEmpty())
    assertTrue(cache.publicEntries(url).isEmpty())

    assertNotEquals(first, second)
}

internal suspend fun testNoCacheScenario(cache: CacheTestFixtures, client: HttpClient, url: Url) {
    val first = client.get(url).body<String>()
    assertEquals(1, cache.publicEntries(url).size)
    assertEquals(0, cache.privateEntries(url).size)

    val second = client.get(url).body<String>()
    assertEquals(1, cache.publicEntries(url).size)
    assertEquals(0, cache.privateEntries(url).size)

    assertNotEquals(first, second)
}

internal suspend fun testETagCacheScenario(cache: CacheTestFixtures, client: HttpClient, url: Url) {
    val first = client.get(url).body<String>()
    assertEquals(1, cache.publicEntries(url).size)

    val second = client.get(url).body<String>()
    assertEquals(1, cache.publicEntries(url).size)

    assertEquals(first, second)
}

internal suspend fun testVaryScenario(client: HttpClient, url: Url) {
    val first = client.get(url) {
        header(HttpHeaders.ContentLanguage, "en")
    }.bodyAsText()

    val second = client.get(url) {
        header(HttpHeaders.ContentLanguage, "en")
    }.bodyAsText()

    assertEquals(first, second)

    val third = client.get(url) {
        header(HttpHeaders.ContentLanguage, "ru")
    }.bodyAsText()

    assertNotEquals(third, second)

    val fourth = client.get(url) {
        header(HttpHeaders.ContentLanguage, "ru")
    }.bodyAsText()

    assertEquals(third, fourth)

    val fifth = client.get(url) {
        header(HttpHeaders.ContentLanguage, "en")
    }.bodyAsText()

    assertEquals(first, fifth)

    val sixth = client.get(url).bodyAsText()

    assertNotEquals(sixth, second)
    assertNotEquals(sixth, third)

    val seventh = client.get(url).bodyAsText()

    assertEquals(sixth, seventh)
}

@OptIn(InternalAPI::class)
internal fun setupNoVaryIn304Client(client: HttpClient) {
    client.receivePipeline.intercept(HttpReceivePipeline.Before) { response ->
        if (response.status != HttpStatusCode.NotModified) {
            return@intercept
        }
        val headers = buildHeaders {
            response.headers
                .filter { name, _ -> !name.equals(HttpHeaders.Vary, ignoreCase = true) }
                .forEach(::appendAll)
        }
        proceedWith(
            object : HttpResponse() {
                override val call get() = response.call
                override val rawContent get() = response.rawContent
                override val coroutineContext get() = response.coroutineContext
                override val headers = headers
                override val requestTime get() = response.requestTime
                override val responseTime get() = response.responseTime
                override val status get() = response.status
                override val version get() = response.version
            }
        )
    }
}

internal suspend fun testNoVaryIn304Scenario(client: HttpClient, url: Url) {
    setupNoVaryIn304Client(client)
    testVaryScenario(client, url)
}

internal suspend fun testMaxAgeScenario(cache: CacheTestFixtures, client: HttpClient, url: Url) {
    val first = client.get(url).body<String>()
    assertEquals(1, cache.publicEntries(url).size)

    val second = client.get(url).body<String>()

    assertEquals(first, second)
    delay(2500.milliseconds)

    val third = client.get(url).body<String>()
    assertNotEquals(first, third)
}

internal suspend fun testOnlyIfCachedScenario(client: HttpClient, url: Url) {
    val responseNoCache = client.get(url) {
        header(HttpHeaders.CacheControl, "only-if-cached")
    }
    assertEquals(HttpStatusCode.GatewayTimeout, responseNoCache.status)

    val bodyOriginal = client.get(url).bodyAsText()

    val responseCached = client.get(url) {
        header(HttpHeaders.CacheControl, "only-if-cached")
    }
    val bodyCached = responseCached.bodyAsText()
    assertEquals(HttpStatusCode.OK, responseCached.status)
    assertEquals(bodyOriginal, bodyCached)
}

internal suspend fun testMaxStaleScenario(cache: CacheTestFixtures, client: HttpClient, url: Url) {
    val original = client.get(url).body<String>()
    assertEquals(1, cache.publicEntries(url).size)

    delay(2500.milliseconds)

    val stale = client.get(url) {
        header(HttpHeaders.CacheControl, "max-stale=4")
    }
    assertEquals("110", stale.headers[HttpHeaders.Warning])
    val staleBody = stale.body<String>()
    assertEquals(original, staleBody)

    val staleMaxInt = client.get(url) {
        header(HttpHeaders.CacheControl, "max-stale=${Int.MAX_VALUE}")
    }
    assertEquals("110", stale.headers[HttpHeaders.Warning])
    val staleMaxIntBody = staleMaxInt.body<String>()
    assertEquals(original, staleMaxIntBody)

    val notStale = client.get(url)
    val notStaleBody = notStale.body<String>()
    assertNull(notStale.headers[HttpHeaders.Warning])
    assertNotEquals(original, notStaleBody)
}

internal suspend fun testNoStoreRequestScenario(cache: CacheTestFixtures, client: HttpClient, url: Url) {
    val first = client.get(url) {
        header(HttpHeaders.CacheControl, "no-store")
    }.body<String>()
    assertEquals(0, cache.publicEntries(url).size)

    val second = client.get(url).body<String>()
    assertEquals(1, cache.publicEntries(url).size)

    assertNotEquals(first, second)
}

internal suspend fun testNoCacheRequestScenario(cache: CacheTestFixtures, client: HttpClient, url: Url) {
    var requestsCount = 0
    client.sendPipeline.intercept(HttpSendPipeline.Engine) {
        requestsCount++
    }

    val first = client.get(url).body<String>()
    assertEquals(1, cache.publicEntries(url).size)

    val second = client.get(url) {
        header(HttpHeaders.CacheControl, "no-cache")
    }.body<String>()
    assertEquals(1, cache.publicEntries(url).size)

    assertEquals(2, requestsCount)
    assertEquals(first, second)
}

internal suspend fun testRequestWithMaxAge0Scenario(cache: CacheTestFixtures, client: HttpClient, url: Url) {
    var requestsCount = 0
    client.sendPipeline.intercept(HttpSendPipeline.Engine) {
        requestsCount++
    }

    val first = client.get(url).body<String>()
    assertEquals(1, cache.publicEntries(url).size)

    val second = client.get(url) {
        header(HttpHeaders.CacheControl, "max-age=0")
    }.body<String>()
    assertEquals(1, cache.publicEntries(url).size)

    assertEquals(2, requestsCount)
    assertEquals(first, second)
}

internal suspend fun testExpiresScenario(
    cache: CacheTestFixtures,
    client: HttpClient,
    url: Url,
    expiresOffset: Duration,
    expireDelay: Duration,
) {
    val now = GMTDate() + expiresOffset

    suspend fun getWithHeader(expires: String): String {
        delayGMTDate(1)
        return client.get(url) { header("X-Expires", expires) }.body()
    }

    val first = getWithHeader(now.toHttpDate())
    assertEquals(1, cache.publicEntries(url).size)

    val second = client.get(url).body<String>()

    assertEquals(first, second)
    delay(expireDelay)

    val third = client.get(url).body<String>()
    assertNotEquals(first, third)

    var previous = third
    getWithHeader("broken-date").let { result ->
        assertNotEquals(previous, result)
        previous = result
    }

    getWithHeader("0").let { result ->
        assertNotEquals(previous, result)
        previous = result
    }

    getWithHeader(" ").let { result ->
        assertNotEquals(previous, result)
        previous = result
    }

    delayGMTDate(1)
    val last = client.get(url).body<String>()
    assertNotEquals(previous, last)
}

internal suspend fun testPublicAndPrivateCacheScenario(cache: CacheTestFixtures, client: HttpClient) {
    val privateUrl = Url("$TEST_SERVER/cache/private")
    val publicUrl = Url("$TEST_SERVER/cache/public")

    val firstPrivate = client.get(privateUrl).body<String>()
    assertEquals("private", firstPrivate)
    assertEquals(1, cache.privateEntries(privateUrl).size)
    assertEquals(0, cache.publicEntries(publicUrl).size)
    val privateCacheEntry = cache.privateEntries(privateUrl).first().raw

    val firstPublic = client.get(publicUrl).body<String>()
    assertEquals("public", firstPublic)
    assertEquals(1, cache.publicEntries(publicUrl).size)
    assertEquals(1, cache.privateEntries(privateUrl).size)
    val publicCacheEntry = cache.publicEntries(publicUrl).first().raw

    val secondPrivate = client.get(privateUrl).body<String>()
    assertEquals("private", secondPrivate)

    assertSame(privateCacheEntry, cache.privateEntries(privateUrl).first().raw)

    val secondPublic = client.get(publicUrl).body<String>()
    assertEquals("public", secondPublic)

    assertEquals(1, cache.privateEntries(privateUrl).size)
    assertEquals(1, cache.publicEntries(publicUrl).size)

    assertSame(publicCacheEntry, cache.publicEntries(publicUrl).first().raw)
}

internal suspend fun testWithLoggingScenario(client: HttpClient) {
    client.receivePipeline.intercept(HttpReceivePipeline.State) { response ->
        val savedResponse = response.call.save().response
        proceedWith(savedResponse)
    }
    val result = client.get("$TEST_SERVER/content/chunked-data?size=5000").bodyAsText()
    assertEquals(5000, result.length)
}

internal suspend fun test304WithoutVaryMatchesByEtagScenario(cache: CacheTestFixtures, client: HttpClient, url: Url) {
    val first = client.get(url).bodyAsText()
    assertEquals("body", first)

    val second = client.get(url).bodyAsText()
    assertEquals("body", second)

    assertEquals(1, cache.publicEntries(url).size)
}
