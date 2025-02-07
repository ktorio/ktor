/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http.content

import io.ktor.http.*
import io.ktor.http.content.PartData.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.flow.*

/**
 * Represents a multipart/form-data entry. Could be a [FormItem] or [FileItem].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.content.PartData)
 *
 * @property dispose to be invoked when this part is no longed needed
 * @property headers of this part, could be inaccurate on some engines
 */
public sealed class PartData(public val dispose: () -> Unit, public val headers: Headers) {
    /**
     * Represents a multipart form item.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.content.PartData.FormItem)
     *
     * @property value of this field
     */
    public class FormItem(public val value: String, dispose: () -> Unit, partHeaders: Headers) :
        PartData(dispose, partHeaders)

    /**
     * Represents a file item.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.content.PartData.FileItem)
     *
     * @property provider of content bytes
     */

    public class FileItem(
        public val provider: () -> ByteReadChannel,
        dispose: () -> Unit,
        partHeaders: Headers
    ) : PartData(dispose, partHeaders) {
        /**
         * Original file name if present
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.content.PartData.FileItem.originalFileName)
         */
        public val originalFileName: String? = contentDisposition?.parameter(ContentDisposition.Parameters.FileName)
    }

    /**
     * Represents a binary item.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.content.PartData.BinaryItem)
     *
     * @property provider of content bytes
     */

    public class BinaryItem(
        public val provider: () -> Input,
        dispose: () -> Unit,
        partHeaders: Headers
    ) : PartData(dispose, partHeaders)

    /**
     * Represents a binary part with a provider that supplies [ByteReadChannel].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.content.PartData.BinaryChannelItem)
     *
     * @property provider supplies a channel to read data from
     */
    public class BinaryChannelItem(
        public val provider: () -> ByteReadChannel,
        partHeaders: Headers
    ) : PartData({}, partHeaders)

    /**
     * Parsed `Content-Disposition` header or `null` if missing.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.content.PartData.contentDisposition)
     */
    public val contentDisposition: ContentDisposition? by lazy(LazyThreadSafetyMode.NONE) {
        headers[HttpHeaders.ContentDisposition]?.let { ContentDisposition.parse(it) }
    }

    /**
     * Parsed `Content-Type` header or `null` if missing.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.content.PartData.contentType)
     */
    public val contentType: ContentType? by lazy(LazyThreadSafetyMode.NONE) {
        headers[HttpHeaders.ContentType]?.let {
            ContentType.parse(
                it
            )
        }
    }

    /**
     * Optional part name based on `Content-Disposition` header.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.content.PartData.name)
     */
    public val name: String? get() = contentDisposition?.name
}

/**
 * Represents a multipart data stream that could be received from a call.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.content.MultiPartData)
 */
public interface MultiPartData {
    /**
     * Reads next part data or `null` if the end of multipart stream encountered.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.content.MultiPartData.readPart)
     */
    public suspend fun readPart(): PartData?

    /**
     * An empty multipart data stream.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.content.MultiPartData.Empty)
     */
    public object Empty : MultiPartData {
        override suspend fun readPart(): PartData? {
            return null
        }
    }
}

/**
 * Transforms the multipart data stream into a [Flow] of [PartData].
 *
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.content.asFlow)
 *
 * @return a [Flow] emitting each part of the multipart data until the end of the stream.
 */
public fun MultiPartData.asFlow(): Flow<PartData> = flow {
    while (true) {
        val part = readPart() ?: break
        emit(part)
    }
}

/**
 * Parse multipart data stream and invoke [partHandler] for each [PartData] encountered.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.content.forEachPart)
 *
 * @param partHandler to be invoked for every part item
 */
public suspend fun MultiPartData.forEachPart(partHandler: suspend (PartData) -> Unit): Unit =
    asFlow().collect(partHandler)

/**
 * Parse multipart data stream and put all parts into a list.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.content.readAllParts)
 *
 * @return a list of [PartData]
 */
@Deprecated("This method can deadlock on large requests. Use `forEachPart` instead.", level = DeprecationLevel.ERROR)
public suspend fun MultiPartData.readAllParts(): List<PartData> {
    var part = readPart() ?: return emptyList()
    val parts = ArrayList<PartData>()
    parts.add(part)

    do {
        part = readPart() ?: break
        parts.add(part)
    } while (true)

    return parts
}
