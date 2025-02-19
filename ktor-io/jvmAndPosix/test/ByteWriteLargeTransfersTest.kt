/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ReaderJob
import io.ktor.utils.io.copyAndClose
import io.ktor.utils.io.discard
import io.ktor.utils.io.reader
import io.ktor.utils.io.writeBuffer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.io.RawSource
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlin.getValue
import kotlin.test.Test

private const val KB = 1024L
private const val GB = KB * KB * KB

class ByteWriteLargeTransfersTest {

    // does not exist on windows systems
    private val randomBytesFile by lazy {
        try {
            Path("/dev/random").takeIf { kotlinx.io.files.SystemFileSystem.exists(it) }
        } catch (_: Throwable) {
            null
        }
    }

    private fun CoroutineScope.oneBillionBytes(): ReaderJob =
        reader {
            var count = 0L
            while (!channel.isClosedForRead && count < GB) {
                channel.discard(KB)
                count += KB
            }
        }

    private fun readFromRandomBytesFile(): RawSource =
        kotlinx.io.files.SystemFileSystem.source(randomBytesFile!!)

    @Test
    fun writeBuffer() = runTest {
        if (randomBytesFile == null) return@runTest

        val reader = oneBillionBytes()
        val writeJob = launch {
            readFromRandomBytesFile().use { source ->
                reader.channel.writeBuffer(source)
            }
        }

        reader.job.join()
        writeJob.cancel()
    }

    @Test
    fun copyTo() = runTest {
        if (randomBytesFile == null) return@runTest

        val reader = oneBillionBytes()
        val writeJob = launch {
            readFromRandomBytesFile().use { source ->
                ByteReadChannel(source.buffered())
                    .copyAndClose(reader.channel)
            }
        }

        reader.job.join()
        writeJob.cancel()
    }
}
