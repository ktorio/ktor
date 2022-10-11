/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.plugins.compression

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.*
import io.ktor.util.cio.*
import io.ktor.util.logging.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*

internal val LOGGER = KtorSimpleLogger("io.ktor.server.plugins.compression.Compression")

/**
 * The default minimal content size to compress.
 */
internal const val DEFAULT_MINIMAL_COMPRESSION_SIZE: Long = 200L

private object ContentEncoding : Hook<suspend ContentEncoding.Context.(ApplicationCall) -> Unit> {

    class Context(private val pipelineContext: PipelineContext<Any, ApplicationCall>) {
        fun transformBody(block: (OutgoingContent) -> OutgoingContent?) {
            val transformedContent = block(pipelineContext.subject as OutgoingContent)
            if (transformedContent != null) {
                pipelineContext.subject = transformedContent
            }
        }
    }

    override fun install(
        pipeline: ApplicationCallPipeline,
        handler: suspend Context.(ApplicationCall) -> Unit
    ) {
        pipeline.sendPipeline.intercept(ApplicationSendPipeline.ContentEncoding) {
            handler(Context(this), call)
        }
    }
}

/**
 * A plugin that provides the capability to compress a response body.
 * You can use different compression algorithms, including `gzip` and `deflate`,
 * specify the required conditions for compressing data (such as a content type or response size),
 * or even compress data based on specific request parameters.
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
 */
public val Compression: RouteScopedPlugin<CompressionConfig> = createRouteScopedPlugin(
    "Compression",
    ::CompressionConfig
) {
    if (pluginConfig.encoders.none()) {
        pluginConfig.default()
    }
    val options = pluginConfig.buildOptions()
    val comparator = compareBy<Pair<CompressionEncoderConfig, HeaderValue>>(
        { it.second.quality },
        { it.first.priority }
    ).reversed()

    on(ContentEncoding) { call ->
        val acceptEncodingRaw = call.request.acceptEncoding()
        if (acceptEncodingRaw == null) {
            LOGGER.trace("Skip compression because no accept encoding provided.")
            return@on
        }

        if (call.isCompressionSuppressed()) {
            LOGGER.trace("Skip compression because it is suppressed.")
            return@on
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
            LOGGER.trace("Skip compression because no encoders provided.")
            return@on
        }

        transformBody { message ->
            if (message is CompressedResponse) {
                LOGGER.trace("Skip compression because it's already compressed.")
                return@transformBody null
            }
            if (options.conditions.any { !it(call, message) }) {
                LOGGER.trace("Skip compression because preconditions doesn't meet.")
                return@transformBody null
            }

            val encodingHeader = message.headers[HttpHeaders.ContentEncoding]
            if (encodingHeader != null) {
                LOGGER.trace("Skip compression because content is already encoded.")
                return@transformBody null
            }

            val encoderOptions = encoders.firstOrNull { encoder -> encoder.conditions.all { it(call, message) } }

            if (encoderOptions == null) {
                LOGGER.trace("Skip compression because no suitable encoder found.")
                return@transformBody null
            }

            LOGGER.trace("Encoding body using ${encoderOptions.name}.")
            return@transformBody when (message) {
                is OutgoingContent.ReadChannelContent -> CompressedResponse(
                    message,
                    { message.readFrom() },
                    encoderOptions.name,
                    encoderOptions.encoder
                )
                is OutgoingContent.WriteChannelContent -> {
                    CompressedWriteResponse(
                        message,
                        encoderOptions.name,
                        encoderOptions.encoder
                    )
                }
                is OutgoingContent.ByteArrayContent -> CompressedResponse(
                    message,
                    { ByteReadChannel(message.bytes()) },
                    encoderOptions.name,
                    encoderOptions.encoder
                )
                is OutgoingContent.NoContent -> null
                is OutgoingContent.ProtocolUpgrade -> null
            }
        }
    }
}

private class CompressedResponse(
    val original: OutgoingContent,
    val delegateChannel: () -> ByteReadChannel,
    val encoding: String,
    val encoder: CompressionEncoder
) : OutgoingContent.ReadChannelContent() {
    override fun readFrom() = encoder.compress(delegateChannel())
    override val headers by lazy(LazyThreadSafetyMode.NONE) {
        Headers.build {
            appendFiltered(original.headers) { name, _ -> !name.equals(HttpHeaders.ContentLength, true) }
            append(HttpHeaders.ContentEncoding, encoding)
        }
    }

    override val contentType: ContentType? get() = original.contentType
    override val status: HttpStatusCode? get() = original.status
    override val contentLength: Long?
        get() = original.contentLength?.let { encoder.predictCompressedLength(it) }?.takeIf { it >= 0 }

    override fun <T : Any> getProperty(key: AttributeKey<T>) = original.getProperty(key)
    override fun <T : Any> setProperty(key: AttributeKey<T>, value: T?) = original.setProperty(key, value)
}

private class CompressedWriteResponse(
    val original: WriteChannelContent,
    val encoding: String,
    val encoder: CompressionEncoder
) : OutgoingContent.WriteChannelContent() {
    override val headers by lazy(LazyThreadSafetyMode.NONE) {
        Headers.build {
            appendFiltered(original.headers) { name, _ -> !name.equals(HttpHeaders.ContentLength, true) }
            append(HttpHeaders.ContentEncoding, encoding)
        }
    }

    override val contentType: ContentType? get() = original.contentType
    override val status: HttpStatusCode? get() = original.status
    override val contentLength: Long?
        get() = original.contentLength?.let { encoder.predictCompressedLength(it) }?.takeIf { it >= 0 }

    override fun <T : Any> getProperty(key: AttributeKey<T>) = original.getProperty(key)
    override fun <T : Any> setProperty(key: AttributeKey<T>, value: T?) = original.setProperty(key, value)

    override suspend fun writeTo(channel: ByteWriteChannel) {
        coroutineScope {
            encoder.compress(channel, coroutineContext).use {
                original.writeTo(this)
            }
        }
    }
}

private fun ApplicationCall.isCompressionSuppressed() = SuppressionAttribute in attributes
