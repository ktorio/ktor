package io.ktor.http.cio

import io.ktor.http.content.*
import io.ktor.http.*
import kotlinx.coroutines.experimental.io.*
import java.io.*
import java.nio.*
import java.nio.channels.*
import java.nio.file.*
import kotlin.coroutines.experimental.*

open class CIOMultipartDataBase(
        coroutineContext: CoroutineContext,
        channel: ByteReadChannel,
        contentType: CharSequence,
        contentLength: Long?,
        private val formFieldLimit: Int = 65536,
        private val inMemoryFileUploadLimit: Int = formFieldLimit
) : MultiPartData {
    private val events = parseMultipart(coroutineContext, channel, contentType, contentLength)

    override suspend fun readPart(): PartData? {
        while (true) {
            val event = events.receiveOrNull() ?: return null
            val part = eventToData(event)
            if (part != null) {
                return part
            }
        }
    }

    private suspend fun eventToData(evt: MultipartEvent): PartData? {
        return try {
            when (evt) {
                is MultipartEvent.MultipartPart -> partToData(evt)
                else -> {
                    evt.release()
                    null
                }
            }
        } catch (t: Throwable) {
            evt.release()
            throw t
        }
    }

    private suspend fun partToData(part: MultipartEvent.MultipartPart): PartData {
        val headers = part.headers.await()

        val contentDisposition = headers["Content-Disposition"]?.let { ContentDisposition.parse(it.toString()) }
        val filename = contentDisposition?.parameter("filename")

        if (filename != null) {
            // file upload
            val buffer = ByteBuffer.allocate(inMemoryFileUploadLimit)
            part.body.readAvailable(buffer)

            val completeRead = if (buffer.remaining() > 0) {
                part.body.readAvailable(buffer) == -1
            } else false

            buffer.flip()

            if (!completeRead) {
                val tmp = Files.createTempFile("file-upload-", ".tmp")
                FileChannel.open(tmp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE).use { out ->
                    while (true) {
                        while (buffer.hasRemaining()) {
                            out.write(buffer)
                        }
                        buffer.clear()

                        if (part.body.readAvailable(buffer) == -1) break
                        buffer.flip()
                    }
                }

                return PartData.FileItem({ FileInputStream(tmp.toFile()) }, { Files.deleteIfExists(tmp); part.release() }, CIOHeaders(headers))
            } else {
                val bis = ByteArrayInputStream(buffer.array(), buffer.arrayOffset(), buffer.remaining())
                return PartData.FileItem({ bis }, { part.release() }, CIOHeaders(headers))
            }
        } else {
            val packet = part.body.readRemaining(formFieldLimit.toLong()) // TODO fail if limit exceeded
            try {
                return PartData.FormItem(packet.readText(), { part.release() }, CIOHeaders(headers))
            } finally {
                packet.release()
            }
        }
    }
}