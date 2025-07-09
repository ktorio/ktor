/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.call.*
import io.ktor.client.plugins.cache.*
import io.ktor.client.plugins.cache.storage.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.test.base.*
import io.ktor.http.*
import kotlinx.io.files.*
import kotlin.test.*
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class FileCacheTest : ClientLoader() {
    private val tmpDirPath = temporaryDirectoryPath()
    private val publicStorage = FileStorage(SystemFileSystem, Path(tmpDirPath, "cache-test-public"))
    private val privateStorage = FileStorage(SystemFileSystem, Path(tmpDirPath, "cache-test-private"))

    @Test
    fun testVaryHeader() = clientTests(except("Js")) {
        config {
            install(HttpCache.Companion) {
                publicStorage(this@FileCacheTest.publicStorage)
                privateStorage(this@FileCacheTest.privateStorage)
            }
        }

        test { client ->
            val url = Url("${TEST_SERVER}/cache/vary")

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
    fun testReuseCacheStorage() = clientTests(except("Js")) {
        config {
            install(HttpCache.Companion) {
                publicStorage(this@FileCacheTest.publicStorage)
                privateStorage(this@FileCacheTest.privateStorage)
            }
        }

        test { client ->
            val client1 = client.config { }
            val client2 = client.config { }
            val url = Url("${TEST_SERVER}/cache/etag-304")

            val first = client1.get(url)
            val second = client2.get(url)

            assertEquals(HttpStatusCode.Companion.OK, first.status)
            assertEquals(HttpStatusCode.Companion.OK, second.status)
            assertEquals(first.body<String>(), second.body<String>())
        }
    }

    @Test
    fun testLongPath() = clientTests(except("Js")) {
        config {
            install(HttpCache.Companion) {
                publicStorage(this@FileCacheTest.publicStorage)
            }
        }

        test { client ->
            val response = client.get("${TEST_SERVER}/cache/cache_${"a".repeat(3000)}").body<String>()
            assertEquals("abc", response)
        }
    }

    @Test
    fun testSkipCacheIfException() = clientTests(except("Js")) {
        val path = Path(SystemTemporaryDirectory, "cache-test-public-deleted")
        val publicStorage = FileStorage(SystemFileSystem, path)
        config {
            install(HttpCache.Companion) {
                publicStorage(publicStorage)
            }
        }
        test { client ->
            val first = client.get(Url("${TEST_SERVER}/cache/public")).bodyAsText()
            assertEquals("public", first)

            SystemFileSystem.deleteRecursively(path)

            val second = client.get("${TEST_SERVER}/cache/cache_${"a".repeat(3000)}")
            assertEquals("abc", second.bodyAsText())
        }
    }

    companion object {
        @OptIn(ExperimentalUuidApi::class)
        private fun temporaryDirectoryPath(): Path {
            return Path(SystemTemporaryDirectory, Uuid.random().toString())
        }

        private fun FileSystem.deleteRecursively(directory: Path) {
            for (subPath in list(directory)) {
                if (metadataOrNull(subPath)?.isDirectory == true) {
                    deleteRecursively(subPath)
                } else {
                    delete(subPath)
                }
            }
            delete(directory)
        }
    }
}
