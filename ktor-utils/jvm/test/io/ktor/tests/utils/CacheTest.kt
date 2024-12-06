/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.utils

import io.ktor.util.*
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals

class CacheTest {

    @Test
    fun testGet() {
        val counter = AtomicInteger(0)
        val cache = LRUCache<Int, Int>(
            supplier = {
                counter.incrementAndGet()
                it
            },
            close = {},
            maxSize = 2,
        )

        for (i in 0 until 10) {
            assertEquals(i, cache[i])
        }

        assertEquals(10, counter.get())
    }

    @Test
    fun testClose() {
        val counter = AtomicInteger(0)
        val lastRemoved = AtomicInteger(-1)
        val cache = LRUCache<Int, Int>(
            supplier = {
                counter.incrementAndGet()
                it
            },
            close = { lastRemoved.set(it) },
            maxSize = 2,
        )

        for (i in 0 until 10) {
            assertEquals(i, cache[i])
            if (i < 2) {
                assertEquals(-1, lastRemoved.get())
            } else {
                assertEquals(i - 2, lastRemoved.get())
            }
        }

        assertEquals(10, counter.get())
    }

    @Test
    fun testCloseMultithreaded() {
        val created = AtomicInteger(0)
        val closed = AtomicInteger(0)

        val cache = LRUCache<Int, Int>(
            supplier = {
                created.incrementAndGet()
                it
            },
            close = { closed.incrementAndGet() },
            maxSize = 2,
        )

        (1..100).map {
            thread {
                val random = Random(it * 43L)
                repeat(1000) {
                    val key = random.nextInt(1000)
                    assertEquals(key, cache[key])
                }
            }
        }.forEach {
            it.join()
        }

        assertEquals(created.get(), closed.get() + cache.size)
    }

    @Test
    fun testWithoutCaching() {
        val counter = AtomicInteger(0)
        val lastRemoved = AtomicInteger(-1)
        val cache = LRUCache<Int, Int>(
            supplier = {
                counter.incrementAndGet()
                it
            },
            close = { lastRemoved.set(it) },
            maxSize = 0,
        )

        for (i in 0 until 10) {
            assertEquals(i, cache[i])
            assertEquals(-1, lastRemoved.get())
        }

        assertEquals(10, counter.get())
    }
}
