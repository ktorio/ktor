/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.files

import io.ktor.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.io.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class IoUringRingTest {
    @Test
    fun `event loop ring processes completions in a batch`() = runTest {
        val driver = FakeIoUringDriver()
        val ring = IoUringRing(driver, 1, entryCount = 8, pollerThread = false)
        var firstSlot = -1
        var secondSlot = -1

        val first = async {
            ring.submitOpen("first", onSlot = { firstSlot = it }, onCompletion = { _, _ -> })
        }
        val second = async {
            ring.submitOpen("second", onSlot = { secondSlot = it }, onCompletion = { _, _ -> })
        }
        runCurrent()

        driver.addCompletion(firstSlot, 10)
        driver.addCompletion(secondSlot, 20)
        ring.processCompletions()

        assertEquals(10, first.await())
        assertEquals(20, second.await())
        assertEquals(2, driver.drainCount)
        ring.close()
    }

    @Test
    fun `closing event loop ring fails pending requests`() = runTest {
        val driver = FakeIoUringDriver()
        val ring = IoUringRing(driver, 1, entryCount = 8, pollerThread = false)
        val pending = async {
            ring.submitOpen("pending", onSlot = {}, onCompletion = { _, _ -> })
        }
        runCurrent()

        ring.close()

        assertEquals(-125, pending.await())
        assertEquals(1, driver.destroyCount)
    }

    @Test
    fun `event loop ring reuses released native read buffers`() {
        val driver = FakeIoUringDriver()
        val ring = IoUringRing(driver, 1, entryCount = 8, pollerThread = false)

        val first = ring.acquireReadBuffer(128 * 1024)
        ring.releaseReadBuffer(first)
        val reused = ring.acquireReadBuffer(128 * 1024)

        assertEquals(first, reused)
        assertEquals(1, driver.allocationCount)
        ring.close()
        ring.releaseReadBuffer(reused)
        assertEquals(1, driver.freeCount)
    }

    private class FakeIoUringDriver : IoUringDriver {
        private val completions = ArrayDeque<Pair<Int, Int>>()
        private var nextAddress = 1L
        var allocationCount: Int = 0
            private set
        var freeCount: Int = 0
            private set
        var drainCount: Int = 0
            private set
        var destroyCount: Int = 0
            private set

        fun addCompletion(slot: Int, result: Int) {
            completions.addLast(slot to result)
        }

        override fun createRing(entries: Int): Long = 1

        override fun destroyRing(ring: Long) {
            destroyCount++
        }

        override fun submitOpen(ring: Long, path: String, slot: Int): Int = 0

        override fun submitRead(ring: Long, fd: Int, address: Long, length: Int, offset: Long, slot: Int): Int = 0

        override fun submitClose(ring: Long, fd: Int, slot: Int): Int = 0

        override fun submitCancel(ring: Long, slot: Int): Int = 0

        override fun submitWake(ring: Long): Int = 0

        override fun waitCompletions(ring: Long, slots: IntArray, results: IntArray): Int = error("Not used")

        override fun drainCompletions(ring: Long, slots: IntArray, results: IntArray): Int {
            drainCount++
            var count = 0
            while (count < slots.size && completions.isNotEmpty()) {
                val (slot, result) = completions.removeFirst()
                slots[count] = slot
                results[count] = result
                count++
            }
            return count
        }

        override fun allocate(size: Int): Long {
            allocationCount++
            return nextAddress++
        }

        override fun free(address: Long) {
            freeCount++
        }

        override fun copyToBuffer(address: Long, length: Int, sink: Buffer) = error("Not used")
    }
}
