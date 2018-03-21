package io.ktor.http.cio

import io.ktor.content.*
import io.ktor.http.*
import io.ktor.http.cio.internals.*
import io.ktor.network.util.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.io.*
import java.io.*
import java.nio.channels.*
import java.nio.file.*
import kotlin.coroutines.experimental.*

class CIOMultipartData(channel: ByteReadChannel,
                       headers: HttpHeadersMap,
                       formFieldLimit: Int = 65536,
                       inMemoryFileUploadLimit: Int = formFieldLimit)
    : CIOMultipartDataBase(
        ioCoroutineDispatcher,
        channel, headers[HttpHeaders.ContentType]!!,
        headers[HttpHeaders.ContentLength]?.parseDecLong(),
        formFieldLimit, inMemoryFileUploadLimit
)

open class CIOMultipartDataBase(
        coroutineContext: CoroutineContext,
        channel: ByteReadChannel,
        contentType: CharSequence,
        contentLength: Long?,
        private val formFieldLimit: Int = 65536,
        private val inMemoryFileUploadLimit: Int = formFieldLimit
) : MultiPartData {
    private val events = parseMultipart(coroutineContext, channel, contentType, contentLength)

    override val parts: Sequence<PartData> = buildSequence {
        while (!events.isClosedForReceive) {
            val transformed = ArrayList<PartData>()

            runBlocking {
                while (true) {
                    val evt = (if (transformed.isEmpty()) events.receiveOrNull() else events.poll()) ?: break
                    val data = eventToData(evt) ?: continue
                    transformed.add(data)
                }
            }

            yieldAll(transformed)
        }
    }

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
            val packet = part.body.readRemaining(formFieldLimit) // TODO fail if limit exceeded
            try {
                return PartData.FormItem(packet.readText(), { part.release() }, CIOHeaders(headers))
            } finally {
                packet.release()
            }
        }
    }
}