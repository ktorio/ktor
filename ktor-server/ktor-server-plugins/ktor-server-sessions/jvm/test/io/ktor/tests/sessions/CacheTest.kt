/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.sessions

import io.ktor.server.netty.*
import io.ktor.server.sessions.*
import kotlinx.coroutines.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*
import kotlin.test.*

class CacheTest {
    @Test
    fun testBaseSimpleCase(): Unit = runBlocking {
        val counter = AtomicInteger()

        val cache = BaseCache<Int, String> { counter.incrementAndGet(); it.toString() }

        assertEquals("1", cache.getOrCompute(1))
        assertEquals(1, counter.get())

        assertEquals("1", cache.getOrCompute(1))
        assertEquals(1, counter.get())

        assertEquals("2", cache.getOrCompute(2))
        assertEquals(2, counter.get())
    }

    @Test
    fun testBlocking(): Unit = runBlocking {
        val latch = CountDownLatch(1)
        val ref = AtomicReference("")

        val cache = BaseCache<Int, String> { if (it == 0) latch.await(); it.toString() }

        assertEquals("1", cache.getOrCompute(1))
        val th = launch(Dispatchers.Default) {
            ref.set(cache.getOrCompute(0))
        }

        assertEquals("2", cache.getOrCompute(2))

        latch.countDown()
        th.join()

        assertEquals("0", ref.get())
    }

    @Test
    fun testPeek(): Unit = runBlocking {
        val cache = BaseCache<Int, String> { fail(""); }
        assertNull(cache.peek(1))
    }

    @Test
    fun testInvalidate(): Unit = runBlocking {
        val counter = AtomicInteger()
        val cache = BaseCache<Int, String> { counter.incrementAndGet(); it.toString() }

        assertEquals("1", cache.getOrCompute(1))
        assertEquals(1, counter.get())

        cache.invalidate(1)

        assertEquals("1", cache.getOrCompute(1))
        assertEquals(2, counter.get())

        cache.invalidate(1, "shouldn't invalidate here")
        assertEquals("1", cache.getOrCompute(1))
        assertEquals(2, counter.get())

        cache.invalidate(1, "1")
        assertEquals("1", cache.getOrCompute(1))
        assertEquals(3, counter.get())
    }

    @Test
    fun testTimeout1(): Unit = runBlocking {
        val counter = AtomicInteger()
        val timeout = BaseTimeoutCache(
            1000L,
            true,
            BaseCache<Int, String> { counter.incrementAndGet(); it.toString() }
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
            BaseCache<Int, String> { counter.incrementAndGet(); it.toString() }
        )

        assertEquals("1", timeout.getOrCompute(1))
        assertEquals(1, counter.get())

        Thread.sleep(500)

        assertNull(timeout.peek(1))
    }

    @Test
    fun testInvalidateWithError(): Unit = runBlocking {
        class ExpectedException : Exception()

        val counter = AtomicInteger()
        val cache = BaseCache<Int, String> { counter.incrementAndGet(); throw ExpectedException() }

        assertFailsWith<ExpectedException> {
            cache.getOrCompute(1)
        }

        assertEquals(1, counter.get())

        assertFailsWith<ExpectedException> {
            cache.getOrCompute(1)
        }
        assertEquals(1, counter.get())

        cache.invalidate(1)
        assertEquals(1, counter.get())

        assertFailsWith<ExpectedException> {
            cache.getOrCompute(1)
        }
        assertEquals(2, counter.get())
    }

    @Test
    fun testWeakReferenceCache(): Unit = runBlocking {
        var ref: D? = null
        val weak = WeakReferenceCache<Int, D> { ref = D(it); ref!! }

        var value: D? = weak.getOrCompute(1)
        assertEquals(D(1), value)
        assertNotNull(ref)
        assertEquals(D(1), weak.peek(1))

        @Suppress("UNUSED_VALUE")
        value = null // workaround for coroutine holding reference
        ref = null

        System.gc()
        Thread.sleep(100)
        System.gc()
        System.gc()

        assertNull(weak.peek(1))
    }

    private data class D(val i: Int)

    @Test
    fun canReadDataFromCacheStorageWithinEventLoopGroupProxy() {
        runBlocking {
            val memoryStorage = SessionStorageMemory()
            memoryStorage.write("id") { channel -> channel.writeByte(123) }
            val storage = CacheStorage(memoryStorage, 100)

            val group = EventLoopGroupProxy.create(4)
            group.submit(
                Runnable {
                    runBlocking {
                        storage.read("id") { channel ->
                            assertEquals(123, channel.readByte())
                        }
                    }
                }
            ).sync()
            group.shutdownGracefully().sync()
        }
    }
}
