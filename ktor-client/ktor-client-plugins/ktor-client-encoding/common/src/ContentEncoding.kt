/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.compression

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.api.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.*
import io.ktor.util.logging.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.io.IOException
import kotlin.coroutines.CoroutineContext

private val LOGGER = KtorSimpleLogger("io.ktor.client.plugins.compression.ContentEncoding")

/**
 * Default maximum number of chained content encodings allowed in a `Content-Encoding` response header.
 *
 * Helps mitigate "decompression bomb" attacks where a small response chains many
 * encodings to expand into a huge payload.
 */
internal const val DEFAULT_MAX_ENCODING_CHAIN_LENGTH: Int = 2

/**
 * Default maximum size, in bytes, of decompressed response content. Defaults to -1 (disabled).
 *
 * Helps mitigate "decompression bomb" attacks where a small compressed body
 * decompresses to a huge payload.
 */
internal const val DEFAULT_MAX_DECODED_CONTENT_LENGTH: Long = -1

/**
 * A configuration for the [ContentEncoding] plugin.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.compression.ContentEncodingConfig)
 */
@KtorDsl
public class ContentEncodingConfig {

    public enum class Mode(internal val request: Boolean, internal val response: Boolean) {
        CompressRequest(true, false),
        DecompressResponse(false, true),
        All(true, true),
    }

    internal val encoders: MutableMap<String, ContentEncoder> = CaseInsensitiveMap()

    internal val qualityValues: MutableMap<String, Float> = CaseInsensitiveMap()

    public var mode: Mode = Mode.DecompressResponse

    /**
     * The maximum number of chained content encodings that will be decoded automatically.
     *
     * Responses whose `Content-Encoding` header lists more than this number of codecs will fail
     * with a [ContentEncodingChainTooLongException]. This protects against "decompression bomb"
     * attacks where a small response chains many encodings to expand into a huge payload.
     *
     * Set to a non-positive value to disable the chain length check. Defaults to `2`.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.compression.ContentEncodingConfig.maxEncodingChainLength)
     */
    public var maxEncodingChainLength: Int = DEFAULT_MAX_ENCODING_CHAIN_LENGTH

    /**
     * The maximum size (in bytes) of the decoded response body.
     *
     * If decompression produces more than this number of bytes, reading the body fails with a
     * [DecodedContentTooLargeException] and the underlying channel is cancelled. This protects
     * against "decompression bomb" attacks where a small compressed body decompresses to a huge payload.
     *
     * Set to a non-positive value to disable the size cap. Defaults to `-1` (disabled).
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.compression.ContentEncodingConfig.maxDecodedContentLength)
     */
    public var maxDecodedContentLength: Long = DEFAULT_MAX_DECODED_CONTENT_LENGTH

    /**
     * Installs the `gzip` encoder.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.compression.ContentEncodingConfig.gzip)
     *
     * @param quality a priority value to use in the `Accept-Encoding` header.
     */
    public fun gzip(quality: Float? = null) {
        customEncoder(GZipEncoder, quality)
    }

    /**
     * Installs the `deflate` encoder.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.compression.ContentEncodingConfig.deflate)
     *
     * @param quality a priority value to use in the `Accept-Encoding` header.
     */
    public fun deflate(quality: Float? = null) {
        customEncoder(DeflateEncoder, quality)
    }

    /**
     * Installs the `identity` encoder.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.compression.ContentEncodingConfig.identity)
     *
     * @param quality a priority value to use in the `Accept-Encoding` header.
     */
    public fun identity(quality: Float? = null) {
        customEncoder(IdentityEncoder, quality)
    }

    /**
     * Installs a custom encoder.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.compression.ContentEncodingConfig.customEncoder)
     *
     * @param encoder a custom encoder to use.
     * @param quality a priority value to use in the `Accept-Encoding` header.
     */
    public fun customEncoder(encoder: ContentEncoder, quality: Float? = null) {
        val name = encoder.name
        encoders[name.lowercase()] = encoder

        if (quality == null) {
            qualityValues.remove(name)
        } else {
            qualityValues[name] = quality
        }
    }
}

/**
 * A plugin that allows you to enable specified compression algorithms (such as `gzip` and `deflate`) and configure their settings.
 * This plugin serves two primary purposes:
 * - Sets the `Accept-Encoding` header with the specified quality value.
 * - Decodes content received from a server to obtain the original payload.
 *
 * You can learn more from [Content encoding](https://ktor.io/docs/content-encoding.html).
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.compression.ContentEncoding)
 */
