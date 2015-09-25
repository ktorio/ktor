package org.jetbrains.ktor.servlet

import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.util.*
import javax.servlet.http.*

internal class ServletMultiPartData(val request: HttpServletRequest) : MultiPartData {
    override val parts: Sequence<PartData>
        get() = when {
            isMultipart -> request.parts.asSequence().map {
                when {
                    it.isFormField -> PartData.FormItem(
                            value = it.inputStream.reader(it.charset ?: request.characterEncoding ?: "ISO-8859-1").use { it.readText() },
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
            else -> emptySequence()
        }

    override val isMultipart: Boolean
        get() = request.getHeaders(HttpHeaders.ContentType)?.toList()?.all { ContentType.parse(it).match(ContentType.MultiPart.FormData) } ?: false

    private val Part.isFormField: Boolean
        get() = submittedFileName == null

    private fun Part.toHeadersMap() = ValuesMap.build(true) {
        headerNames.forEach { headerName ->
            appendAll(headerName, getHeaders(headerName))
        }
    }

    private val Part.charset: String?
        get() = contentType?.let { ContentType.parse(it).parameter("charset") }
                ?: getHeader(HttpHeaders.ContentDisposition)?.let { ContentDisposition.parse(it).parameters["charset"] }
}

