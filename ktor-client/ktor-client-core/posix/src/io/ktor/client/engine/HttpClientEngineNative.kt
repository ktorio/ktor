/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.engine

import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

/**
 * Create call context with the specified [parentJob] to be used during call execution in the engine. Call context
 * inherits [coroutineContext], but overrides job and coroutine name so that call job's parent is [parentJob] and
 * call coroutine's name is "call-context".
 */
internal actual suspend fun HttpClientEngine.createCallContext(parentJob: Job): CoroutineContext {
    parentJob.makeShared()
    val callJob = Job(parentJob).apply {
        makeShared()
    }

    attachToUserJob(callJob)

    return (functionContext() + callJob + CALL_COROUTINE).apply {
        makeShared()
    }
}

private suspend inline fun functionContext(): CoroutineContext = coroutineContext
