/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.fatures.retry

import io.ktor.utils.io.*
import kotlinx.coroutines.*

/**
 * Attach client engine job.
 */
internal fun attachToClientEngineJob(
    requestJob: CompletableJob,
    clientEngineJob: Job
) {
    clientEngineJob.makeShared()

    val handler = clientEngineJob.invokeOnCompletion { cause ->
        if (cause != null) {
            requestJob.cancel("Engine failed", cause)
        } else {
            requestJob.complete()
        }
    }

    requestJob.invokeOnCompletion {
        handler.dispose()
    }
}
