package io.ktor.netty

import io.netty.buffer.*
import io.netty.handler.codec.http.*
import io.netty.handler.codec.http.multipart.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.io.*
import io.ktor.http.*
import io.ktor.http.HttpHeaders
import io.ktor.request.*
import io.ktor.http.response.*
import io.ktor.util.*
import java.util.*

internal class NettyMultiPartData(private val decoder: HttpPostMultipartRequestDecoder, val alloc: ByteBufAllocator, private val channel: ByteReadChannel) : MultiPartData {
    // netty's decoder doesn't provide us headers so we have to parse it or try to reconstruct
    // TODO original headers

    private val all = ArrayList<PartData>()
    private var destroyed = false

    override val parts: Sequence<PartData> by lazy {
        runBlocking(Unconfined) {
            processQueue()
        }
        all.asSequence()
    }

    private suspend fun processQueue() {
        val channel = this.channel
        val alloc = this.alloc

        while (!channel.isClosedForRead) {
            val buf = alloc.buffer(channel.availableForRead.coerceIn(256, 4096))
            val bb = buf.nioBuffer(buf.writerIndex(), buf.writableBytes())
            val rc = channel.readAvailable(bb)

            if (rc == -1) {
                buf.release()
                decoder.offer(DefaultLastHttpContent.EMPTY_LAST_CONTENT)
                break
            }

            buf.writerIndex(rc)
            decoder.offer(DefaultHttpContent(buf))
            buf.release()

            processItems()
        }

        processItems()
    }

    private fun processItems() {
        while (true) {
            val hostPart = decoder.next() ?: break
            val part = convert(hostPart)
            if (part != null) {
                all.add(part)
            }
        }
    }

    internal fun destroy() {
        if (!destroyed) {
            destroyed = true
            decoder.destroy()
            all.forEach {
                it.dispose()
            }
        }
    }

    private fun convert(part: InterfaceHttpData) = when (part) {
        is FileUpload -> PartData.FileItem(
                streamProvider = {
                    when {
                        part.isInMemory -> part.get().inputStream()
                        else -> part.file.inputStream()
                    }
                },
                dispose = { part.delete() },
                partHeaders = part.headers()
        )
        is Attribute -> PartData.FormItem(part.value, { part.delete() }, part.headers())
        else -> null
    }

    private fun FileUpload.headers() = ValuesMap.build(true) {
        if (contentType != null) {
            append(HttpHeaders.ContentType, contentType)
        }
        if (contentTransferEncoding != null) {
            append(HttpHeaders.TransferEncoding, contentTransferEncoding)
        }
        if (filename != null) {
            append(HttpHeaders.ContentDisposition, ContentDisposition.File.withParameters(listOf(
                    HeaderValueParam(ContentDisposition.Parameters.Name, name),
                    HeaderValueParam(ContentDisposition.Parameters.FileName, filename)
            )).toString())
        }
        contentLength(length())
    }

    private fun Attribute.headers() = ValuesMap.build(true) {
        contentType(ContentType.MultiPart.Mixed)
        append(HttpHeaders.ContentDisposition, ContentDisposition.Mixed.withParameter(ContentDisposition.Parameters.Name, name).toString())
    }
}
