/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package io.ktor.test.dispatcher

import io.ktor.util.*
import kotlinx.cinterop.*
import platform.posix.*
import kotlin.native.concurrent.*
import kotlin.system.*
import kotlin.time.Duration.Companion.milliseconds

private var TEST_WORKER: Worker? = null
private val SLEEP_TIME: UInt = 10.milliseconds.inWholeMicroseconds.toUInt()

@OptIn(InternalAPI::class)
internal fun executeInWorker(timeout: Long, block: () -> Unit) {
    if (TEST_WORKER == null) {
        createTestWorker()
    }

    val result = TEST_WORKER!!.execute(TransferMode.SAFE, { block }) {
        it()
    }

    val endTime = getTimeMillis() + timeout
    while (result.state == FutureState.SCHEDULED && endTime > getTimeMillis()) {
        usleep(SLEEP_TIME)
    }

    when (result.state) {
        FutureState.SCHEDULED -> {
            ThreadInfo.printAllStackTraces()
            restartTestWorker()
            error("Test is timed out")
        }
        else -> {
            result.consume { }
        }
    }
}

@OptIn(InternalAPI::class)
private fun createTestWorker() {
    val worker = Worker.start(
        name = "Ktor Test Worker",
        errorReporting = true
    )

    worker.execute(TransferMode.SAFE, { }) {
        ThreadInfo.registerCurrentThread()
    }.consume { }

    TEST_WORKER = worker
}

private fun restartTestWorker() {
    TEST_WORKER!!.requestTermination(processScheduledJobs = false).consume {  }
    createTestWorker()
}
