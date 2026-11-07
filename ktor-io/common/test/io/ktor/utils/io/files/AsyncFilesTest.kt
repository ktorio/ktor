/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.files

import io.ktor.test.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.io.readByteArray
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class AsyncFilesTest {

    @Test
    fun `reads files of varying sizes`() = runTest {
        for (size in listOf(0, 1, 64 * 1024 - 1, 64 * 1024, 64 * 1024 + 1, 10 * 1024 * 1024)) {
            withFile(Random(size).nextBytes(size)) { path, expected ->
                val actual = SystemFileSystem.byteReadChannel(path).readBuffer().readByteArray()
                assertContentEquals(expected, actual, "Unexpected contents for file with $size bytes")
            }
        }
    }

    @Test
    fun `reads concurrently from many channels`() = runTest {
        val content = Random(42).nextBytes(256 * 1024 + 3)
        withFile(content) { path, expected ->
            val results = coroutineScope {
                List(200) {
                    async(Dispatchers.Default) {
                        SystemFileSystem.byteReadChannel(path).readBuffer().readByteArray()
                    }
                }.awaitAll()
            }

            results.forEach { assertContentEquals(expected, it) }
        }
    }

    @Test
    fun `missing file fails when read`() = runTest {
        val path = temporaryPath()
        assertFailsWith<kotlinx.io.IOException> {
            SystemFileSystem.byteReadChannel(path).awaitContent(1)
        }
    }

    @Test
    fun `cancel stops further reads`() = runTest {
        withFile(Random(17).nextBytes(1024)) { path, _ ->
            val channel = SystemFileSystem.byteReadChannel(path)
            channel.cancel()

            assertFailsWith<kotlinx.coroutines.CancellationException> {
                channel.awaitContent(1)
            }
            assertTrue(channel.isClosedForRead)
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun temporaryPath(): Path = Path(SystemTemporaryDirectory, "ktor-async-files-${Uuid.random()}")

    private suspend fun withFile(content: ByteArray, block: suspend (Path, ByteArray) -> Unit) {
        val path = temporaryPath()
        try {
            SystemFileSystem.sink(path).buffered().use { it.write(content) }
            block(path, content)
        } finally {
            runCatching { SystemFileSystem.delete(path, mustExist = false) }
        }
    }
}
