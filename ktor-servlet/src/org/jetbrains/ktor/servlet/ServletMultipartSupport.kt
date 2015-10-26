package org.jetbrains.ktor.servlet

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.util.*
import java.io.*
import javax.servlet.http.*

internal class ServletMultiPartData(val kRequest: ApplicationRequest, val request: HttpServletRequest) : MultiPartData {
    override val parts: Sequence<PartData>
        get() = when {
            kRequest.contentType().match(ContentType.MultiPart.FormData) -> request.parts.asSequence().map {
                when {
                    it.isFormField -> PartData.FormItem(
                            value = it.inputStream.reader(it.charset ?: request.characterEncoding ?: Charsets.UTF_8.name()).use { it.readText() },
                            dispose = { it.delete() },
                            partHeaders = it.toHeadersMap()
                    )
                    else -> PartData.FileItem(
                            streamProvider = { it.inputStream!! },
                            dispose = { it.delete() },
                            partHeaders = it.toHeadersMap()
                    )
                }
            }
            kRequest.contentType().match(ContentType.MultiPart.Any) -> throw UnsupportedOperationException("Multipart encoding ${kRequest.contentType()} is not supported by Servlet's implementation")
            else -> throw IOException("The request content is not multipart encoded")
        }

    private val Part.isFormField: Boolean
        get() = submittedFileName == null

    private fun Part.toHeadersMap() = ValuesMap.build(true) {
        headerNames.forEach { headerName ->
            appendAll(headerName, getHeaders(headerName))
        }
    }

    private val Part.charset: String?
        get() = contentType?.let { ContentType.parse(it).parameter("charset") }
                ?: getHeader(HttpHeaders.ContentDisposition)?.let { ContentDisposition.parse(it).parameter("charset") }
}

