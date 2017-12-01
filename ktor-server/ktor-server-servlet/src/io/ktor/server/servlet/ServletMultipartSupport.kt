package io.ktor.server.servlet

import io.ktor.content.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.util.*
import java.io.*
import java.nio.charset.*
import javax.servlet.http.*

internal class ServletMultiPartData(val request: ApplicationRequest, val servletRequest: HttpServletRequest) : MultiPartData {
    private val partsIter by lazy { servletRequest.parts.iterator() }

    override val parts: Sequence<PartData>
        get() = when {
            request.contentType().match(ContentType.MultiPart.FormData) -> servletRequest.parts.asSequence().map {
                transformPart(it)
            }
            request.contentType().match(ContentType.MultiPart.Any) -> throw UnsupportedOperationException("Multipart encoding ${request.contentType()} is not supported by Servlet's implementation")
            else -> throw IOException("The request content is not multipart encoded")
        }

    suspend override fun readPart() = if (partsIter.hasNext()) transformPart(partsIter.next()) else null

    private fun transformPart(servletPart: Part): PartData {
        return when {
            servletPart.isFormField -> {
                val charset = servletPart.charset ?: servletRequest.charset()?: Charsets.UTF_8
                PartData.FormItem(
                        value = servletPart.inputStream.reader(charset).use { it.readText() },
                        dispose = { servletPart.delete() },
                        partHeaders = servletPart.toHeadersMap()
                )
            }
            else -> PartData.FileItem(
                    streamProvider = { servletPart.inputStream!! },
                    dispose = { servletPart.delete() },
                    partHeaders = servletPart.toHeadersMap()
            )
        }
    }

    private val Part.isFormField: Boolean
        get() = submittedFileName == null

    private fun Part.toHeadersMap() = ValuesMap.build(true) {
        headerNames.forEach { headerName ->
            appendAll(headerName, getHeaders(headerName))
        }
    }

    private val Part.charset: Charset?
        get() = contentType?.let { ContentType.parse(it).charset() }
                ?: getHeader(HttpHeaders.ContentDisposition)?.let { ContentDisposition.parse(it).charset() }

    private fun HttpServletRequest.charset(): Charset? = characterEncoding?.let { charset(it) }
}

