/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.server.plugins.doublereceive.*
import io.ktor.test.*
import io.ktor.utils.io.*
import kotlinx.io.readByteArray
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertContentEquals

class DoubleReceiveTest {

    @Test
    fun testInMemoryCache() = runTest {
        val content = ByteArray(1024 * 1024) { it.toByte() }
        val cache = MemoryCache(
            ByteReadChannel(content),
            EmptyCoroutineContext
        )

        repeat(3) {
            val received = cache.read().readRemaining().readByteArray()
            assertContentEquals(content, received)
        }
    }
}