@OptIn(InternalAPI::class)
public val ContentEncoding: ClientPlugin<ContentEncodingConfig> = createClientPlugin(
    "HttpEncoding",
    ::ContentEncodingConfig
) {
    val encoders: Map<String, ContentEncoder> = pluginConfig.encoders
    val qualityValues: Map<String, Float> = pluginConfig.qualityValues
    val mode = pluginConfig.mode
    val maxEncodingChainLength = pluginConfig.maxEncodingChainLength
    val maxDecodedContentLength = pluginConfig.maxDecodedContentLength

    val requestHeader = buildString {
        for (encoder in encoders.values) {
            if (isNotEmpty()) append(',')

            append(encoder.name)

            val quality = qualityValues[encoder.name] ?: continue
            check(quality in 0.0..1.0) { "Invalid quality value: $quality for encoder: $encoder" }

            val qualityValue = quality.toString().take(5)
            append(";q=$qualityValue")
        }
    }

    fun HttpResponse.decodeContent(encoders: List<ContentEncoder>): ByteReadChannel {
        var current = rawContent
        for (encoder in encoders) {
            LOGGER.trace { "Decoding response with $encoder for ${call.request.url}" }
            current = encoder.decode(current, coroutineContext)
        }

        if (maxDecodedContentLength > 0) {
            current = current.limitDecodedSize(maxDecodedContentLength, coroutineContext)
        }

        return current
    }

    fun decode(response: HttpResponse): HttpResponse {
        if (!shouldDecode(response)) return response
        val contentEncodingHeader = response.headers[HttpHeaders.ContentEncoding]
            ?: error("${HttpHeaders.ContentEncoding} unavailable")
        val encodings = contentEncodingHeader.split(",").map { it.trim().lowercase() }

        if (maxEncodingChainLength > 0 && encodings.size > maxEncodingChainLength) {
            LOGGER.trace {
                "Rejecting decompression for ${response.call.request.url} because the Content-Encoding chain " +
                    "of length ${encodings.size} exceeds the configured limit of $maxEncodingChainLength."
            }
            throw ContentEncodingChainTooLongException(encodings.size, maxEncodingChainLength)
        }

        val selectedEncoders = encodings.asReversed().map { encoding ->
            encoders[encoding] ?: throw UnsupportedContentEncodingException(encoding)
        }

        val headers = headers {
            response.headers.forEach { name, values ->
                if (name.equals(
                        HttpHeaders.ContentEncoding,
                        ignoreCase = true
                    ) ||
                    name.equals(HttpHeaders.ContentLength, ignoreCase = true)
                ) {
                    return@forEach
                }
                appendAll(name, values)
            }
            val remainingEncodings = encodings.filter { !encodings.contains(it) }
            if (remainingEncodings.isNotEmpty()) {
                append(HttpHeaders.ContentEncoding, remainingEncodings.joinToString(","))
            }
        }
        response.call.attributes.put(DecompressionListAttribute, encodings)
        return response.call.replaceResponse(headers) { decodeContent(selectedEncoders) }.response
    }

    onRequest { request, _ ->
        if (!mode.response) return@onRequest
        if (request.headers.contains(HttpHeaders.AcceptEncoding)) return@onRequest
        LOGGER.trace { "Adding Accept-Encoding=$requestHeader for ${request.url}" }
        request.headers[HttpHeaders.AcceptEncoding] = requestHeader
    }

    on(AfterRenderHook) { request, content ->
        if (!mode.request) return@on null

        val encoderNames = request.attributes.getOrNull(CompressionListAttribute) ?: run {
            LOGGER.trace { "Skipping request compression for ${request.url} because no compressions set" }
            return@on null
        }

        LOGGER.trace { "Compressing request body for ${request.url} using $encoderNames" }
        val selectedEncoders = encoderNames.map {
            encoders[it] ?: throw UnsupportedContentEncodingException(it)
        }

        if (selectedEncoders.isEmpty()) return@on null
        selectedEncoders.fold(content) { compressed, encoder ->
            compressed.compressed(encoder, request.executionContext) ?: compressed
        }
    }

    on(ReceiveStateHook) { response ->
        if (!mode.response) return@on null

        val method = response.request.method
        val contentLength = response.contentLength()

        if (contentLength == 0L) return@on null
        if (contentLength == null && method == HttpMethod.Head) return@on null

        return@on decode(response)
    }
}

internal object AfterRenderHook : ClientHook<suspend (HttpRequestBuilder, OutgoingContent) -> OutgoingContent?> {
    val afterRenderPhase = PipelinePhase("AfterRender")
    override fun install(
        client: HttpClient,
        handler: suspend (HttpRequestBuilder, OutgoingContent) -> OutgoingContent?
    ) {
        client.requestPipeline.insertPhaseAfter(HttpRequestPipeline.Render, afterRenderPhase)
        client.requestPipeline.intercept(afterRenderPhase) {
            val result = handler(context, subject as OutgoingContent)
            if (result != null) proceedWith(result)
        }
    }
}

