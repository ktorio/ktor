/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc.utils

import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.measureTime

/**
 * A helper utility for executing periodic tasks in tests.
 *
 * Executes the given [onTick] function at regular [interval] periods,
 * automatically compensating for execution time to maintain consistent timing.
 * Uses a dedicated IO dispatcher scope for non-blocking operation.
 *
 * @param interval The duration between tick executions (must be positive)
 * @param onTick The suspend function to execute on each tick
 */
class Ticker(
    private val interval: Duration,
    private val onTick: suspend () -> Unit,
) {
    private val scope = CoroutineScope(context = SupervisorJob() + Dispatchers.IO)
    private var isActiveAtomic = atomic(false)

    /**
     * Whether the ticker is currently running.
     */
    val isActive: Boolean
        get() = isActiveAtomic.value

    init {
        require(interval.isPositive()) { "Interval must be positive" }
    }

    /**
     * Starts the ticker if not already running.
     *
     * @return The ticker job if started successfully, or null if already running
     */
    fun start(): Job? {
        if (!isActiveAtomic.compareAndSet(expect = false, update = true)) {
            return null
        }
        return scope.launch {
            while (isActive) {
                val calculationTime = measureTime { onTick() }
                val duration = interval.minus(calculationTime)
                if (duration.isPositive() && isActive) {
                    delay(duration)
                }
            }
        }
    }

    /**
     * Stops the ticker if currently running.
     * Cancels all child coroutines and prevents further tick executions.
     */
    fun stop() {
        if (!isActiveAtomic.compareAndSet(expect = true, update = false)) {
            return
        }
        scope.coroutineContext.cancelChildren()
    }
}
