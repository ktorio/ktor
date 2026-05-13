/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.http.cio

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlin.coroutines.*

/**
 * Represents a multipart data object that does parse and convert parts to ktor's [PartData]
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.cio.CIOMultipartDataBase)
 */
@InternalAPI
public class CIOMultipartDataBase(
    override val coroutineContext: CoroutineContext,
    channel: ByteReadChannel,
    contentType: CharSequence,
    contentLength: Long?,
    formFieldLimit: Long = 65536,
) : MultiPartData, CoroutineScope {
    // keep a reference to the previous part, so that we can
    // close the body if the next is retrieved without reading
    private var previousPart: PartData? = null

    private val events: ReceiveChannel<MultipartEvent> =
        parseMultipart(channel, contentType, contentLength, formFieldLimit)

    override suspend fun readPart(): PartData? {
        previousPart?.release()

        while (true) {
            val event = events.tryReceive().getOrNull() ?: break
            eventToData(event)?.let {
                previousPart = it
                return it
            }
        }

        return readPartSuspend()
    }

    private suspend fun readPartSuspend(): PartData? {
        try {
            while (true) {
                val event = events.receive()
                eventToData(event)?.let { return it }
            }
        } catch (_: ClosedReceiveChannelException) {
            return null
        }
    }

    private suspend fun eventToData(event: MultipartEvent): PartData? {
        return try {
            when (event) {
                is MultipartEvent.MultipartPart -> partToData(event)
                else -> {
                    event.releaseSuspend()
                    null
                }
            }
        } catch (cause: Throwable) {
            event.releaseSuspend()
            throw cause
        }
    }

    private suspend fun partToData(part: MultipartEvent.MultipartPart): PartData {
        val rawHeaders = part.headers.await()
        // Snapshot headers into a non-pooled instance so that PartData remains
        // valid after MultipartPart.releaseSuspend() recycles the pooled HttpHeadersMap.
        val partHeaders = rawHeaders.snapshot()

        val contentDisposition = partHeaders[HttpHeaders.ContentDisposition]?.let { ContentDisposition.parse(it) }
        val filename = contentDisposition?.parameter("filename")

        val body = part.body
        if (filename == null) {
            val packet = body.readRemaining()
            packet.use {
                return PartData.FormItem(it.readText(), part::release, partHeaders, part::releaseSuspend)
            }
        }

        return PartData.FileItem(
            { part.body },
            part::release,
            partHeaders,
            part::releaseSuspend
        )
    }

    private fun HttpHeadersMap.snapshot(): Headers = Headers.build {
        for (offset in offsets()) {
            append(nameAtOffset(offset).toString(), valueAtOffset(offset).toString())
        }
    }
}
