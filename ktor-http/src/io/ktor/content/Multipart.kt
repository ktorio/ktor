package io.ktor.content

import io.ktor.http.*
import kotlinx.coroutines.experimental.*
import java.io.*
import kotlin.coroutines.experimental.*

/**
 * Represents a multipart/form-data entry. Could be a [FormItem] or [FileItem]
 */
sealed class PartData(val dispose: () -> Unit, val headers: Headers) {
    class FormItem(val value: String, dispose: () -> Unit, partHeaders: Headers) : PartData(dispose, partHeaders)
    class FileItem(val streamProvider: () -> InputStream, dispose: () -> Unit, partHeaders: Headers) : PartData(dispose, partHeaders) {
        val originalFileName = contentDisposition?.parameter(ContentDisposition.Parameters.FileName)
    }

    /**
     * Parsed `Content-Disposition` header or `null` if missing
     */
    val contentDisposition: ContentDisposition? by lazy(LazyThreadSafetyMode.NONE) {
        headers[HttpHeaders.ContentDisposition]?.let { ContentDisposition.parse(it) }
    }

    /**
     * Parsed `Content-Type` header or `null` if missing
     */
    val contentType: ContentType? by lazy(LazyThreadSafetyMode.NONE) { headers[HttpHeaders.ContentType]?.let { ContentType.parse(it) } }

    /**
     * Optional part name based on `Content-Disposition` header
     */
    val name: String? get() = contentDisposition?.name

    @Deprecated("Use name property instead", ReplaceWith("name"))
    val partName: String?
        get() = name

    @Deprecated("Use headers property instead", ReplaceWith("headers"))
    val partHeaders: Headers get() = headers
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

    /**
     * Reads next part data or `null` if end of multipart stream encountered
     */
    suspend fun readPart(): PartData?

    object Empty : MultiPartData {
        @Suppress("OverridingDeprecatedMember", "DEPRECATION")
        override val parts: Sequence<PartData>
            get() = emptySequence()

        override suspend fun readPart(): PartData? {
            return null
        }
    }
}

/**
 * Parse multipart data stream and invoke [partHandler] for each [PartData] encountered
 * @param partHandler to be invoked for every part item
 */
suspend fun MultiPartData.forEachPart(partHandler: suspend (PartData) -> Unit) {
    while (true) {
        val part = readPart() ?: break
        partHandler(part)
    }
}

/**
 * Parse multipart data stream and put all parts into a list
 * @return a list of part data
 */
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
