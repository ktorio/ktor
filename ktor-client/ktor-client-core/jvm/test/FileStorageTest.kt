/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.client.plugins.cache.storage.*
import io.ktor.http.*
import io.ktor.util.date.*
import kotlinx.coroutines.test.*
import java.io.*
import kotlin.io.path.*
import kotlin.test.*

class FileStorageTest {
    private lateinit var tempDirectory: File

    @BeforeTest
    fun setUp() {
        tempDirectory = createTempDirectory().toFile()
    }

    @AfterTest
    fun tearDown() {
        tempDirectory.deleteRecursively()
    }

    @Test
    fun testFindAll() = runTest {
        val storage = FileStorage(tempDirectory)

        storage.store(Url("http://example.com"), data())
        storage.store(Url("http://example.com"), data(mapOf("key" to "value")))

        assertEquals(2, storage.findAll(Url("http://example.com")).size)
    }

    @Test
    fun testFind() = runTest {
        val storage = FileStorage(tempDirectory)

        storage.store(Url("http://example.com"), data())
        storage.store(Url("http://example.com"), data(mapOf("key" to "value")))

        assertNotNull(storage.find(Url("http://example.com"), mapOf("key" to "value")))
    }

    @Test
    fun testStore() = runTest {
        val storage = FileStorage(tempDirectory)
        storage.store(Url("http://example.com"), data())

        assertEquals(1, storage.findAll(Url("http://example.com")).size)

        storage.store(Url("http://example.com"), data(mapOf("key" to "value")))
        assertEquals(2, storage.findAll(Url("http://example.com")).size)
        assertNotNull(storage.find(Url("http://example.com"), mapOf("key" to "value")))
    }

    @Test
    fun testRemove() = runTest {
        val storage = FileStorage(tempDirectory)
        storage.store(Url("http://example.com"), data())
        storage.store(Url("http://example.com"), data(mapOf("key" to "value")))

        assertEquals(2, storage.findAll(Url("http://example.com")).size)

        storage.remove(Url("http://example.com"), mapOf("key" to "value"))
        assertEquals(1, storage.findAll(Url("http://example.com")).size)
        assertNull(storage.find(Url("http://example.com"), mapOf("key" to "value")))
    }

    @Test
    fun testRemoveAll() = runTest {
        val storage = FileStorage(tempDirectory)
        storage.store(Url("http://example.com"), data())
        storage.store(Url("http://example.com"), data(mapOf("key" to "value")))

        assertEquals(2, storage.findAll(Url("http://example.com")).size)

        storage.removeAll(Url("http://example.com"))
        assertEquals(0, storage.findAll(Url("http://example.com")).size)
    }

    private fun data(varyKeys: Map<String, String> = emptyMap()) = CachedResponseData(
        Url("http://example.com"),
        HttpStatusCode.OK,
        GMTDate(),
        GMTDate(),
        HttpProtocolVersion.HTTP_1_1,
        GMTDate(),
        headersOf(),
        varyKeys,
        ByteArray(0)
    )
}
