/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.plugins.compression

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.*
import io.ktor.util.logging.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

/**
 * List of [ContentEncoder] names that were used to decode request body.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.compression.appliedDecoders)
 */
public val ApplicationRequest.appliedDecoders: List<String>
    get() = call.attributes.getOrNull(DecompressionListAttribute) ?: emptyList()

internal val DecompressionListAttribute: AttributeKey<List<String>> = AttributeKey("DecompressionListAttribute")

internal val LOGGER = KtorSimpleLogger("io.ktor.server.plugins.compression.Compression")

/**
 * The default minimal content size to compress.
 */
internal const val DEFAULT_MINIMAL_COMPRESSION_SIZE: Long = 200L

/**
 * Default maximum number of chained content encodings allowed in a `Content-Encoding` request
 * header.
 *
 * Helps mitigate "decompression bomb" / DoS attacks where a small request chains many
 * encodings to expand into a huge payload.
 */
public const val DEFAULT_MAX_ENCODING_CHAIN_LENGTH: Int = 2

/**
 * Default maximum size, in bytes, of decompressed request content. Defaults to -1 (disabled).
 *
 * Helps mitigate "decompression bomb" / DoS attacks where a small compressed body
 * decompresses to a huge payload.
 */
public const val DEFAULT_MAX_DECODED_CONTENT_LENGTH: Long = -1

/**
 * Thrown when the `Content-Encoding` header on a request lists more codecs than
 * [CompressionConfig.maxEncodingChainLength] allows.
 *
 * Throwing this exception in a handler will lead to a 400 Bad Request response unless a
 * custom [io.ktor.server.plugins.statuspages.StatusPages] handler is registered.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.compression.ContentEncodingChainTooLongException)
 *
 * @property chainLength the number of encodings present in the request
 * @property limit the configured maximum chain length
 */
public class ContentEncodingChainTooLongException(
    public val chainLength: Int,
    public val limit: Int,
) : BadRequestException(
    "Content-Encoding chain of length $chainLength exceeds the configured limit of $limit. " +
        "This may indicate a decompression bomb."
)

/**
 * Wraps this [ByteReadChannel] so that reading more than [limit] bytes from it cancels the
 * channel with a [PayloadTooLargeException].
 *
 * The wrapper is launched in the caller's [coroutineContext] (typically the call/request
 * coroutine scope), so it is cancelled together with the request and does not leak into
 * [GlobalScope].
 */
@OptIn(InternalAPI::class)
internal fun ByteReadChannel.limitDecodedSize(
    limit: Long,
    coroutineContext: CoroutineContext,
): ByteReadChannel {
    val source = this
    return CoroutineScope(coroutineContext).writer(coroutineContext) {
        var total = 0L
        try {
            while (!source.isClosedForRead) {
                if (source.availableForRead == 0) {
                    source.awaitContent()
                }
                val available = source.availableForRead
                if (available <= 0) continue
                val toRead = minOf(available.toLong(), limit - total + 1).toInt()
                if (toRead <= 0) {
                    throw PayloadTooLargeException(limit)
                }
                val bytes = source.readByteArray(toRead)
                total += bytes.size
                if (total > limit) {
                    throw PayloadTooLargeException(limit)
                }
                channel.writeFully(bytes)
                channel.flush()
            }
            source.closedCause?.let { throw it }
        } catch (cause: Throwable) {
            source.cancel(cause)
            throw cause
        }
    }.channel
}

/**
 * A plugin that provides the capability to compress a response and decompress request bodies.
 * You can use different compression algorithms, including `gzip` and `deflate`,
 * specify the required conditions for compressing data (such as a content type or response size),
 * or even compress data based on specific request parameters.
 *
 * Note that if the request body was decompressed,
 * the plugin will remove [HttpHeaders.ContentEncoding] and [HttpHeaders.ContentLength] headers.
 * Also, it will add [HttpHeaders.TransferEncoding] header with `chunked` value.
 * Original encodings can be accessed through [ApplicationRequest.appliedDecoders] property.
 *
 * The example below shows how to compress JavaScript content using `gzip` with the specified priority:
 * ```kotlin
 * install(Compression) {
 *     gzip {
 *         priority = 0.9
 *         matchContentType(ContentType.Application.JavaScript)
 *     }
 * }
 * ```
 *
 * You can learn more from [Compression](https://ktor.io/docs/compression.html).
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.compression.Compression)
 */
public val Compression: RouteScopedPlugin<CompressionConfig> = createRouteScopedPlugin(
    "Compression",
    ::CompressionConfig
) {
    if (pluginConfig.encoders.none()) {
        pluginConfig.default()
    }
    val options = pluginConfig.buildOptions()
    val mode = pluginConfig.mode
    val maxEncodingChainLength = pluginConfig.maxEncodingChainLength
    val maxDecodedContentLength = pluginConfig.maxDecodedContentLength

    on(ContentEncoding) { call ->
        if (!mode.response) return@on
        encode(call, options)
    }

    on(ContentDecoding) { call ->
        if (!mode.request) return@on
        decode(call, options, maxEncodingChainLength, maxDecodedContentLength)
    }
}

