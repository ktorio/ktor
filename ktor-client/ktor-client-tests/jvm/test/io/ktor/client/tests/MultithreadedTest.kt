/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.tests

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.tests.utils.*
import kotlinx.coroutines.*
import java.util.concurrent.*
import kotlin.test.*
import kotlin.time.Duration.Companion.minutes

private const val TEST_SIZE = 100_000
private const val DEFAULT_THREADS_COUNT = 32

class MultithreadedTest : ClientLoader(10.minutes) {
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
