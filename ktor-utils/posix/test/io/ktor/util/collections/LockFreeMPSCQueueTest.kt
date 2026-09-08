/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.collections

import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(InternalAPI::class)
class LockFreeMPSCQueueTest {

    @Test
    fun `preserves order when growing and reusing slots`() {
        val queue = LockFreeMPSCQueue<Int>()

        repeat(10) { round ->
            repeat(1000) { index ->
                assertTrue(queue.addLast(round * 1000 + index))
            }
            repeat(1000) { index ->
                assertEquals(round * 1000 + index, queue.removeFirstOrNull())
            }
            assertTrue(queue.isEmpty)
            assertNull(queue.removeFirstOrNull())
        }

        assertTrue(queue.addLast(1))
        assertFalse(queue.isClosed)
        queue.close()
        assertTrue(queue.isClosed)
        assertFalse(queue.addLast(2))
        assertEquals(1, queue.removeFirstOrNull())
        assertNull(queue.removeFirstOrNull())
        assertTrue(queue.isEmpty)
    }

    @Test
    fun `preserves every producer order during concurrent publication`() = runTest {
        val queue = LockFreeMPSCQueue<Int>()
        val producerCount = 4
        val elementsPerProducer = 2000
        val start = CompletableDeferred<Unit>()
        val producers = List(producerCount) { producer ->
            launch(Dispatchers.Default) {
                start.await()
                repeat(elementsPerProducer) { index ->
                    assertTrue(queue.addLast(producer * elementsPerProducer + index))
                }
            }
        }

        val nextElements = IntArray(producerCount)
        var received = 0
        start.complete(Unit)
        while (received < producerCount * elementsPerProducer) {
            val element = queue.removeFirstOrNull()
            if (element == null) {
                yield()
                continue
            }
            val producer = element / elementsPerProducer
            assertEquals(nextElements[producer], element % elementsPerProducer)
            nextElements[producer]++
            received++
        }

        producers.joinAll()
        assertTrue(nextElements.all { it == elementsPerProducer })
        assertTrue(queue.isEmpty)
        assertNull(queue.removeFirstOrNull())
    }
}
