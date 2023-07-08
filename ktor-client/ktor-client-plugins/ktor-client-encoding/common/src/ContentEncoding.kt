/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.plugins.compression

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.observer.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.*
import io.ktor.util.date.*
import io.ktor.util.logging.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

private val LOGGER = KtorSimpleLogger("io.ktor.client.plugins.compression.ContentEncoding")

/**
 * A plugin that allows you to enable specified compression algorithms (such as `gzip` and `deflate`) and configure their settings.
 * This plugin serves two primary purposes:
 * - Sets the `Accept-Encoding` header with the specified quality value.
 * - Decodes content received from a server to obtain the original payload.
 *
 * You can learn more from [Content encoding](https://ktor.io/docs/content-encoding.html).
 */
public class ContentEncoding private constructor(
    private val encoders: Map<String, ContentEncoder>,
    private val qualityValues: Map<String, Float>
) {
    private val requestHeader = buildString {
        for (encoder in encoders.values) {
            if (length > 0) append(',')

            append(encoder.name)

            val quality = qualityValues[encoder.name] ?: continue
            check(quality in 0.0..1.0) { "Invalid quality value: $quality for encoder: $encoder" }

            val qualityValue = quality.toString().take(5)
            append(";q=$qualityValue")
        }
    }

    private fun setRequestHeaders(request: HttpRequestBuilder) {
        if (request.headers.contains(HttpHeaders.AcceptEncoding)) return
        LOGGER.trace("Adding Accept-Encoding=$request for ${request.url}")
        request.headers[HttpHeaders.AcceptEncoding] = requestHeader
    }

    private fun decode(response: HttpResponse, content: ByteReadChannel): HttpResponse {
        val encodings = response.headers[HttpHeaders.ContentEncoding]?.split(",")?.map { it.trim().lowercase() }
            ?: run {
                LOGGER.trace(
                    "Empty or no Content-Encoding header in response. " +
                        "Skipping ContentEncoding for ${response.call.request.url}"
                )
                return response
            }

        var current = content
        for (encoding in encodings.reversed()) {
            val encoder: Encoder = encoders[encoding] ?: throw UnsupportedContentEncodingException(encoding)

            LOGGER.trace("Decoding response with $encoder for ${response.call.request.url}")
            with(encoder) {
                current = decode(current, response.coroutineContext)
            }
        }

        val headers = headers {
            response.headers.forEach { name, values ->
                if (name.equals(HttpHeaders.ContentEncoding, ignoreCase = true)) return@forEach
                appendAll(name, values)
            }
            val remainingEncodings = encodings.filter { !encodings.contains(it) }
            if (remainingEncodings.isNotEmpty()) {
                append(HttpHeaders.ContentEncoding, remainingEncodings.joinToString(","))
            }
        }
        response.call.attributes.put(DecompressionListAttribute, encodings)
        return response.call.wrap(current, headers).response
    }

    /**
     * A configuration for the [ContentEncoding] plugin.
     */
    @KtorDsl
    public class Config {
        internal val encoders: MutableMap<String, ContentEncoder> = CaseInsensitiveMap()

        internal val qualityValues: MutableMap<String, Float> = CaseInsensitiveMap()

        /**
         * Installs the `gzip` encoder.
         *
         * @param quality a priority value to use in the `Accept-Encoding` header.
         */
        public fun gzip(quality: Float? = null) {
            customEncoder(GZipEncoder, quality)
        }

        /**
         * Installs the `deflate` encoder.
         *
         * @param quality a priority value to use in the `Accept-Encoding` header.
         */
        public fun deflate(quality: Float? = null) {
            customEncoder(DeflateEncoder, quality)
        }

        /**
         * Installs the `identity` encoder.
         * @param quality a priority value to use in the `Accept-Encoding` header.
         */
        public fun identity(quality: Float? = null) {
            customEncoder(IdentityEncoder, quality)
        }

        /**
         * Installs a custom encoder.
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

    public companion object : HttpClientPlugin<Config, ContentEncoding> {
        override val key: AttributeKey<ContentEncoding> = AttributeKey("HttpEncoding")

        override fun prepare(block: Config.() -> Unit): ContentEncoding {
            val config = Config().apply(block)

            return with(config) {
                ContentEncoding(encoders, qualityValues)
            }
        }

        @OptIn(InternalAPI::class)
        override fun install(plugin: ContentEncoding, scope: HttpClient) {
            scope.requestPipeline.intercept(HttpRequestPipeline.State) {
                plugin.setRequestHeaders(context)
            }

            val afterRenderPhase = PipelinePhase("AfterRender")
            scope.requestPipeline.insertPhaseAfter(HttpRequestPipeline.Render, afterRenderPhase)
            scope.requestPipeline.intercept(afterRenderPhase) {
                val encoderNames = context.attributes.getOrNull(CompressionListAttribute) ?: run {
                    LOGGER.trace("Skipping request compression for ${context.url} because no compressions set")
                    return@intercept
                }

                LOGGER.trace("Compressing request body for ${context.url} using $encoderNames")
                val encoders = encoderNames.map {
                    plugin.encoders[it] ?: throw UnsupportedContentEncodingException(it)
                }

                if (encoders.isEmpty()) return@intercept
                val content = encoders.fold(subject as OutgoingContent) { compressed, encoder ->
                    compressed.compressed(encoder, context.executionContext) ?: compressed
                }
                proceedWith(content)
            }

            scope.receivePipeline.intercept(HttpReceivePipeline.State) { response ->
                val method = response.call.request.method
                val contentLength = response.contentLength()

                if (contentLength == 0L) return@intercept
                if (contentLength == null && method == HttpMethod.Head) return@intercept

                val decompressed = with(plugin) {
                    decode(response, response.content)
                }

                proceedWith(decompressed)
            }
        }
    }
}

/**
 * Installs or configures the [ContentEncoding] plugin.
 *
 * @param block: a [ContentEncoding] configuration.
 */
public fun HttpClientConfig<*>.ContentEncoding(
    block: ContentEncoding.Config.() -> Unit = {
        gzip()
        deflate()
        identity()
    }
) {
    install(ContentEncoding, block)
}

@Suppress("KDocMissingDocumentation")
public class UnsupportedContentEncodingException(encoding: String) :
    IllegalStateException("Content-Encoding: $encoding unsupported.")

internal val CompressionListAttribute: AttributeKey<List<String>> = AttributeKey("CompressionListAttribute")
internal val DecompressionListAttribute: AttributeKey<List<String>> = AttributeKey("DecompressionListAttribute")

/**
 * Compresses request body using [ContentEncoding] plugin.
 *
 * @param contentEncoderName names of compression encoders to use, such as "gzip", "deflate", etc
 */
public fun HttpRequestBuilder.compress(vararg contentEncoderName: String) {
    attributes.put(CompressionListAttribute, contentEncoderName.toList())
}

/**
 * Compress request body using [ContentEncoding] plugin.
 *
 * @param contentEncoderNames names of compression encoders to use, such as "gzip", "deflate", etc
 */
public fun HttpRequestBuilder.compress(contentEncoderNames: List<String>) {
    attributes.put(CompressionListAttribute, contentEncoderNames)
}

/**
 * List of [ContentEncoder] names that were used to decode response body.
 */
public val HttpResponse.appliedDecoders: List<String>
    get() = call.attributes.getOrNull(DecompressionListAttribute) ?: emptyList()
