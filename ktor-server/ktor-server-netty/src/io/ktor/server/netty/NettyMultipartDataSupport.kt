package io.ktor.server.netty

import io.ktor.http.content.*
import io.ktor.http.*
import io.ktor.http.HttpHeaders
import io.netty.buffer.*
import io.netty.handler.codec.http.*
import io.netty.handler.codec.http.multipart.*
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder.*
import kotlinx.coroutines.experimental.io.*
import java.util.*

internal class NettyMultiPartData(private val decoder: HttpPostMultipartRequestDecoder, val alloc: ByteBufAllocator, private val channel: ByteReadChannel) : MultiPartData {
    // netty's decoder doesn't provide us headers so we have to parse it or try to reconstruct

    private val all = ArrayList<PartData>()

    private var processed = false
    private var destroyed = false

    override tailrec suspend fun readPart(): PartData? {
        if (processed || destroyed) return null

        val data = decodeNextOrNull()
        if (data != null) {
            val part = convert(data)
            if (part == null) {
                data.release()
                return readPart()
            }
            all.add(part)
            return part
        } else if (channel.isClosedForRead) {
            processed = true
            return null
        }

        return readNextSuspend()
    }

    private suspend fun readNextSuspend(): PartData? {
        do {
            if (!doDecode() || hasNextToDecode()) {
                return readPart()
            }
        } while (true)
    }

    private fun decodeNextOrNull(): InterfaceHttpData? {
        return try { decoder.next() } catch (t: EndOfDataDecoderException) { null }
    }

    private fun hasNextToDecode() = try { decoder.hasNext() } catch (t: EndOfDataDecoderException) { false }

    private suspend fun doDecode(): Boolean {
        val channel = this.channel
        if (channel.isClosedForRead) {
            decoder.offer(DefaultLastHttpContent.EMPTY_LAST_CONTENT)
            return false
        }

        val buf = alloc.buffer(channel.availableForRead.coerceIn(256, 4096))
        val bb = buf.nioBuffer(buf.writerIndex(), buf.writableBytes())
        val rc = channel.readAvailable(bb)

        if (rc == -1) {
            buf.release()
            decoder.offer(DefaultLastHttpContent.EMPTY_LAST_CONTENT)
            return false
        }

        buf.writerIndex(rc)
        decoder.offer(DefaultHttpContent(buf))
        buf.release()
        return true
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

    private fun FileUpload.headers() = Headers.build {
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

    private fun Attribute.headers() = Headers.build {
        contentType(ContentType.MultiPart.Mixed)
        append(HttpHeaders.ContentDisposition, ContentDisposition.Mixed.withParameter(ContentDisposition.Parameters.Name, name).toString())
    }
}
