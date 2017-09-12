package org.jetbrains.ktor.cio.http

import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.*
import kotlinx.coroutines.experimental.io.*
import kotlinx.http.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.request.*
import java.io.*
import java.nio.channels.*
import java.nio.file.*
import kotlin.coroutines.experimental.*

class CIOMultipartData(private val channel: ByteReadChannel,
                       private val headers: kotlinx.http.HttpHeaders,
                       private val formFieldLimit: Int = 65536,
                       private val inMemoryFileUploadLimit: Int = formFieldLimit) : MultiPartData {

    private val events = parseMultipart(channel, headers)

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
                FileChannel.open(tmp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING).use { out ->
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
                return PartData.FormItem(packet.readText().toString(), { part.release() }, CIOHeaders(headers))
            } finally {
                packet.release()
            }
        }
    }

    private fun <F, T> ReceiveChannel<F>.toSequence(transform: suspend (F) -> T) = buildSequence<T> {
        runBlocking {
            while (true) {
                receiveOrNull()?.let { yield(transform(it)) } ?: break
            }
        }
    }
}