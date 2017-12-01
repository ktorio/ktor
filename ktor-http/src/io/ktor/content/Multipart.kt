package io.ktor.content

import io.ktor.http.*
import io.ktor.util.*
import kotlinx.coroutines.experimental.*
import java.io.*
import kotlin.coroutines.experimental.*

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
    @Deprecated("Use readAllParts() or readPart() in loop until null")
    val parts: Sequence<PartData>
        get() = buildSequence {
            while (true) {
                val part = runBlocking { readPart() } ?: break
                yield(part)
            }
        }

    suspend fun readPart(): PartData?

    object Empty : MultiPartData {
        override val parts: Sequence<PartData>
            get() = emptySequence()

        suspend override fun readPart(): PartData? {
            return null
        }
    }
}

suspend fun MultiPartData.forEachPart(partHandler: suspend (PartData) -> Unit) {
    while (true) {
        val part = readPart() ?: break
        partHandler(part)
    }
}

suspend fun MultiPartData.readAllParts(): List<PartData> {
    var part = readPart() ?: return emptyList()
    val parts = ArrayList<PartData>()
    parts.add(part)

    do {
        part = readPart() ?: break
        parts.add(part)
    } while (true)

    return parts
}
