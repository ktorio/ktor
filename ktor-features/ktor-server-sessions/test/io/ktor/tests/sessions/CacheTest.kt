package io.ktor.tests.sessions

import kotlinx.coroutines.*
import io.ktor.sessions.*
import org.junit.Test
import java.util.concurrent.*
import java.util.concurrent.atomic.*
import kotlin.test.*

class CacheTest {
    @Test
    fun testBaseSimpleCase() = runBlocking {
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
    fun testBlocking() = runBlocking {
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
    fun testPeek() = runBlocking {
        val cache = BaseCache<Int, String> { fail(""); }
        assertNull(cache.peek(1))
    }

    @Test
    fun testInvalidate() = runBlocking {
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
    fun testTimeout() = runBlocking {
        val counter = AtomicInteger()
        val timeout = BaseTimeoutCache(100L, true, BaseCache<Int, String> { counter.incrementAndGet(); it.toString() })

        assertEquals("1", timeout.getOrCompute(1))
        assertEquals(1, counter.get())
        assertEquals("1", timeout.getOrCompute(1))
        assertEquals(1, counter.get())

        Thread.sleep(300)
        assertNull(timeout.peek(1))
    }

    @Test
    fun testWeakReferenceCache() = runBlocking {
        var ref: D? = null
        val weak = WeakReferenceCache<Int, D> { ref = D(it); ref!! }

        var value : D? = weak.getOrCompute(1)
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

}
