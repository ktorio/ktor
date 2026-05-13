/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.cio

import io.ktor.server.cio.backend.buildBadRequestPacket
import kotlinx.io.Buffer
import kotlinx.io.Segment
import java.lang.reflect.Field
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.fail

class BadRequestPacketSourceTest {

    @Test
    fun `concurrent calls return independent buffers with no shared segment metadata`() {
        val threads = 16
        val iterations = 10_000
        val executor = Executors.newFixedThreadPool(threads)
        try {
            for (iteration in 0 until iterations) {
                val barrier = CyclicBarrier(threads)
                val futures = (0 until threads).map {
                    executor.submit<Buffer> {
                        barrier.await()
                        buildBadRequestPacket() as Buffer
                    }
                }
                val results = futures.map { it.get(30, TimeUnit.SECONDS) }

                val seenHeads = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())
                results.forEachIndexed { idx, buf ->
                    val head = SegmentReflection.headOf(buf)
                    assertNotNull(head, "iteration $iteration source[$idx] missing head segment")
                    assertNull(
                        SegmentReflection.copyTrackerOf(head),
                        "iteration $iteration source[$idx]: expected fresh segment but copyTracker " +
                            "is non-null. Indicates segments are being shared across calls — regression " +
                            "of the BadRequestPacket buffer.copy() data race.",
                    )
                    seenHeads.add(head)
                }

                if (seenHeads.size != threads) {
                    fail(
                        "iteration $iteration: expected $threads distinct head segments across " +
                            "concurrent buildBadRequestPacket() calls, got ${seenHeads.size}. " +
                            "Calls are sharing underlying buffers.",
                    )
                }
            }
        } finally {
            executor.shutdown()
            executor.awaitTermination(10, TimeUnit.SECONDS)
        }
    }
}

private object SegmentReflection {
    private val headField: Field =
        Buffer::class.java.getDeclaredField("head").apply { isAccessible = true }
    private val copyTrackerField: Field =
        Segment::class.java.getDeclaredField("copyTracker").apply { isAccessible = true }

    fun headOf(buffer: Buffer): Segment? = headField.get(buffer) as Segment?
    fun copyTrackerOf(segment: Segment): Any? = copyTrackerField.get(segment)
}
