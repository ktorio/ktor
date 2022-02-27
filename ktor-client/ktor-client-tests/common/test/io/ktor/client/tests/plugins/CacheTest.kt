/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/
package io.ktor.client.tests.plugins

import io.ktor.client.call.*
import io.ktor.client.plugins.cache.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.tests.utils.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.util.*
import io.ktor.util.date.*
import kotlinx.coroutines.*
import kotlin.test.*

class CacheTest : ClientLoader() {
    var storage: HttpCache.Config? = null

    @Test
    fun testNoStore() = clientTests {
        config {
            install(HttpCache) {
                storage = this
            }
        }

        test { client ->
            val url = Url("$TEST_SERVER/cache/no-store")

            val first = client.get(url).body<String>()
            assertTrue(storage!!.privateStorage.findByUrl(url).isEmpty())
            assertTrue(storage!!.publicStorage.findByUrl(url).isEmpty())

            val second = client.get(url).body<String>()
            assertTrue(storage!!.privateStorage.findByUrl(url).isEmpty())
            assertTrue(storage!!.publicStorage.findByUrl(url).isEmpty())

            assertNotEquals(first, second)
        }
    }

    @Test
    fun testNoCache() = clientTests {
        config {
            install(HttpCache) {
                storage = this
            }
        }

        test { client ->
            val url = Url("$TEST_SERVER/cache/no-cache")

            val first = client.get(url).body<String>()
            assertEquals(1, storage!!.publicStorage.findByUrl(url).size)
            assertEquals(0, storage!!.privateStorage.findByUrl(url).size)

            val second = client.get(url).body<String>()
            assertEquals(1, storage!!.publicStorage.findByUrl(url).size)
            assertEquals(0, storage!!.privateStorage.findByUrl(url).size)

            assertNotEquals(first, second)
        }
    }

    @Test
    fun testETagCache() = clientTests(listOf("Js")) {
        config {
            install(HttpCache) {
                storage = this
            }
        }

        test { client ->
            val url = Url("$TEST_SERVER/cache/etag")

            val first = client.get(url).body<String>()
            assertEquals(1, storage!!.publicStorage.findByUrl(url).size)

            val second = client.get(url).body<String>()
            assertEquals(1, storage!!.publicStorage.findByUrl(url).size)

            assertEquals(first, second)
        }
    }

    @Test
    fun testLastModified() = clientTests(listOf("Js")) {
        config {
            install(HttpCache) {
                storage = this
            }
        }

        test { client ->
            val url = Url("$TEST_SERVER/cache/last-modified")

            val first = client.get(url).body<String>()
            assertEquals(1, storage!!.publicStorage.findByUrl(url).size)

            val second = client.get(url).body<String>()
            assertEquals(1, storage!!.publicStorage.findByUrl(url).size)

            assertEquals(first, second)
        }
    }

    @Test
    fun testVary() = clientTests(listOf("Js")) {
        config {
            install(HttpCache) {
                storage = this
            }
        }

        test { client ->
            val url = Url("$TEST_SERVER/cache/vary")

            // first header value from Vary
            val first = client.get(url) {
                header(HttpHeaders.ContentLanguage, "en")
            }.body<String>()

            val second = client.get(url) {
                header(HttpHeaders.ContentLanguage, "en")
            }.body<String>()

            assertEquals(first, second)

            // second header value from Vary
            val third = client.get(url) {
                header(HttpHeaders.ContentLanguage, "ru")
            }.body<String>()

            assertNotEquals(third, second)

            val fourth = client.get(url) {
                header(HttpHeaders.ContentLanguage, "ru")
            }.body<String>()

            assertEquals(third, fourth)

            // first header value from Vary
            val fifth = client.get(url) {
                header(HttpHeaders.ContentLanguage, "en")
            }.body<String>()

            assertEquals(first, fifth)

            // no header value from Vary
            val sixth = client.get(url).body<String>()

            assertNotEquals(sixth, second)
            assertNotEquals(sixth, third)

            val seventh = client.get(url).body<String>()

            assertEquals(sixth, seventh)
        }
    }

    @Test
    fun testVaryStale() = clientTests(listOf("Js")) {
        config {
            install(HttpCache) {
                storage = this
            }
        }

        test { client ->
            val url = Url("$TEST_SERVER/cache/vary-stale")

            // first header value from Vary
            val first = client.get(url) {
                header(HttpHeaders.ContentLanguage, "en")
            }.body<String>()

            val second = client.get(url) {
                header(HttpHeaders.ContentLanguage, "en")
            }.body<String>()

            assertEquals(first, second)

            // second header value from Vary
            val third = client.get(url) {
                header(HttpHeaders.ContentLanguage, "ru")
            }.body<String>()

            assertNotEquals(third, second)

            val fourth = client.get(url) {
                header(HttpHeaders.ContentLanguage, "ru")
            }.body<String>()

            assertEquals(third, fourth)

            // first header value from Vary
            val fifth = client.get(url) {
                header(HttpHeaders.ContentLanguage, "en")
            }.body<String>()

            assertEquals(first, fifth)

            // no header value from Vary
            val sixth = client.get(url).body<String>()

            assertNotEquals(sixth, second)
            assertNotEquals(sixth, third)

            val seventh = client.get(url).body<String>()

            assertEquals(sixth, seventh)
        }
    }

