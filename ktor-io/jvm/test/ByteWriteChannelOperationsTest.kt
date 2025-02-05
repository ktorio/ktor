/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.utils.io.discard
import io.ktor.utils.io.reader
import io.ktor.utils.io.writeBuffer
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlin.test.Test

private const val KB = 1024L
private const val GB = KB * KB * KB

class ByteWriteChannelOperationsTest {

    @Test
    fun writeSource() = runTest {
        val randomBytes = Path("/dev/random")
        val randomBytesFileExists = SystemFileSystem.exists(randomBytes)
        if (randomBytesFileExists) return@runTest

        val reader = reader {
            var count = 0L
            while (!channel.isClosedForRead && count < GB) {
                channel.discard(KB)
                count += KB
            }
        }

        val writeRandomBytesJob = launch {
            SystemFileSystem.source(randomBytes).use { source ->
                reader.channel.writeBuffer(source)
            }
        }

        reader.job.join()
        writeRandomBytesJob.cancel()
    }
}
