/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.call.*
import io.ktor.client.plugins.cache.*
import io.ktor.client.plugins.cache.storage.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import java.nio.file.*
import kotlin.test.*

class FileCacheTest : ClientLoader() {
    private val publicStorage = FileStorage(Files.createTempDirectory("cache-test-public").toFile())
    private val privateStorage = FileStorage(Files.createTempDirectory("cache-test-private").toFile())

    @Test
    fun testVaryHeader() = clientTests(listOf("Js")) {
        config {
            install(HttpCache) {
                publicStorage(this@FileCacheTest.publicStorage)
                privateStorage(this@FileCacheTest.privateStorage)
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

            assertEquals(3, publicStorage.findAll(url).size)
            assertEquals(0, privateStorage.findAll(url).size)
        }
    }

    @Test
    fun testReuseCacheStorage() = clientTests(listOf("Js")) {
        config {
            install(HttpCache) {
                publicStorage(this@FileCacheTest.publicStorage)
                privateStorage(this@FileCacheTest.privateStorage)
            }
        }

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
    fun testLongPath() = clientTests {
        config {
            install(HttpCache) {
                publicStorage(this@FileCacheTest.publicStorage)
            }
        }

        test { client ->
            val response = client.get("$TEST_SERVER/cache/cache_${"a".repeat(3000)}").body<String>()
            assertEquals("abc", response)
        }
    }

    @Test
    fun testSkipCacheIfException() = clientTests {
        val file = Files.createTempDirectory("cache-test-public-deleted").toFile()
        val publicStorage = FileStorage(file)
        config {
            install(HttpCache) {
                publicStorage(publicStorage)
            }
        }
        test { client ->
            val first = client.get(Url("$TEST_SERVER/cache/public")).bodyAsText()
            assertEquals("public", first)

            file.deleteRecursively()

            val second = client.get("$TEST_SERVER/cache/cache_${"a".repeat(3000)}")
            assertEquals("abc", second.bodyAsText())
        }
    }
}