    @OptIn(InternalAPI::class)
    @Test
    fun testNoVaryIn304() = clientTests(listOf("Js")) {
        config {
            install(HttpCache) {
                storage = this
            }
        }

        test { client ->
            client.receivePipeline.intercept(HttpReceivePipeline.Before) { response ->
                if (response.status == HttpStatusCode.NotModified) {
                    val headers = buildHeaders {
                        response.headers
                            .filter { name, _ ->
                                !name.equals(HttpHeaders.Vary, ignoreCase = true)
                            }
                            .forEach(::appendAll)
                    }
                    proceedWith(
                        object : HttpResponse() {
                            override val call get() = response.call
                            override val content get() = response.content
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

            val url = Url("$TEST_SERVER/cache/vary-stale")

            // first header value from Vary
            val first = client.get(url) {
                header(HttpHeaders.ContentLanguage, "en")
            }.body<String>()

            val second = client.get(url) {
                header(HttpHeaders.ContentLanguage, "en")
            }.body<String>()

            assertEquals(first, second)

            // second header value from Vary
            val third = client.get(url) {
                header(HttpHeaders.ContentLanguage, "ru")
            }.body<String>()

            assertNotEquals(third, second)

            val fourth = client.get(url) {
                header(HttpHeaders.ContentLanguage, "ru")
            }.body<String>()

            assertEquals(third, fourth)

            // first header value from Vary
            val fifth = client.get(url) {
                header(HttpHeaders.ContentLanguage, "en")
            }.body<String>()

            assertEquals(first, fifth)

            // no header value from Vary
            val sixth = client.get(url).body<String>()

            assertNotEquals(sixth, second)
            assertNotEquals(sixth, third)

            val seventh = client.get(url).body<String>()

            assertEquals(sixth, seventh)
        }
    }

    @Test
    fun testMaxAge() = clientTests {
        config {
            install(HttpCache) {
                storage = this
            }
        }

        test { client ->
            val url = Url("$TEST_SERVER/cache/max-age")

            val first = client.get(url).body<String>()
            val cache = storage!!.publicStorage.findByUrl(url)
            assertEquals(1, cache.size)

            val second = client.get(url).body<String>()

            assertEquals(first, second)
            delay(5000)

            val third = client.get(url).body<String>()
            assertNotEquals(first, third)
        }
    }

    @Test
    fun testExpires() = clientTests {
        config {
            install(HttpCache) {
                storage = this
            }
        }

        test { client ->
            val now = GMTDate() + 2000L
            val url = Url("$TEST_SERVER/cache/expires")

            suspend fun getWithHeader(expires: String): String {
                delayGMTDate(1)

                return client.get(url) {
                    header("X-Expires", expires)
                }.body()
            }

            val first = getWithHeader(now.toHttpDate())
            val cache = storage!!.publicStorage.findByUrl(url)
            assertEquals(1, cache.size)

            // this should be from the cache
            val second = client.get(url).body<String>()

            assertEquals(first, second)
            delay(5000)

            // now it should be already expired
            val third = client.get(url).body<String>()
            assertNotEquals(first, third)

            // illegal values: broken, "0" and blank should be treated as already expired
            // so shouldn't be cached
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
    }

    @Test
    fun testPublicAndPrivateCache() = clientTests(listOf("native")) {
        config {
            install(HttpCache) {
                storage = this
            }
        }

        test { client ->
            val privateUrl = Url("$TEST_SERVER/cache/private")
            val publicUrl = Url("$TEST_SERVER/cache/public")

            fun publicCache() = storage!!.publicStorage.findByUrl(publicUrl)
            fun privateCache() = storage!!.privateStorage.findByUrl(privateUrl)

            val firstPrivate = client.get(privateUrl).body<String>()
            assertEquals(firstPrivate, "private")
            assertEquals(1, privateCache().size)
            assertEquals(0, publicCache().size)

            val privateCacheEntry = privateCache().first()

            val firstPublic = client.get(publicUrl).body<String>()
            assertEquals(firstPublic, "public")
            assertEquals(1, publicCache().size)
            assertEquals(1, privateCache().size)

            assertSame(privateCacheEntry, privateCache().first())
            val publicCacheEntry = publicCache().first()

            // Private is updated from server by server implementation.
            val secondPrivate = client.get(privateUrl).body<String>()
            assertEquals(secondPrivate, "private")

            // Old entry should be replaced.
            assertEquals(1, privateCache().size)

            // Public keeps the same.
            assertEquals(1, publicCache().size)

            val actual = privateCache().first()
            assertNotSame(privateCacheEntry, actual)

            // Public from cache.
            val secondPublic = client.get(publicUrl).body<String>()
            assertEquals(secondPublic, "public")

            assertEquals(1, privateCache().size)
            assertEquals(1, publicCache().size)

            assertSame(publicCacheEntry, publicCache().first())
        }
    }

    /**
     * Does delay and ensures that the [GMTDate] measurements report at least
     * the specified number of [milliseconds].
     * The reason why it's not the same is that on some platforms time granularity of GMTDate
     * is not that high and could be even larger than a millisecond.
     */
    private suspend fun delayGMTDate(milliseconds: Long) {
        var delayValue = milliseconds

        do {
            val start = GMTDate()
            delay(delayValue)
            val end = GMTDate()
            if (end > start + milliseconds) {
                break
            }
            if (delayValue != 1L) {
                delayValue = 1L
            }
        } while (true)
    }
}
