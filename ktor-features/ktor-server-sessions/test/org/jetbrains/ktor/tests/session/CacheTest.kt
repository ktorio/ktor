package org.jetbrains.ktor.tests.session

import org.jetbrains.ktor.sessions.*
import org.junit.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*
import kotlin.concurrent.*
import kotlin.test.*

class CacheTest {
    @Test
    fun testBaseSimpleCase() {
        val counter = AtomicInteger()

        val cache = BaseCache<Int, String> { counter.incrementAndGet(); it.toString() }

        assertEquals("1", cache[1])
        assertEquals(1, counter.get())

        assertEquals("1", cache[1])
        assertEquals(1, counter.get())

        assertEquals("2", cache[2])
        assertEquals(2, counter.get())
    }

    @Test
    fun testBlocking() {
        val latch = CountDownLatch(1)
        val ref = AtomicReference("")

        val cache = BaseCache<Int, String> { if (it == 0) latch.await(); it.toString() }

        assertEquals("1", cache[1])
        val th = thread {
            ref.set(cache[0])
        }

        assertEquals("2", cache[2])

        latch.countDown()
        th.join()

        assertEquals("0", ref.get())
    }

    @Test
    fun testPeek() {
        val cache = BaseCache<Int, String> { fail(""); }
        assertNull(cache.peek(1))
    }

    @Test
    fun testInvalidate() {
        val counter = AtomicInteger()
        val cache = BaseCache<Int, String> { counter.incrementAndGet(); it.toString() }

        assertEquals("1", cache[1])
        assertEquals(1, counter.get())

        cache.invalidate(1)

        assertEquals("1", cache[1])
        assertEquals(2, counter.get())

        cache.invalidate(1, "shouldnt invalidate here")
        assertEquals("1", cache[1])
        assertEquals(2, counter.get())

        cache.invalidate(1, "1")
        assertEquals("1", cache[1])
        assertEquals(3, counter.get())
    }

    @Test
    fun testTimeout() {
        val counter = AtomicInteger()
        val timeout = BaseTimeoutCache(100L, true, true, BaseCache<Int, String> { counter.incrementAndGet(); it.toString() })

        assertEquals("1", timeout[1])
        assertEquals(1, counter.get())
        assertEquals("1", timeout[1])
        assertEquals(1, counter.get())

        Thread.sleep(300)
        assertNull(timeout.peek(1))
    }

    @Test
    fun testWeakReferenceCache() {
        var ref: D? = null
        val weak = WeakReferenceCache<Int, D> { ref = D(it); ref!! }

        assertEquals(D(1), weak[1])
        assertNotNull(ref)
        assertEquals(D(1), weak.peek(1))
        ref = null

        System.gc()
        Thread.sleep(100)
        System.gc()
        System.gc()

        assertNull(weak.peek(1))
    }

    private data class D(val i: Int)

}
