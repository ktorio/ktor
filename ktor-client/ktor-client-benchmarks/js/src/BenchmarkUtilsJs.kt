/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.benchmarks

import kotlinx.coroutines.*

internal actual fun <T> runBenchmark(block: suspend CoroutineScope.() -> T): dynamic = GlobalScope.async<Unit> {
    block()
}.asPromise()
