package org.jetbrains.ktor.netty

import io.netty.handler.codec.http.*
import io.netty.handler.codec.http.multipart.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.http.HttpHeaders
import org.jetbrains.ktor.util.*
import java.io.*
import kotlin.support.*


private class MultipartIterator(val decoder: HttpPostMultipartRequestDecoder) : AbstractIterator<InterfaceHttpData>() {
    constructor(request: HttpRequest) : this(HttpPostMultipartRequestDecoder(request))

    override fun computeNext() {
        if (decoder.hasNext()) {
            setNext(decoder.next())
        } else {
            done()
        }
    }
}

private class MultipartSequenceFull(val fullRequest: FullHttpRequest) : Sequence<InterfaceHttpData> {
    override fun iterator(): Iterator<InterfaceHttpData> = MultipartIterator(fullRequest)
}

internal class NettyMultiPartData(val kRequest: ApplicationRequest, val request: FullHttpRequest) : MultiPartData {
    // netty's decoder doesn't provide us headers so we have to parse it or try to reconstruct
    // as far as we use FullHttpRequest we probably shouldn't use netty with multipart uploads at all
    // TODO original headers

    override val parts: Sequence<PartData>
        get() = when {
            kRequest.isMultipart() -> MultipartSequenceFull(request).map {
                when (it) {
                    is FileUpload -> PartData.FileItem(
                            streamProvider = {
                                when {
                                    it.isInMemory -> it.get().inputStream()
                                    else -> it.file.inputStream()
                                }
                            },
                            dispose = { it.delete() },
                            partHeaders = it.headers()
                    )
                    is Attribute -> PartData.FormItem(it.value, { it.delete() }, it.headers())
                    else -> null
                }
            }.filterNotNull()
            else -> throw IOException("The request content is not multipart encoded")
        }

    private fun FileUpload.headers() = ValuesMap.build(true) {
        if (contentType != null) {
            append(HttpHeaders.ContentType, contentType)
        }
        if (contentTransferEncoding != null) {
            append(HttpHeaders.TransferEncoding, contentTransferEncoding)
        }
        if (filename != null) {
            append(HttpHeaders.ContentDisposition, ContentDisposition.File.withParameters(ValuesMap.build {
                append("name", name)
                append("filename", filename)
            }).toString())
        }
        append(HttpHeaders.ContentLength, length().toString())
    }

    private fun Attribute.headers() = ValuesMap.build(true) {
        append(HttpHeaders.ContentType, ContentType.MultiPart.Mixed.toString())
        append(HttpHeaders.ContentDisposition, ContentDisposition.Mixed.withParameters(valuesOf("name" to listOf(name))).toString())
    }
}
