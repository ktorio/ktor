package io.ktor.http.content

import io.ktor.http.*
import kotlinx.io.core.*

/**
 * Represents a multipart/form-data entry. Could be a [FormItem] or [FileItem]
 * @property dispose to be invoked when this part is no longed needed
 * @property headers of this part, could be inaccurate on some engines
 */
sealed class PartData(val dispose: () -> Unit, val headers: Headers) {
    /**
     * Represents a multipart form item
     * @property value of this field
     */
    class FormItem(val value: String, dispose: () -> Unit, partHeaders: Headers) : PartData(dispose, partHeaders)

    /**
     * Represents a file item
     * @property provider of content bytes
     */
    class FileItem(val provider: () -> Input, dispose: () -> Unit, partHeaders: Headers) :
        PartData(dispose, partHeaders) {
        /**
         * Original file name if present
         */
        val originalFileName: String? = contentDisposition?.parameter(ContentDisposition.Parameters.FileName)
    }

    /**
     * Represents a binary item
     * @property provider of content bytes
     */
    class BinaryItem(val provider: () -> Input, dispose: () -> Unit, partHeaders: Headers) :
        PartData(dispose, partHeaders)

    /**
     * Parsed `Content-Disposition` header or `null` if missing
     */
    val contentDisposition: ContentDisposition? by lazy(LazyThreadSafetyMode.NONE) {
        headers[HttpHeaders.ContentDisposition]?.let { ContentDisposition.parse(it) }
    }

    /**
     * Parsed `Content-Type` header or `null` if missing
     */
    val contentType: ContentType? by lazy(LazyThreadSafetyMode.NONE) {
        headers[HttpHeaders.ContentType]?.let {
            ContentType.parse(
                it
            )
        }
    }

    /**
     * Optional part name based on `Content-Disposition` header
     */
    val name: String? get() = contentDisposition?.name

    @Suppress("KDocMissingDocumentation", "unused")
    @Deprecated(
        "Use name property instead", ReplaceWith("name"),
        level = DeprecationLevel.ERROR
    )
    val partName: String?
        get() = name

    @Suppress("KDocMissingDocumentation", "unused")
    @Deprecated(
        "Use headers property instead", ReplaceWith("headers"),
        level = DeprecationLevel.ERROR
    )
    val partHeaders: Headers
        get() = headers
}

/**
 * Represents a multipart data stream that could be received from a call
 */
interface MultiPartData {
    /**
     * Reads next part data or `null` if end of multipart stream encountered
     */
    suspend fun readPart(): PartData?

    /**
     * An empty multipart data stream
     */
    object Empty : MultiPartData {
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
