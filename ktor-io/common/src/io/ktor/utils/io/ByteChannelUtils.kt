/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io

import kotlinx.coroutines.Job

/**
 * Ensures that when the given job is canceled, the ByteChannel is canceled with the same exception.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.utils.io.attachJob)
 */
public fun ByteChannel.attachJob(job: Job) {
    job.invokeOnCompletion {
        if (it != null) {
            cancel(it)
        }
    }
}

/**
 * Ensures that when the given job is canceled, the ByteChannel is canceled with the same exception.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.utils.io.attachJob)
 */
public fun ByteChannel.attachJob(job: ChannelJob) {
    attachJob(job.job)
}

/**
 * Ensures that when the [WriterJob]'s output channel is canceled, this [ByteReadChannel] is also canceled.
 */
@InternalAPI
public fun ByteReadChannel.attachWriterJob(writerJob: WriterJob) {
    (writerJob.channel as? ByteChannel)?.invokeOnClose { cause ->
        if (cause != null) cancel(cause)
    }
}