internal object ReceiveStateHook : ClientHook<suspend (HttpResponse) -> HttpResponse?> {

    override fun install(
        client: HttpClient,
        handler: suspend (HttpResponse) -> HttpResponse?
    ) {
        client.receivePipeline.intercept(HttpReceivePipeline.State) {
            val result = handler(it)
            if (result != null) proceedWith(result)
        }
    }
}

/**
 * Installs or configures the [ContentEncoding] plugin.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.compression.ContentEncoding)
 *
 * @param block: a [ContentEncoding] configuration.
 */
@Suppress("FunctionName")
public fun HttpClientConfig<*>.ContentEncoding(
    mode: ContentEncodingConfig.Mode = ContentEncodingConfig.Mode.DecompressResponse,
    block: ContentEncodingConfig.() -> Unit = {
        gzip()
        deflate()
        identity()
    }
) {
    install(ContentEncoding) {
        this.mode = mode
        block()
    }
}

public class UnsupportedContentEncodingException(encoding: String) :
    IllegalStateException("Content-Encoding: $encoding unsupported.")

/**
 * Thrown when the `Content-Encoding` header of a response lists more codecs than
 * [ContentEncodingConfig.maxEncodingChainLength] allows.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.compression.ContentEncodingChainTooLongException)
 *
 * @param chainLength the number of encodings present in the response
 * @param limit the configured maximum chain length
 */
public class ContentEncodingChainTooLongException(chainLength: Int, limit: Int) : IllegalStateException(
    "Content-Encoding chain of length $chainLength exceeds the configured limit of $limit. " +
        "This may indicate a decompression bomb."
)

/**
 * Thrown while reading a response body when the decoded content exceeds
 * [ContentEncodingConfig.maxDecodedContentLength] bytes.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.compression.DecodedContentTooLargeException)
 *
 * @param limit the configured maximum decoded content length in bytes
 */
@OptIn(ExperimentalCoroutinesApi::class)
public class DecodedContentTooLargeException(
    private val limit: Long
) : IOException(
    "Decoded content exceeds the configured limit of $limit bytes. This may indicate a decompression bomb."
),
    CopyableThrowable<DecodedContentTooLargeException> {

    override fun createCopy(): DecodedContentTooLargeException = DecodedContentTooLargeException(limit)
}

/**
 * Wraps this [ByteReadChannel] so that reading more than [limit] bytes from it fails with a
 * [DecodedContentTooLargeException] and cancels the source channel.
 */
internal fun ByteReadChannel.limitDecodedSize(
    limit: Long,
    coroutineContext: CoroutineContext,
): ByteReadChannel {
    val source = this
    return CoroutineScope(coroutineContext).writer(coroutineContext) {
        val buffer = ByteArray(8192)
        var total = 0L
        try {
            while (true) {
                val read = source.readAvailable(buffer, 0, buffer.size)
                if (read == -1) break
                total += read
                if (total > limit) throw DecodedContentTooLargeException(limit)
                channel.writeFully(buffer, 0, read)
                channel.flush()
            }
        } catch (cause: Throwable) {
            source.cancel(cause)
            throw cause
        }
    }.channel
}

internal val CompressionListAttribute: AttributeKey<List<String>> = AttributeKey("CompressionListAttribute")
internal val DecompressionListAttribute: AttributeKey<List<String>> = AttributeKey("DecompressionListAttribute")

/**
 * Compresses request body using [ContentEncoding] plugin.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.compression.compress)
 *
 * @param contentEncoderName names of compression encoders to use, such as "gzip", "deflate", etc
 */
public fun HttpRequestBuilder.compress(vararg contentEncoderName: String) {
    attributes.put(CompressionListAttribute, contentEncoderName.toList())
}

/**
 * Compress request body using [ContentEncoding] plugin.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.compression.compress)
 *
 * @param contentEncoderNames names of compression encoders to use, such as "gzip", "deflate", etc
 */
public fun HttpRequestBuilder.compress(contentEncoderNames: List<String>) {
    attributes.put(CompressionListAttribute, contentEncoderNames)
}

/**
 * List of [ContentEncoder] names that were used to decode response body.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.compression.appliedDecoders)
 */
public val HttpResponse.appliedDecoders: List<String>
    get() = call.attributes.getOrNull(DecompressionListAttribute) ?: emptyList()

internal expect fun shouldDecode(response: HttpResponse): Boolean
