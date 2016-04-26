package org.jetbrains.ktor.netty

import io.netty.channel.*
import io.netty.handler.codec.http.*
import io.netty.handler.codec.http.multipart.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.http.HttpHeaders
import org.jetbrains.ktor.util.*
import java.io.*
import java.util.*
import java.util.concurrent.locks.*
import kotlin.concurrent.*

internal class NettyMultiPartData(val decoder: HttpPostMultipartRequestDecoder, val kRequest: NettyApplicationRequest) : MultiPartData, SimpleChannelInboundHandler<DefaultHttpContent>(true) {
    // netty's decoder doesn't provide us headers so we have to parse it or try to reconstruct
    // TODO original headers

    private val lock = ReentrantLock()
    private val elementPresent = lock.newCondition()
    private val all = ArrayList<PartData>()
    private var completed = false

    override val parts: Sequence<PartData>
        get() = when {
            kRequest.isMultipart() -> object : Sequence<PartData> {
                override fun iterator() = PartIterator()
            }
            else -> throw IOException("The request content is not multipart encoded")
        }

    override fun acceptInboundMessage(msg: Any?): Boolean {
        return super.acceptInboundMessage(msg)
    }

    override fun channelRead0(context: ChannelHandlerContext, msg: DefaultHttpContent) {
        var added = 0

        lock.withLock {
            decoder.offer(msg)

            try {
                while (true) {
                    val hostPart = decoder.next() ?: break
                    val part = convert(hostPart)
                    if (part != null) {
                        all.add(part)
                        added++
                    }
                }

                if (added > 0) {
                    elementPresent.signalAll()
                }
            } catch (e: HttpPostRequestDecoder.EndOfDataDecoderException) {
                completed = true
                elementPresent.signalAll()
            }

            if (msg is LastHttpContent) {
                completed = true
                elementPresent.signalAll()
            }
        }
    }

    override fun channelReadComplete(ctx: ChannelHandlerContext) {
        lock.withLock {
            completed = true
            elementPresent.signalAll()
        }
    }

    private inner class PartIterator : AbstractIterator<PartData>() {
        private var index = 0

        override fun computeNext() {
            lock.withLock {
                while (true) {
                    if (index < all.size) {
                        setNext(all[index])
                        index++
                        break
                    } else if (completed) {
                        done()
                        break
                    } else {
                        elementPresent.await()
                    }
                }
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
        append(HttpHeaders.ContentLength, length().toString())
    }

    private fun Attribute.headers() = ValuesMap.build(true) {
        append(HttpHeaders.ContentType, ContentType.MultiPart.Mixed.toString())
        append(HttpHeaders.ContentDisposition, ContentDisposition.Mixed.withParameter(ContentDisposition.Parameters.Name, name).toString())
    }
}
