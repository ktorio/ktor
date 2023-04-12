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
import io.ktor.util.logging.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.*

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

private object ContentDecoding : Hook<suspend ContentDecoding.Context.(ApplicationCall) -> Unit> {

    class Context(private val pipelineContext: PipelineContext<Any, ApplicationCall>) {
        fun transformBody(block: (ByteReadChannel) -> ByteReadChannel?) {
            val transformedContent = block(pipelineContext.subject as ByteReadChannel)
            if (transformedContent != null) {
                pipelineContext.subject = transformedContent
            }
        }
    }

    override fun install(
        pipeline: ApplicationCallPipeline,
        handler: suspend Context.(ApplicationCall) -> Unit
    ) {
        val beforeTransform = PipelinePhase("BeforeTransform")
        pipeline.receivePipeline.insertPhaseBefore(ApplicationReceivePipeline.Transform, beforeTransform)
        pipeline.receivePipeline.intercept(beforeTransform) {
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

    on(ContentEncoding) { call ->
        encode(call, options)
    }
    on(ContentDecoding) { call ->
        decode(call, options)
    }
}

private fun ContentDecoding.Context.decode(call: ApplicationCall, options: CompressionOptions) {
    val encodingRaw = call.request.headers[HttpHeaders.ContentEncoding]
    if (encodingRaw == null) {
        LOGGER.trace("Skip decompression for ${call.request.uri} because no content encoding provided.")
        return
    }
    val encoding = parseHeaderValue(encodingRaw)
    val encoders = encoding.mapNotNull { options.encoders[it.value] }
    if (encoders.isEmpty()) {
        LOGGER.trace("Skip decompression for ${call.request.uri} because no suitable encoders found.")
        return
    }
    if (encoding.size > encoders.size) {
        val missingEncoders = encoding.map { it.value } - encoders.map { it.encoder.name }.toSet()
        LOGGER.trace(
            "Skip some of decompression for ${call.request.uri} " +
                "because no suitable encoders found for $missingEncoders"
        )
    }
    transformBody { channel ->
        encoders.fold(channel) { content, encoder -> encoder.encoder.decode(content) }
    }
}

private fun ContentEncoding.Context.encode(call: ApplicationCall, options: CompressionOptions) {
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
        return@transformBody message.compressed(encoderOptions.encoder)
    }
}
