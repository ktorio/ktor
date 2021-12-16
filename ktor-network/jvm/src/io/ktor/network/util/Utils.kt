/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.util

import io.ktor.util.date.*
import io.ktor.utils.io.concurrent.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import kotlin.contracts.*
import kotlin.time.*

internal class Timeout(
    private val name: String,
    private val timeout: Duration,
    private val clock: GMTClock,
    private val scope: CoroutineScope,
    private val onTimeout: suspend () -> Unit
) {

    private var lastActivityTime: GMTDate? by atomic(null)
    private val isStarted = atomic(false)

    private var workerJob = initTimeoutJob()

    fun start() {
        lastActivityTime = clock.now()
        isStarted.value = true
    }

    fun stop() {
        isStarted.value = false
    }

    fun finish() {
        workerJob?.cancel()
    }

    private fun initTimeoutJob(): Job? {
        if (timeout == Duration.INFINITE) return null
        return scope.launch(scope.coroutineContext + CoroutineName("Timeout $name")) {
            try {
                while (true) {
                    if (!isStarted.value) {
                        lastActivityTime = clock.now()
                    }
                    val remaining = lastActivityTime!! + timeout - clock.now()
                    if (!remaining.isPositive() && isStarted.value) {
                        break
                    }

                    @OptIn(ExperimentalTime::class)
                    delay(remaining)
                }
                yield()
                onTimeout()
            } catch (cause: Throwable) {
                // no op
            }
        }
    }
}

/**
 * Starts timeout coroutine that will invoke [onTimeout] after [timeout] of inactivity.
 * Use [Timeout] object to wrap code that can timeout or cancel this coroutine
 */
internal fun CoroutineScope.createTimeout(
    name: String = "",
    timeout: Duration,
    clock: GMTClock,
    onTimeout: suspend () -> Unit
): Timeout {
    return Timeout(name, timeout, clock, this, onTimeout)
}

internal inline fun <T> Timeout?.withTimeout(block: () -> T): T {
    if (this == null) {
        return block()
    }

    start()
    return try {
        block()
    } finally {
        stop()
    }
}
