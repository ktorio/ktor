package io.ktor.request

import io.ktor.http.*
import io.ktor.util.*
import java.io.*

sealed class PartData(val dispose: () -> Unit, val partHeaders: ValuesMap) {
    class FormItem(val value: String, dispose: () -> Unit, partHeaders: ValuesMap) : PartData(dispose, partHeaders)
    class FileItem(val streamProvider: () -> InputStream, dispose: () -> Unit, partHeaders: ValuesMap) : PartData(dispose, partHeaders) {
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
