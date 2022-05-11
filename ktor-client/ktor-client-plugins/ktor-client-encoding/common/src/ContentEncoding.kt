/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.plugins.compression

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*

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

    private fun setRequestHeaders(headers: HeadersBuilder) {
        if (headers.contains(HttpHeaders.AcceptEncoding)) return
        headers[HttpHeaders.AcceptEncoding] = requestHeader
    }

    private fun CoroutineScope.decode(headers: Headers, content: ByteReadChannel): ByteReadChannel {
        val encodings = headers[HttpHeaders.ContentEncoding]?.split(",")?.map { it.trim().lowercase() }
            ?: return content

        var current = content
        for (encoding in encodings.reversed()) {
            val encoder: Encoder = encoders[encoding] ?: throw UnsupportedContentEncodingException(encoding)

            with(encoder) {
                current = decode(current)
            }
        }

        return current
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

        override fun install(plugin: ContentEncoding, scope: HttpClient) {
            scope.requestPipeline.intercept(HttpRequestPipeline.State) {
                plugin.setRequestHeaders(context.headers)
            }

            scope.responsePipeline.intercept(HttpResponsePipeline.Receive) { (type, content) ->
                val method = context.request.method
                val contentLength = context.response.contentLength()

                if (contentLength == 0L) return@intercept
                if (contentLength == null && method == HttpMethod.Head) return@intercept
                if (content !is ByteReadChannel) return@intercept

                val response = with(plugin) {
                    HttpResponseContainer(type, context.decode(context.response.headers, content))
                }

                proceedWith(response)
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
