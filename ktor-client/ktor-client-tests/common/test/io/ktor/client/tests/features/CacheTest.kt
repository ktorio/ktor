/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package io.ktor.client.tests.features

import io.ktor.client.features.cache.*
import io.ktor.client.request.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlin.test.*

class CacheTest : ClientLoader() {
    @Test
    fun testNoStore() = clientTests {
        var storage: HttpCache.Config? = null
        config {
            install(HttpCache) {
                storage = this
            }
        }

        test { client ->
            val url = Url("$TEST_SERVER/cache/no-store")

            val first = client.get<String>(url)
            assertTrue(storage!!.privateStorage.findByUrl(url).isEmpty())
            assertTrue(storage!!.publicStorage.findByUrl(url).isEmpty())

            val second = client.get<String>(url)
            assertTrue(storage!!.privateStorage.findByUrl(url).isEmpty())
            assertTrue(storage!!.publicStorage.findByUrl(url).isEmpty())

            assertNotEquals(first, second)
        }
    }

    @Test
    fun testNoCache() = clientTests {
        var storage: HttpCache.Config? = null
        config {
            install(HttpCache) {
                storage = this
            }
        }

        test { client ->
            val url = Url("$TEST_SERVER/cache/no-cache")

            val first = client.get<String>(url)
            assertEquals(1, storage!!.publicStorage.findByUrl(url).size)
            assertEquals(0, storage!!.privateStorage.findByUrl(url).size)

            val second = client.get<String>(url)
            assertEquals(1, storage!!.publicStorage.findByUrl(url).size)
            assertEquals(0, storage!!.privateStorage.findByUrl(url).size)

            assertNotEquals(first, second)
        }
    }

    @Test
    fun testETagCache() = clientTests(listOf("Js")) {
        var storage: HttpCache.Config? = null
        config {
            install(HttpCache) {
                storage = this
            }
        }

        test { client ->
            val url = Url("$TEST_SERVER/cache/etag")

            val first = client.get<String>(url)
            assertEquals(1, storage!!.publicStorage.findByUrl(url).size)

            val second = client.get<String>(url)
            assertEquals(1, storage!!.publicStorage.findByUrl(url).size)

            assertEquals(first, second)
        }
    }

    @Test
    fun testLastModified() = clientTests(listOf("Js")) {
        var storage: HttpCache.Config? = null
        config {
            install(HttpCache) {
                storage = this
            }
        }

        test { client ->
            val url = Url("$TEST_SERVER/cache/last-modified")

            val first = client.get<String>(url)
            assertEquals(1, storage!!.publicStorage.findByUrl(url).size)

            val second = client.get<String>(url)
            assertEquals(1, storage!!.publicStorage.findByUrl(url).size)

            assertEquals(first, second)
        }
    }

    @Test
    fun testVary() = clientTests(listOf("Js")) {
        var storage: HttpCache.Config? = null
        config {
            install(HttpCache) {
                storage = this
            }
        }

        test { client ->
            val url = Url("$TEST_SERVER/cache/vary")

            val first = client.get<String>(url) {
                header(HttpHeaders.ContentLanguage, "en")

            }

            val second = client.get<String>(url) {
                header(HttpHeaders.ContentLanguage, "en")
            }

            assertEquals(first, second)

            val third = client.get<String>(url) {
                header(HttpHeaders.ContentLanguage, "ru")

            }

            assertNotEquals(third, second)

            val fourth = client.get<String>(url) {
                header(HttpHeaders.ContentLanguage, "ru")

            }

            assertEquals(third, fourth)

            val fifth = client.get<String>(url) {
                header(HttpHeaders.ContentLanguage, "en")

            }

            assertEquals(first, fifth)
        }
    }

    @Test
    fun testMaxAge() = clientTests {
        var storage: HttpCache.Config? = null
        config {
            install(HttpCache) {
                storage = this
            }
        }

        test { client ->
            val url = Url("$TEST_SERVER/cache/max-age")

            val first = client.get<String>(url)
            assertEquals(1, storage!!.publicStorage.findByUrl(url).size)

            val second = client.get<String>(url)

            assertEquals(first, second)
            delay(1000)

            val third = client.get<String>(url)
            assertNotEquals(first, third)
        }
    }

    @Test
    fun testPublicAndPrivateCache() = clientTests {
        var storage: HttpCache.Config? = null
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

            val firstPrivate = client.get<String>(privateUrl)
            assertEquals(firstPrivate, "private")
            assertEquals(1, privateCache().size)
            assertEquals(0, publicCache().size)

            val privateCacheEntry = privateCache().first()

            val firstPublic = client.get<String>(publicUrl)
            assertEquals(firstPublic, "public")
            assertEquals(1, publicCache().size)
            assertEquals(1, privateCache().size)

            assertSame(privateCacheEntry, privateCache().first())
            val publicCacheEntry = publicCache().first()

            // Private is updated from server by server implementation.
            val secondPrivate = client.get<String>(privateUrl)
            assertEquals(secondPrivate, "private")

            // Old entry should be replaced.
            assertEquals(1, privateCache().size)

            // Public keeps the same.
            assertEquals(1, publicCache().size)

            val actual = privateCache().first()
            assertNotSame(privateCacheEntry, actual)

            // Public from cache.
            val secondPublic = client.get<String>(publicUrl)
            assertEquals(secondPublic, "public")

            assertEquals(1, privateCache().size)
            assertEquals(1, publicCache().size)

            assertSame(publicCacheEntry, publicCache().first())
        }
    }
}
