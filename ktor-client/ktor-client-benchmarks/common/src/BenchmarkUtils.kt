/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.benchmarks
import io.ktor.client.engine.*
import kotlinx.coroutines.*


internal expect fun <T> runBenchmark(block: suspend CoroutineScope.() -> T)

internal const val TEST_BENCHMARKS_SERVER = "http://127.0.0.1:8080/benchmarks"

expect fun findEngine(name: String): HttpClientEngineFactory<HttpClientEngineConfig>
