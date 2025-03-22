/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.sessions

import io.ktor.server.netty.*
import io.ktor.server.sessions.*
import kotlinx.coroutines.*
import java.util.concurrent.atomic.*
import kotlin.test.*

class CacheTest {

    @Test
    fun testTimeout1(): Unit = runBlocking {
        val counter = AtomicInteger()
        val timeout = BaseTimeoutCache(
            1000L,
            true,
            BaseCache<Int, String> {
                counter.incrementAndGet()
                it.toString()
            }
        )

        assertEquals("1", timeout.getOrCompute(1))
        assertEquals(1, counter.get())
        assertEquals("1", timeout.getOrCompute(1))
        assertEquals(1, counter.get())
    }

    @Test
    fun testTimeout2(): Unit = runBlocking {
        val counter = AtomicInteger()
        val timeout = BaseTimeoutCache(
            10L,
            true,
            BaseCache<Int, String> {
                counter.incrementAndGet()
                it.toString()
            }
        )

        assertEquals("1", timeout.getOrCompute(1))
        assertEquals(1, counter.get())

        Thread.sleep(500)

        assertNull(timeout.peek(1))
    }

    @Test
    fun canReadDataFromCacheStorageWithinEventLoopGroupProxy() {
        runBlocking {
            val memoryStorage = SessionStorageMemory()
            memoryStorage.write("id", "123")
            val storage = CacheStorage(memoryStorage, 100)

            val group = EventLoopGroupProxy.create(4)
            group.submit(
                Runnable {
                    runBlocking {
                        assertEquals("123", storage.read("id"))
                    }
                }
            ).sync()
            group.shutdownGracefully().sync()
        }
    }
}