@OptIn(InternalAPI::class)
private suspend fun ContentDecoding.Context.decode(
    call: PipelineCall,
    options: CompressionOptions,
    maxEncodingChainLength: Int,
    maxDecodedContentLength: Long,
) {
    val encodingRaw = call.request.headers[HttpHeaders.ContentEncoding]
    if (call.isDecompressionSuppressed) {
        LOGGER.trace("Skip decompression for ${call.request.uri} because it is suppressed.")
        return
    }
    if (encodingRaw == null) {
        LOGGER.trace("Skip decompression for ${call.request.uri} because no content encoding provided.")
        return
    }
    val encoding = parseHeaderValue(encodingRaw)
    if (maxEncodingChainLength > 0 && encoding.size > maxEncodingChainLength) {
        LOGGER.trace(
            "Rejecting decompression for ${call.request.uri} because the Content-Encoding chain " +
                "of length ${encoding.size} exceeds the configured limit of $maxEncodingChainLength."
        )
        throw ContentEncodingChainTooLongException(encoding.size, maxEncodingChainLength)
    }
    val encoders = encoding.mapNotNull { options.encoders[it.value] }
    if (encoders.isEmpty()) {
        LOGGER.trace("Skip decompression for ${call.request.uri} because no suitable encoders found.")
        return
    }
    val encoderNames = encoders.map { it.encoder.name }
    if (encoding.size > encoders.size) {
        val missingEncoders = encoding.map { it.value } - encoderNames.toSet()
        call.request.setHeader(HttpHeaders.ContentEncoding, missingEncoders)
        LOGGER.trace(
            "Skip some of decompression for ${call.request.uri} " +
                "because no suitable encoders found for $missingEncoders"
        )
    } else {
        call.request.setHeader(HttpHeaders.ContentEncoding, null)
    }

    call.attributes.put(DecompressionListAttribute, encoderNames)

    transformBody { body ->
        val decoded = encoders.fold(body) { content, encoder -> encoder.encoder.decode(content) }
        if (maxDecodedContentLength > 0) {
            decoded.limitDecodedSize(maxDecodedContentLength, call.coroutineContext)
        } else {
            decoded
        }
    }
}

private fun ContentEncoding.Context.encode(call: PipelineCall, options: CompressionOptions) {
    if (call.response.isSSEResponse()) {
        LOGGER.trace("Skip compression for sse response ${call.request.uri} ")
        return
    }

    val comparator = compareBy<Pair<CompressionEncoderConfig, HeaderValue>>(
        { it.second.quality },
        { it.first.priority }
    ).reversed()

    val acceptEncodingRaw = call.request.acceptEncoding()
    if (acceptEncodingRaw == null) {
        LOGGER.trace("Skip compression for ${call.request.uri} because no accept encoding provided.")
        return
    }

    if (call.isCompressionSuppressed) {
        LOGGER.trace("Skip compression for ${call.request.uri} because it is suppressed.")
        return
    }

    val encoders = parseHeaderValue(acceptEncodingRaw)
        .filter { it.value == "*" || it.value in options.encoders }
        .flatMap { header ->
            when (header.value) {
                "*" -> options.encoders.values.map { it to header }
                else -> options.encoders[header.value]?.let { listOf(it to header) } ?: emptyList()
            }
        }
        .sortedWith(comparator)
        .map { it.first }

    if (encoders.isEmpty()) {
        LOGGER.trace("Skip compression for ${call.request.uri} because no encoders provided.")
        return
    }

    transformBody { message ->
        if (options.conditions.any { !it(call, message) }) {
            LOGGER.trace("Skip compression for ${call.request.uri} because preconditions doesn't meet.")
            return@transformBody null
        }

        val encodingHeader = message.headers[HttpHeaders.ContentEncoding]
        if (encodingHeader != null) {
            LOGGER.trace("Skip compression for ${call.request.uri} because content is already encoded.")
            return@transformBody null
        }

        val encoderOptions = encoders.firstOrNull { encoder -> encoder.conditions.all { it(call, message) } }

        if (encoderOptions == null) {
            LOGGER.trace("Skip compression for ${call.request.uri} because no suitable encoder found.")
            return@transformBody null
        }

        LOGGER.trace("Encoding body for ${call.request.uri} using ${encoderOptions.encoder.name}.")

        // Most compression algorithms (e.g. gzip, deflate, brotli) cannot compress streaming responses
        // incrementally without buffering the entire body, which defeats the purpose of streaming.
        if (message is OutgoingContent.WriteChannelContent) {
            LOGGER.warn(
                "Compressing a WriteChannelContent response for ${call.request.uri}. " +
                    "Compression will buffer the entire body before sending, which defeats the purpose of streaming. " +
                    "Consider suppressing compression for this route with call.suppressCompression()."
            )
        }

        return@transformBody message.compressed(encoderOptions.encoder)
    }
}

private fun PipelineResponse.isSSEResponse(): Boolean {
    val contentType = headers[HttpHeaders.ContentType]?.let { ContentType.parse(it) }
    return contentType?.withoutParameters() == ContentType.Text.EventStream
}
