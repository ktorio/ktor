package io.ktor.client.benchmarks

import kotlinx.coroutines.*

internal fun <T> runBenchmark(block: suspend CoroutineScope.() -> T): Unit = runBlocking<Unit> {
    block()
}
