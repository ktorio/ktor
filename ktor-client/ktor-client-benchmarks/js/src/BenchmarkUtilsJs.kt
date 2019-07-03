/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.benchmarks

import io.ktor.client.engine.*
import kotlinx.coroutines.*

internal actual fun <T> runBenchmark(block: suspend CoroutineScope.() -> T): dynamic = GlobalScope.async<Unit> {
    block()
}.asPromise()

actual fun findEngine(name: String): HttpClientEngineFactory<HttpClientEngineConfig> {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
}
