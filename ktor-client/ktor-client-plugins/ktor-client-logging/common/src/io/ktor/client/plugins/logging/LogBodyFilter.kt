/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.logging

import io.ktor.http.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import kotlinx.io.Buffer

/**
 * Decides to include the body of a response in the logs (if body logging is enabled).
 *
 * The result can be a subset of the complete response, or some alternative format.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.logging.LogBodyFilter)
 */
public interface LogBodyFilter {
    /**
     * Applies a filtering operation on the request body based on the specified parameters.
     * Determines how the request body should be included in the logs.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.logging.LogBodyFilter.filterRequest)
     *
     * @param url The URL of the request.
     * @param contentLength The length of the request content, or null if unknown.
     * @param contentType The content type of the request, or null if unspecified.
     * @param headers The headers of the request.
     * @param body The channel containing the request body data.
     * @return The result of applying the body filter, which may include a subset or an alternative representation of the body.
     */
    public suspend fun filterRequest(
        url: Url,
        contentLength: Long?,
        contentType: ContentType?,
        headers: Headers,
        body: ByteReadChannel,
    ): BodyFilterResult

    /**
     * Applies a filtering operation on the response body based on the specified parameters.
     * Determines how the response body should be included in the logs.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.logging.LogBodyFilter.filterResponse)
     *
     * @param url The URL of the request.
     * @param contentLength The length of the response content, or null if unknown.
     * @param contentType The content type of the response, or null if unspecified.
     * @param headers The headers of the response.
     * @param body The channel containing the response body data.
     * @return The result of applying the body filter, which may include a subset or an alternative representation of the body.
     */
    public suspend fun filterResponse(
        url: Url,
        contentLength: Long?,
        contentType: ContentType?,
        headers: Headers,
        body: ByteReadChannel,
    ): BodyFilterResult
}

/**
 * Convenience interface for two-way filtering using a common method.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.logging.CommonLogBodyFilter)
 */
public fun interface CommonLogBodyFilter : LogBodyFilter {
    override suspend fun filterRequest(
        url: Url,
        contentLength: Long?,
        contentType: ContentType?,
        headers: Headers,
        body: ByteReadChannel
    ): BodyFilterResult = filterAll(contentLength, contentType, headers, body)

    override suspend fun filterResponse(
        url: Url,
        contentLength: Long?,
        contentType: ContentType?,
        headers: Headers,
        body: ByteReadChannel
    ): BodyFilterResult = filterAll(contentLength, contentType, headers, body)

    /**
     * Filters the body content using the provided parameters during both request and response processing.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.logging.CommonLogBodyFilter.filterAll)
     *
     * @param contentLength The length of the content, or null if the length is unknown.
     * @param contentType The type of the content, or null if the type is not specified.
     * @param headers The headers associated with the content.
     * @param body The channel for reading the body content.
     * @return A [BodyFilterResult] containing the result of the filtering operation.
     */
    public suspend fun filterAll(
        contentLength: Long?,
        contentType: ContentType?,
        headers: Headers,
        body: ByteReadChannel
    ): BodyFilterResult
}

/**
 * A [LogBodyFilter] that filters out binary content.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.logging.BinaryLogBodyFilter)
 */
public val BinaryLogBodyFilter: LogBodyFilter = object : CommonLogBodyFilter {
    override suspend fun filterAll(
        contentLength: Long?,
        contentType: ContentType?,
        headers: Headers,
        body: ByteReadChannel
    ): BodyFilterResult {
        if (headers.contains(HttpHeaders.ContentEncoding)) {
            return BodyFilterResult.Skip("encoded", contentLength)
        }

        if (contentType != null && contentType.isTextType()) {
            return BodyFilterResult.BufferContent(
                body.readBuffer(),
                contentType.charset() ?: Charsets.UTF_8
            )
        }

        val charset = if (contentType != null) {
            contentType.charset() ?: Charsets.UTF_8
        } else {
            Charsets.UTF_8
        }

        var isBinary = false
        val firstChunk = ByteArray(1024)
        val firstReadSize = body.readAvailable(firstChunk)

        if (firstReadSize < 1) {
            return BodyFilterResult.Empty
        }

        val buffer = Buffer().apply { writeFully(firstChunk, 0, firstReadSize) }

        val firstChunkText = try {
            charset.newDecoder().decode(buffer)
        } catch (_: MalformedInputException) {
            isBinary = true
            ""
        }

        if (!isBinary) {
            var lastCharIndex = -1
            for (ch in firstChunkText) {
                lastCharIndex += 1
            }

            for ((i, ch) in firstChunkText.withIndex()) {
                if (ch == '\ufffd' && i != lastCharIndex) {
                    isBinary = true
                    break
                }
            }
        }

        return if (isBinary) {
            BodyFilterResult.Skip("binary", contentLength)
        } else {
            BodyFilterResult.BufferContent(
                Buffer().apply {
                    writeFully(firstChunk, 0, firstReadSize)
                    transferFrom(body.readBuffer())
                },
                contentType?.charset() ?: Charsets.UTF_8
            )
        }
    }
}

/**
 * Represents the result of a body filtering process, which can determine the state of the body after processing.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.logging.BodyFilterResult)
 */
public sealed interface BodyFilterResult {
    public val byteSize: Long? get() = null

    /**
     * Represents an empty body.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.logging.BodyFilterResult.Empty)
     */
    public object Empty : BodyFilterResult

    /**
     * Represents a body filtering result that skips processing for a specific reason.
     *
     * This result indicates that the body should not be processed further.
     * A reason for skipping can be optionally provided as well as the size of the body in bytes.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.logging.BodyFilterResult.Skip)
     *
     * @property reason An optional explanation for why the body processing was skipped.
     * @property byteSize The size of the body in bytes, or null if unknown.
     */
    public class Skip(
        public val reason: String? = null,
        override val byteSize: Long? = null,
    ) : BodyFilterResult

    /**
     * Represents a body filtering result that contains readable content.
     *
     * This interface defines the functionality to access the content of a body after filtering.
     * Implementations should specify how the content is stored and provide the ability to read it as a string.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.logging.BodyFilterResult.Content)
     */
    public interface Content : BodyFilterResult {
        public fun read(): String
    }

    /**
     * Implements the [BodyFilterResult.Content] interface, representing a filtering result
     * that contains readable content from a buffer.
     *
     * This class provides functionality to decode the content of a given [buffer] into a string
     * using the specified [charset]. It also includes the size of the buffer as its byte size.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.logging.BodyFilterResult.BufferContent)
     *
     * @property buffer The [Buffer] containing the content to be read.
     * @property charset The [Charset] used for decoding the buffer content into a string.
     * @property byteSize The size of the buffer in bytes.
     */
    public class BufferContent(
        private val buffer: Buffer,
        private val charset: Charset,
        override val byteSize: Long = buffer.size,
    ) : Content {
        override fun read(): String =
            buffer.readText(charset)
    }
}
