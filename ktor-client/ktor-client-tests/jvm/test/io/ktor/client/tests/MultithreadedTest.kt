/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.test.base.*
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

private const val TEST_SIZE = 100_000
private const val DEFAULT_THREADS_COUNT = 32

class MultithreadedTest : ClientLoader(timeout = 10.minutes) {
    @Test
    @Ignore
    fun numberTest() = clientTests {
        config {
            engine {
                pipelining = true
            }
        }
        test { client ->
            val result = withPool {
                client.get("$TEST_SERVER/multithreaded").body<Int>()
            }.toSet().size

            assertEquals(TEST_SIZE, result)
        }
    }
}

private fun <T> withPool(
    threads: Int = DEFAULT_THREADS_COUNT,
    testSize: Int = TEST_SIZE,
    block: suspend () -> T
): List<T> {
    val pool = Executors.newFixedThreadPool(threads)
    val result = List(testSize) {
        pool.submit(
            Callable<T> {
                runBlocking { block() }
            }
        )
    }.map { it.get() }

    pool.shutdown()
    assertTrue(pool.awaitTermination(1, TimeUnit.SECONDS))
    return result
}
