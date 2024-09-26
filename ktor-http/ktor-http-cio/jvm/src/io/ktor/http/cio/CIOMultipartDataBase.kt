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

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun readPart(): PartData? {
        try {
            previousPart?.dispose?.invoke()

            while (!events.isEmpty) {
                val event = events.receive()
                eventToData(event)?.let {
                    previousPart = it
                    return it
                }
            }
        } catch (_: ClosedReceiveChannelException) {}

        return null
    }

    private suspend fun eventToData(event: MultipartEvent): PartData? {
        return try {
            when (event) {
                is MultipartEvent.MultipartPart -> partToData(event)
                else -> {
                    event.release()
                    null
                }
            }
        } catch (cause: Throwable) {
            event.release()
            throw cause
        }
    }

    private suspend fun partToData(part: MultipartEvent.MultipartPart): PartData {
        val headers = part.headers.await()

        val contentDisposition = headers["Content-Disposition"]?.let { ContentDisposition.parse(it.toString()) }
        val filename = contentDisposition?.parameter("filename")

        val body = part.body
        if (filename == null) {
            val packet = body.readRemaining() // formFieldLimit.toLong())
//            if (!body.exhausted()) {
//                val cause = IllegalStateException("Form field size limit exceeded: $formFieldLimit")
//                body.cancel(cause)
//                throw cause
//            }

            packet.use {
                return PartData.FormItem(it.readText(), { part.release() }, CIOHeaders(headers))
            }
        }

        return PartData.FileItem(
            { part.body },
            { part.release() },
            CIOHeaders(headers)
        )
    }
}
