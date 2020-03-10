/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("KDocMissingDocumentation")

package io.ktor.server.benchmarks.cio

import io.ktor.server.benchmarks.*
import kotlinx.coroutines.*
import org.openjdk.jmh.annotations.*
import org.openjdk.jmh.infra.*
import kotlin.coroutines.*
import kotlin.coroutines.intrinsics.*

private const val REPEAT_COUNT: Int = 1000
private const val MAX_CONSUME_CPU_CYCLE_COUNT: Int = 10
private const val SUSPEND_FACTOR: Int = 100 // every 1/100th should suspend


/**
    Run > ./gradle runBenchmark -PbenchmarkName=cio.CoroutineCancellationBenchmark

    Sample results:
    Benchmark                                                       Mode  Cnt    Score    Error   Units
    CoroutineCancellationBenchmark.customCancellationHandler       thrpt   20  275,802 ± 23,465  ops/ms
    CoroutineCancellationBenchmark.regularCancellableContinuation  thrpt   20   52,506 ±  5,350  ops/ms
 */
@State(Scope.Benchmark)
class CoroutineCancellationBenchmark {

    @Benchmark
    fun regularCancellableContinuation(): Unit = runBlocking {
        var continuation: Continuation<Unit>? = null

        launch {
            while (isActive) {
                val toResume = continuation ?: break
                continuation = null
                toResume.resume(Unit)
                yield()
            }
        }

        repeat(REPEAT_COUNT) { attempt ->
            suspendCancellableCoroutine<Unit> {
                Blackhole.consumeCPU(attempt.toLong() % MAX_CONSUME_CPU_CYCLE_COUNT)
                if (attempt % SUSPEND_FACTOR == 0) {
                    continuation = it
                } else {
                    it.resume(Unit)
                }
            }
        }
    }

    @Benchmark
    @OptIn(InternalCoroutinesApi::class)
    fun customCancellationHandler(): Unit = runBlocking {
        var continuation: Continuation<Unit>? = null

        launch {
            while (isActive) {
                val toResume = continuation ?: break
                continuation = null
                toResume.resume(Unit)
                yield()
            }
        }

        coroutineContext[Job]!!.invokeOnCompletion(true) {
            val toResume = continuation ?: return@invokeOnCompletion
            continuation = null
            toResume.resumeWithException(CancellationException("Job cancelled"))
        }

        repeat(REPEAT_COUNT) { attempt ->
            suspendCoroutineUninterceptedOrReturn<Unit> {
                Blackhole.consumeCPU(attempt.toLong() % MAX_CONSUME_CPU_CYCLE_COUNT)
                if (attempt % SUSPEND_FACTOR == 0) {
                    continuation = it
                    COROUTINE_SUSPENDED
                } else {
                    Unit
                }
            }
        }
    }
}

fun main(args: Array<String>) {
    benchmark(args) {
        threads = 8
        jmhOptions.jvmArgsAppend("-Xmx32m")
        run<CoroutineCancellationBenchmark>()
    }

//    CoroutineCancellationBenchmark().regularCancellableContinuation()
}

