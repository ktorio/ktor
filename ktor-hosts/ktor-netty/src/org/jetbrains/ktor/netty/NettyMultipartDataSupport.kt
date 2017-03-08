package org.jetbrains.ktor.netty

import io.netty.handler.codec.http.*
import io.netty.handler.codec.http.multipart.*
import kotlinx.coroutines.experimental.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.http.HttpHeaders
import org.jetbrains.ktor.request.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.util.*
import java.util.*

internal class NettyMultiPartData(private val decoder: HttpPostMultipartRequestDecoder, private val queue: NettyContentQueue) : MultiPartData {
    // netty's decoder doesn't provide us headers so we have to parse it or try to reconstruct
    // TODO original headers

    private val all = ArrayList<PartData>()
    private var fetched = false
    private var destroyed = false

    override val parts: Sequence<PartData>
        get() = runBlocking(Unconfined) { parts() }

    suspend fun parts(): Sequence<PartData> {
        if (!fetched)
            processQueue()
        return all.asSequence()
    }

    suspend fun processQueue() {
        while (true) {
            val item = queue.pull() ?: run {
                fetched = true
                return
            }
            processItem(item)
        }

    }

    fun processItem(content: HttpContent) {
        decoder.offer(content)
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
