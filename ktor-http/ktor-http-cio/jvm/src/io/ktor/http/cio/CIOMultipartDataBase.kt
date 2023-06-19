/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.http.cio

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.*
import io.ktor.utils.io.*
import io.ktor.utils.io.bits.*
import io.ktor.utils.io.core.*
import io.ktor.utils.io.pool.*
import io.ktor.utils.io.streams.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import java.io.*
import java.nio.*
import kotlin.coroutines.*
import kotlin.io.use

/**
 * Represents a multipart data object that does parse and convert parts to ktor's [PartData]
 */
@InternalAPI
public class CIOMultipartDataBase(
    override val coroutineContext: CoroutineContext,
    channel: ByteReadChannel,
    contentType: CharSequence,
    contentLength: Long?,
    private val formFieldLimit: Int = 65536,
    private val inMemoryFileUploadLimit: Int = formFieldLimit
) : MultiPartData, CoroutineScope {
    private val events: ReceiveChannel<MultipartEvent> = parseMultipart(channel, contentType, contentLength)

    override suspend fun readPart(): PartData? {
        while (true) {
            val event = events.tryReceive().getOrNull() ?: break
            eventToData(event)?.let { return it }
        }

        return readPartSuspend()
    }

    private suspend fun readPartSuspend(): PartData? {
        try {
            while (true) {
                val event = events.receive()
                eventToData(event)?.let { return it }
            }
        } catch (t: ClosedReceiveChannelException) {
            return null
        }
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

        if (filename == null) {
            val packet = part.body.readRemaining(formFieldLimit.toLong()) // TODO fail if limit exceeded

            try {
                return PartData.FormItem(packet.readText(), { part.release() }, CIOHeaders(headers))
            } finally {
                packet.release()
            }
        }

        // file upload
        val buffer = ByteBuffer.allocate(inMemoryFileUploadLimit)
        part.body.readAvailable(buffer)

        val completeRead = if (buffer.remaining() > 0) {
            part.body.readAvailable(buffer) == -1
        } else {
            false
        }

        buffer.flip()

        if (completeRead) {
            val input = ByteArrayInputStream(buffer.array(), buffer.arrayOffset(), buffer.remaining()).asInput()
            return PartData.FileItem({ input }, { part.release() }, CIOHeaders(headers))
        }

        if (System.getProperty("io.ktor.http.content.multipart.skipTempFile") == "true") {
            return withoutTempFile(part, headers, buffer)
        }
        return withTempFile(part, headers, buffer)
    }

    private fun withoutTempFile(
        part: MultipartEvent.MultipartPart,
        headers: HttpHeadersMap,
        buffer: ByteBuffer
    ): PartData {
        var closed = false
        val lazyInput = lazy {
            if (closed) throw IllegalStateException("Already disposed")
            MultipartInput(buffer, part.body)
        }

        return PartData.FileItem(
            { lazyInput.value },
            {
                closed = true
                if (lazyInput.isInitialized()) lazyInput.value.close()
                part.release()
            },
            CIOHeaders(headers)
        )
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    private suspend fun withTempFile(
        part: MultipartEvent.MultipartPart,
        headers: HttpHeadersMap,
        buffer: ByteBuffer
    ): PartData {
        val tmp = File.createTempFile("file-upload", ".tmp")

        try {
            FileOutputStream(tmp).use { stream ->
                stream.channel.use { out ->
                    out.truncate(0L)

                    while (true) {
                        while (buffer.hasRemaining()) {
                            out.write(buffer)
                        }
                        buffer.clear()

                        if (part.body.readAvailable(buffer) == -1) break
                        buffer.flip()
                    }
                }
            }
        } catch (cause: Throwable) {
            tmp.delete()
            throw cause
        }

        var closed = false
        val lazyInput = lazy {
            if (closed) throw IllegalStateException("Already disposed")
            FileInputStream(tmp).asInput()
        }

        return PartData.FileItem(
            { lazyInput.value },
            {
                closed = true
                if (lazyInput.isInitialized()) lazyInput.value.close()
                part.release()
                tmp.delete()
            },
            CIOHeaders(headers)
        )
    }
}

private class MultipartInput(
    private val head: ByteBuffer,
    private val tail: ByteReadChannel
) : Input() {

    override fun fill(destination: Memory, offset: Int, length: Int): Int {
        if (head.hasRemaining()) {
            if (destination.buffer.hasArray() && !destination.buffer.isReadOnly) {
                val rc = minOf(head.remaining(), length)
                head.get(destination.buffer.array(), offset, rc)
                return rc.coerceAtLeast(0)
            }

            val buffer = ByteArrayPool.borrow()
            try {
                val rc = minOf(head.remaining(), length)
                head.get(buffer, 0, rc)
                destination.storeByteArray(offset, buffer, 0, rc)
                return rc
            } finally {
                ByteArrayPool.recycle(buffer)
            }
        }

        return runBlocking {
            val buffer = ByteArrayPool.borrow()
            try {
                val rc = tail.readAvailable(buffer, 0, minOf(length, buffer.size)).coerceAtLeast(0)
                destination.storeByteArray(offset, buffer, 0, rc)
                rc
            } finally {
                ByteArrayPool.recycle(buffer)
            }
        }
    }

    override fun closeSource() {
        tail.cancel()
    }
}
