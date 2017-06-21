package org.jetbrains.ktor.servlet

import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.request.*
import org.jetbrains.ktor.util.*
import java.io.*
import java.nio.charset.*
import javax.servlet.http.*

internal class ServletMultiPartData(val request: ApplicationRequest, val servletRequest: HttpServletRequest) : MultiPartData {
    override val parts: Sequence<PartData>
        get() = when {
            request.contentType().match(ContentType.MultiPart.FormData) -> servletRequest.parts.asSequence().map {
                when {
                    it.isFormField -> {
                        val charset = it.charset ?: servletRequest.charset()?: Charsets.UTF_8
                        PartData.FormItem(
                                value = it.inputStream.reader(charset).use { it.readText() },
                                dispose = { it.delete() },
                                partHeaders = it.toHeadersMap()
                        )
                    }
                    else -> PartData.FileItem(
                            streamProvider = { it.inputStream!! },
                            dispose = { it.delete() },
                            partHeaders = it.toHeadersMap()
                    )
                }
            }
            request.contentType().match(ContentType.MultiPart.Any) -> throw UnsupportedOperationException("Multipart encoding ${request.contentType()} is not supported by Servlet's implementation")
            else -> throw IOException("The request content is not multipart encoded")
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

