/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package io.ktor.test.dispatcher

import platform.posix.*
import kotlin.native.concurrent.*
import kotlin.system.*
import kotlin.time.Duration.Companion.milliseconds

private var TEST_WORKER: Worker = createTestWorker()
private val SLEEP_TIME: UInt = 10.milliseconds.inWholeMicroseconds.toUInt()

internal fun executeInWorker(timeout: Long, block: () -> Unit) {
    val result = TEST_WORKER.execute(TransferMode.UNSAFE, { block }) {
        it()
    }

    val endTime = getTimeMillis() + timeout
    while (result.state == FutureState.SCHEDULED && endTime > getTimeMillis()) {
        usleep(SLEEP_TIME)
    }

    when (result.state) {
        FutureState.SCHEDULED -> {
            TEST_WORKER.requestTermination(processScheduledJobs = false)
            TEST_WORKER = createTestWorker()
            error("Test is timed out")
        }
        else -> {
            result.consume { }
        }
    }
}

private fun createTestWorker(): Worker = Worker.start(
    name = "Ktor Test Worker",
    errorReporting = true
)
