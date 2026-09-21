/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.server.plugins.doublereceive.*
import io.ktor.server.request.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.io.*
import kotlin.coroutines.*
import kotlin.test.*

class DoubleReceiveTestJvm {

    @Test
    fun testFileCache() = runBlocking {
        val content = ByteArray(1024 * 1024) { it.toByte() }
        val cache = FileCache(
            ByteReadChannel(content),
            4096,
            EmptyCoroutineContext
        )

        val first = cache.read()
        assertFailsWith<RequestAlreadyConsumedException> { cache.read() }
        assertContentEquals(content, first.readBuffer().readByteArray())

        val second = cache.read()
        assertFailsWith<RequestAlreadyConsumedException> { cache.read() }
        second.cancel()

        assertContentEquals(content, cache.read().readBuffer().readByteArray())
        cache.dispose()
    }
}
