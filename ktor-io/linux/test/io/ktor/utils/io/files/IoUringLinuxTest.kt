/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.files

import io.ktor.test.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.io.readByteArray
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class IoUringLinuxTest {

    @Test
    fun `uses io_uring channel when available`() = runTest {
        if (!SystemFileSystem.supportsAsyncFileIo()) return@runTest

        val content = Random(42).nextBytes(1024)
        withFile(content) { path ->
            val channel = SystemFileSystem.byteReadChannel(path)
            assertIs<IoUringByteReadChannel>(channel)
            assertContentEquals(content, channel.readBuffer().readByteArray())
        }
    }

    @Test
    fun `cancellation does not leak file descriptors`() = runTest {
        if (!SystemFileSystem.supportsAsyncFileIo()) return@runTest

        withFile(Random(17).nextBytes(10 * 1024 * 1024)) { path ->
            val initialFileDescriptorCount = fileDescriptorCount()
            repeat(100) {
                val channel = SystemFileSystem.byteReadChannel(path)
                val reader = async(Dispatchers.Default) { channel.awaitContent() }
                yield()
                channel.cancel()
                reader.cancel()
                runCatching { reader.await() }
            }

            withTimeout(10_000) {
                while (fileDescriptorCount() != initialFileDescriptorCount) delay(10)
            }
            assertEquals(initialFileDescriptorCount, fileDescriptorCount())
        }
    }

    private suspend fun withFile(content: ByteArray, block: suspend (Path) -> Unit) {
        val path = temporaryPath()
        try {
            SystemFileSystem.sink(path).buffered().use { it.write(content) }
            block(path)
        } finally {
            runCatching { SystemFileSystem.delete(path, mustExist = false) }
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun temporaryPath(): Path = Path(SystemTemporaryDirectory, "ktor-io-uring-${Uuid.random()}")

    private fun fileDescriptorCount(): Int = SystemFileSystem.list(Path("/proc/self/fd")).size
}
