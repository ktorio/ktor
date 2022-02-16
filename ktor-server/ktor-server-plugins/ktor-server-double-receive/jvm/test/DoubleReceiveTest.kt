import io.ktor.server.plugins.doublereceive.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import kotlin.coroutines.*

/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

class DoubleReceiveTest {

    @Test
    fun testFileCache() = runBlocking {
        val content = ByteArray(16 * 1024 * 1024) { it.toByte() }
        val cache = FileCache(
            ByteReadChannel(content),
            4096,
            EmptyCoroutineContext
        )

        repeat(3) {
            val received = cache.read().readRemaining().readBytes()
            assertArrayEquals(content, received)
        }
    }

    @Test
    fun testInMemoryCache() = runBlocking {
        val content = ByteArray(16 * 1024 * 1024) { it.toByte() }
        val cache = MemoryCache(
            ByteReadChannel(content),
            EmptyCoroutineContext
        )

        repeat(3) {
            val channel = cache.read()
            val received = channel.readRemaining().readBytes()
            assertArrayEquals(content, received)
        }
    }
}
