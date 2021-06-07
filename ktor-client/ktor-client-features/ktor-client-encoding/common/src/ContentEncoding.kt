/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.features.compression

import io.ktor.client.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*

/**
 * Content-Encoding header support.
 *
 * See also: https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Content-Encoding
 */
public class ContentEncoding(
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
        val encodings = headers[HttpHeaders.ContentEncoding]?.split(",")?.map { it.trim().toLowerCase() }
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
     * [ContentEncoding] configuration.
     */
    public class Config {
        internal val encoders: MutableMap<String, ContentEncoder> = CaseInsensitiveMap()

        internal val qualityValues: MutableMap<String, Float> = CaseInsensitiveMap()

        /**
         * Install gzip encoder.
         *
         * @param quality: priority value to use in Accept-Encoding header.
         */
        public fun gzip(quality: Float? = null) {
            customEncoder(GZipEncoder, quality)
        }

        /**
         * Install deflate encoder.
         *
         * @param quality: priority value to use in Accept-Encoding header.
         */
        public fun deflate(quality: Float? = null) {
            customEncoder(DeflateEncoder, quality)
        }

        /**
         * Install identity encoder.
         * @param quality: priority value to use in Accept-Encoding header.
         */
        public fun identity(quality: Float? = null) {
            customEncoder(IdentityEncoder, quality)
        }

        /**
         * Install custom encoder.
         *
         * @param encoder: custom encoder to use.
         * @param quality: priority value to use in Accept-Encoding header.
         */
        public fun customEncoder(encoder: ContentEncoder, quality: Float? = null) {
            val name = encoder.name
            encoders[name.toLowerCase()] = encoder

            if (quality == null) {
                qualityValues.remove(name)
            } else {
                qualityValues[name] = quality
            }
        }
    }

    public companion object : HttpClientFeature<Config, ContentEncoding> {
        override val key: AttributeKey<ContentEncoding> = AttributeKey("HttpEncoding")

        override fun prepare(block: Config.() -> Unit): ContentEncoding {
            val config = Config().apply(block)

            return with(config) {
                ContentEncoding(encoders, qualityValues)
            }
        }

        override fun install(feature: ContentEncoding, scope: HttpClient) {
            scope.requestPipeline.intercept(HttpRequestPipeline.State) {
                feature.setRequestHeaders(context.headers)
            }

            scope.responsePipeline.intercept(HttpResponsePipeline.Receive) { (type, content) ->
                if (content !is ByteReadChannel) return@intercept

                val response = with(feature) {
                    HttpResponseContainer(type, context.decode(context.response.headers, content))
                }

                proceedWith(response)
            }
        }
    }
}

/**
 * Install or configure [ContentEncoding] feature.
 *
 * @param block: [ContentEncoding] configuration.
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
