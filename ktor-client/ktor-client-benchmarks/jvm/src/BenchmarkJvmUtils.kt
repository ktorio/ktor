/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.benchmarks

import io.ktor.client.engine.*
import io.ktor.client.engine.android.*
import io.ktor.client.engine.apache.*
import io.ktor.client.engine.cio.*
import io.ktor.client.engine.jetty.*
import io.ktor.client.engine.okhttp.*
import kotlinx.coroutines.*

internal actual fun <T> runBenchmark(block: suspend CoroutineScope.() -> T): Unit = runBlocking {
    block()

    Unit
}

actual fun findEngine(name: String): HttpClientEngineFactory<HttpClientEngineConfig> = when (name) {
    "Apache" -> Apache
    "OkHttp" -> OkHttp
    "Android" -> Android
    "CIO" -> CIO
    "Jetty" -> Jetty
    else -> error("")
}
