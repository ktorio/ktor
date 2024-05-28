/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io

import kotlinx.coroutines.*

@Suppress("UnusedReceiverParameter", "UNUSED_PARAMETER")
public fun ByteChannel.attachJob(job: Job) {
    job.invokeOnCompletion {
        if (it != null) {
            cancel(it)
        }
    }
}

public fun ByteChannel.attachJob(job: ChannelJob) {
    attachJob(job.job)
}
