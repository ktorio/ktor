/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.files

import io.ktor.test.*
import io.ktor.utils.io.*
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.io.readByteArray
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class IoUringJvmTest {

    @Test
    fun `loads bundled JNI library on supported Linux architectures`() {
        val osName = System.getProperty("os.name")
        val architecture = System.getProperty("os.arch")
        if (!osName.startsWith("Linux", ignoreCase = true)) return
        if (architecture !in setOf("amd64", "x86_64", "aarch64", "arm64")) return

        assertTrue(NativeLibraryLoader.load())
    }

    @Test
    fun `uses io_uring on Linux when available`() = runTest {
        if (!System.getProperty("os.name").startsWith("Linux", ignoreCase = true)) return@runTest
        if (!SystemFileSystem.supportsAsyncFileIo()) return@runTest

        val content = "io_uring JVM test".encodeToByteArray()
        withFile(content) { path ->
            val channel = SystemFileSystem.byteReadChannel(path)
            assertIs<IoUringByteReadChannel>(channel)
            assertContentEquals(content, channel.readBuffer().readByteArray())
        }
    }

    @Test
    fun `system property disables io_uring`() = runTest {
        val previousValue = System.getProperty(IO_URING_ENABLED_PROPERTY)
        System.setProperty(IO_URING_ENABLED_PROPERTY, "false")
        try {
            assertFalse(SystemFileSystem.supportsAsyncFileIo())
            withFile(byteArrayOf(1)) { path ->
                val channel = SystemFileSystem.byteReadChannel(path)
                assertFalse(channel is IoUringByteReadChannel)
                assertContentEquals(byteArrayOf(1), channel.readBuffer().readByteArray())
            }
        } finally {
            if (previousValue == null) {
                System.clearProperty(IO_URING_ENABLED_PROPERTY)
            } else {
                System.setProperty(IO_URING_ENABLED_PROPERTY, previousValue)
            }
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
    private fun temporaryPath(): Path = Path(SystemTemporaryDirectory, "ktor-io-uring-jvm-${Uuid.random()}")

    private companion object {
        const val IO_URING_ENABLED_PROPERTY = "io.ktor.io.uring.enabled"
    }
}
