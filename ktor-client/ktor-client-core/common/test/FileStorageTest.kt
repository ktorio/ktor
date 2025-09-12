/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.client.plugins.cache.storage.*
import io.ktor.http.*
import io.ktor.util.date.*
import kotlinx.coroutines.test.*
import kotlinx.io.files.*
import kotlin.test.*
import kotlin.uuid.*

class FileStorageTest {
    private lateinit var tempDirectory: Path

    @BeforeTest
    fun setUp() {
        tempDirectory = temporaryDirectoryPath()
        SystemFileSystem.createDirectories(tempDirectory)
    }

    @AfterTest
    fun tearDown() {
        SystemFileSystem.deleteRecursively(tempDirectory)
    }

    @Test
    fun testFindAll() = runTest {
        val storage = FileStorage(SystemFileSystem, tempDirectory)

        storage.store(Url("http://example.com"), data())
        storage.store(Url("http://example.com"), data(mapOf("key" to "value")))

        assertEquals(2, storage.findAll(Url("http://example.com")).size)
    }

    @Test
    fun testFind() = runTest {
        val storage = FileStorage(SystemFileSystem, tempDirectory)

        storage.store(Url("http://example.com"), data())
        storage.store(Url("http://example.com"), data(mapOf("key" to "value")))

        assertNotNull(storage.find(Url("http://example.com"), mapOf("key" to "value")))
    }

    @Test
    fun testStore() = runTest {
        val storage = FileStorage(SystemFileSystem, tempDirectory)
        storage.store(Url("http://example.com"), data())

        assertEquals(1, storage.findAll(Url("http://example.com")).size)

        storage.store(Url("http://example.com"), data(mapOf("key" to "value")))
        assertEquals(2, storage.findAll(Url("http://example.com")).size)
        assertNotNull(storage.find(Url("http://example.com"), mapOf("key" to "value")))
    }

    @Test
    fun testRemove() = runTest {
        val storage = FileStorage(SystemFileSystem, tempDirectory)
        storage.store(Url("http://example.com"), data())
        storage.store(Url("http://example.com"), data(mapOf("key" to "value")))

        assertEquals(2, storage.findAll(Url("http://example.com")).size)

        storage.remove(Url("http://example.com"), mapOf("key" to "value"))
        assertEquals(1, storage.findAll(Url("http://example.com")).size)
        assertNull(storage.find(Url("http://example.com"), mapOf("key" to "value")))
    }

    @Test
    fun testRemoveAll() = runTest {
        val storage = FileStorage(SystemFileSystem, tempDirectory)
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
