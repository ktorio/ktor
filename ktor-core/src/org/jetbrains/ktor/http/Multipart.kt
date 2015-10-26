package org.jetbrains.ktor.http

import org.jetbrains.ktor.util.*
import java.io.*

class ContentDisposition(val disposition: String, parameters: List<HeaderValueParam> = emptyList()) : HeaderValueWithParameters(disposition, parameters) {
    val name: String?
        get() = parameter(Parameters.Name)

    fun withParameter(key: String, value: String) = ContentDisposition(disposition, parameters + HeaderValueParam(key, value))
    fun withParameters(newParameters: List<HeaderValueParam>) = ContentDisposition(disposition, parameters + newParameters)

    override fun equals(other: Any?): Boolean =
        other is ContentDisposition &&
        disposition == other.disposition &&
        parameters == other.parameters

    override fun hashCode(): Int = disposition.hashCode() * 31 + parameters.hashCode()

    companion object {
        val File = ContentDisposition("file")
        val Mixed = ContentDisposition("mixed")
        val Attachment = ContentDisposition("attachment")
        val Inline = ContentDisposition("inline")

        fun parse(value: String) = HeaderValueWithParameters.parse(value) { v, p -> ContentDisposition(v, p) }
    }

    object Parameters {
        val FileName = "filename"
        val FileNameAsterisk = "filename*"
        val Name = "name"
        val CreationDate = "creation-date"
        val ModificationDate = "modification-date"
        val ReadDate = "read-date"
        val Size = "size"
        val handling = "handling"
    }
}

sealed class PartData(open val dispose: () -> Unit, open val partHeaders: ValuesMap) {
    class FormItem(val value: String, override val dispose: () -> Unit, override val partHeaders: ValuesMap) : PartData(dispose, partHeaders)
    class FileItem(val streamProvider: () -> InputStream, override val dispose: () -> Unit, override val partHeaders: ValuesMap) : PartData(dispose, partHeaders) {
        val originalFileName = contentDisposition?.parameter(ContentDisposition.Parameters.FileName)
    }

    val contentDisposition: ContentDisposition? by lazy {
        partHeaders[HttpHeaders.ContentDisposition]?.let { ContentDisposition.parse(it) }
    }

    val partName: String?
        get() = contentDisposition?.name

    val contentType: ContentType? by lazy { partHeaders[HttpHeaders.ContentType]?.let { ContentType.parse(it) } }
}

interface MultiPartData {
    val parts: Sequence<PartData>
    // TODO think of possible async methods
}
