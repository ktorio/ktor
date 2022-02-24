/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.features

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.util.*
import io.ktor.util.cio.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import java.util.*
import kotlin.coroutines.*

/**
 * The default minimal content size to compress
 */
internal const val DEFAULT_MINIMAL_COMPRESSION_SIZE: Long = 200L

/**
 * Compression feature configuration
 */
public data class CompressionOptions(
    /**
     * Map of encoder configurations
     */
    val encoders: Map<String, CompressionEncoderConfig> = emptyMap(),
    /**
     * Conditions for all encoders
     */
    val conditions: List<ApplicationCall.(OutgoingContent) -> Boolean> = emptyList()
)

/**
 * Configuration for an encoder
 */
public data class CompressionEncoderConfig(
    /**
     * Name of the encoder, matched against entry in `Accept-Encoding` header
     */
    val name: String,
    /**
     * Encoder implementation
     */
    val encoder: CompressionEncoder,
    /**
     * Conditions for the encoder
     */
    val conditions: List<ApplicationCall.(OutgoingContent) -> Boolean>,
    /**
     * Priority of the encoder
     */
    val priority: Double
)

/**
 * Feature to compress a response based on conditions and ability of client to decompress it
 */
public class Compression(compression: Configuration) {
    private val options = compression.buildOptions()
    private val comparator = compareBy<Pair<CompressionEncoderConfig, HeaderValue>>(
        { it.second.quality },
        { it.first.priority }
    ).reversed()

    private suspend fun interceptor(context: PipelineContext<Any, ApplicationCall>) {
        val call = context.call
        val message = context.subject
        val acceptEncodingRaw = call.request.acceptEncoding()
        if (acceptEncodingRaw == null || call.isCompressionSuppressed()) {
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
            return
        }

        if (message is OutgoingContent &&
            message !is CompressedResponse &&
            options.conditions.all { it(call, message) } &&
            !call.isCompressionSuppressed() &&
            message.headers[HttpHeaders.ContentEncoding].let { it == null || it != "identity" }
        ) {
            val encoderOptions = encoders.firstOrNull { encoder -> encoder.conditions.all { it(call, message) } }

            val channel: () -> ByteReadChannel = when (message) {
                is OutgoingContent.ReadChannelContent -> ({ message.readFrom() })
                is OutgoingContent.WriteChannelContent -> {
                    if (encoderOptions != null) {
                        val response = CompressedWriteResponse(message, encoderOptions.name, encoderOptions.encoder)
                        context.proceedWith(response)
                    }
                    return
                }
                is OutgoingContent.NoContent -> return
                is OutgoingContent.ByteArrayContent -> ({ ByteReadChannel(message.bytes()) })
                is OutgoingContent.ProtocolUpgrade -> return
            }

            if (encoderOptions != null) {
                val response = CompressedResponse(message, channel, encoderOptions.name, encoderOptions.encoder)
                context.proceedWith(response)
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

    /**
     * `ApplicationFeature` implementation for [Compression]
     */
    public companion object Feature : ApplicationFeature<ApplicationCallPipeline, Configuration, Compression> {
        /**
         * Attribute that could be added to an application call to prevent it's response from being compressed
         */
        public val SuppressionAttribute: AttributeKey<Boolean> = AttributeKey("preventCompression")

        override val key: AttributeKey<Compression> = AttributeKey("Compression")
        override fun install(pipeline: ApplicationCallPipeline, configure: Configuration.() -> Unit): Compression {
            val config = Configuration().apply(configure)
            if (config.encoders.none()) {
                config.default()
            }

            val feature = Compression(config)
            pipeline.sendPipeline.intercept(ApplicationSendPipeline.ContentEncoding) {
                feature.interceptor(this)
            }
            return feature
        }
    }

    /**
     * Configuration builder for Compression feature
     */
    public class Configuration : ConditionsHolderBuilder {
        /**
         * Encoders map by names
         */
        public val encoders: MutableMap<String, CompressionEncoderBuilder> = hashMapOf()

        override val conditions: MutableList<ApplicationCall.(OutgoingContent) -> Boolean> = arrayListOf()

        /**
         * Appends an encoder to the configuration
         */
        public fun encoder(
            name: String,
            encoder: CompressionEncoder,
            block: CompressionEncoderBuilder.() -> Unit = {}
        ) {
            require(name.isNotBlank()) { "encoder name couldn't be blank" }
            if (name in encoders) {
                throw IllegalArgumentException("Encoder $name is already registered")
            }

            encoders[name] = CompressionEncoderBuilder(name, encoder).apply(block)
        }

        /**
         * Appends default configuration having gzip and deflate.
         */
        public fun default() {
            gzip()
            deflate()
            identity()
        }

        /**
         * Builds `CompressionOptions`
         */
        internal fun buildOptions(): CompressionOptions = CompressionOptions(
            encoders = encoders.mapValues { (_, builder) ->
                if (conditions.none() && builder.conditions.none()) {
                    builder.defaultConditions()
                }

                builder.buildConfig()
            },
            conditions = conditions.toList()
        )

        /**
         * Builds `CompressionOptions`
         */
        @Deprecated(
            "This is going to become internal. " +
                "Please stop building it manually or file a ticket with explanation why do you need it."
        )
        public fun build(): CompressionOptions = buildOptions()
    }
}

private fun ApplicationCall.isCompressionSuppressed() = Compression.SuppressionAttribute in attributes

/**
 * Represents a Compression encoder
 */
public interface CompressionEncoder {
    /**
     * May predict compressed length based on the [originalLength] or return `null` if it is impossible.
     */
    public fun predictCompressedLength(originalLength: Long): Long? = null

    /**
     * Wraps [readChannel] into a compressing [ByteReadChannel]
     */
    public fun compress(
        readChannel: ByteReadChannel,
        coroutineContext: CoroutineContext = Dispatchers.Unconfined
    ): ByteReadChannel

    /**
     * Wraps [writeChannel] into a compressing [ByteWriteChannel]
     */
    public fun compress(
        writeChannel: ByteWriteChannel,
        coroutineContext: CoroutineContext = Dispatchers.Unconfined
    ): ByteWriteChannel
}

/**
 * Implementation of the gzip encoder
 */
public object GzipEncoder : CompressionEncoder {
    override fun compress(readChannel: ByteReadChannel, coroutineContext: CoroutineContext): ByteReadChannel =
        readChannel.deflated(true, coroutineContext = coroutineContext)

    override fun compress(writeChannel: ByteWriteChannel, coroutineContext: CoroutineContext): ByteWriteChannel =
        writeChannel.deflated(true, coroutineContext = coroutineContext)
}

/**
 * Implementation of the deflate encoder
 */
public object DeflateEncoder : CompressionEncoder {
    override fun compress(readChannel: ByteReadChannel, coroutineContext: CoroutineContext): ByteReadChannel =
        readChannel.deflated(false, coroutineContext = coroutineContext)

    override fun compress(writeChannel: ByteWriteChannel, coroutineContext: CoroutineContext): ByteWriteChannel =
        writeChannel.deflated(false, coroutineContext = coroutineContext)
}

/**
 *  Implementation of the identity encoder
 */
public object IdentityEncoder : CompressionEncoder {
    override fun predictCompressedLength(originalLength: Long): Long = originalLength

    override fun compress(
        readChannel: ByteReadChannel,
        coroutineContext: CoroutineContext
    ): ByteReadChannel = readChannel

    override fun compress(
        writeChannel: ByteWriteChannel,
        coroutineContext: CoroutineContext
    ): ByteWriteChannel = writeChannel
}

/**
 * Represents a builder for conditions
 */
public interface ConditionsHolderBuilder {
    /**
     * Preconditions applied to every response object to check if it should be compressed
     */
    public val conditions: MutableList<ApplicationCall.(OutgoingContent) -> Boolean>
}

/**
 * Builder for compression encoder configuration
 * @property name of encoder
 * @property encoder instance
 */
@Suppress("MemberVisibilityCanBePrivate")
public class CompressionEncoderBuilder internal constructor(
    public val name: String,
    public val encoder: CompressionEncoder
) : ConditionsHolderBuilder {
    /**
     * List of conditions for this encoder
     */
    override val conditions: ArrayList<ApplicationCall.(OutgoingContent) -> Boolean> = arrayListOf()

    /**
     * Priority for this encoder
     */
    public var priority: Double = 1.0

    /**
     * Builds [CompressionEncoderConfig] instance
     */
    @Deprecated(
        "This is going to become internal. " +
            "Please stop building it manually or file a ticket with explanation why do you need it."
    )
    public fun build(): CompressionEncoderConfig = buildConfig()

    internal fun buildConfig(): CompressionEncoderConfig {
        return CompressionEncoderConfig(name, encoder, conditions.toList(), priority)
    }
}

/**
 * Appends `gzip` encoder
 */
public fun Compression.Configuration.gzip(block: CompressionEncoderBuilder.() -> Unit = {}) {
    encoder("gzip", GzipEncoder, block)
}

/**
 * Appends `deflate` encoder with default priority of 0.9
 */
public fun Compression.Configuration.deflate(block: CompressionEncoderBuilder.() -> Unit = {}) {
    encoder("deflate", DeflateEncoder) {
        priority = 0.9
        block()
    }
}

/**
 * Appends `identity` encoder
 */
public fun Compression.Configuration.identity(block: CompressionEncoderBuilder.() -> Unit = {}) {
    encoder("identity", IdentityEncoder, block)
}

/**
 * Appends a custom condition to the encoder or Compression configuration.
 * A predicate returns `true` when a response need to be compressed.
 * If at least one condition is not met then the response compression is skipped.
 *
 * Please note that adding a single condition removes the default configuration.
 */
public fun ConditionsHolderBuilder.condition(predicate: ApplicationCall.(OutgoingContent) -> Boolean) {
    conditions.add(predicate)
}

/**
 * Appends a minimum size condition to the encoder or Compression configuration.
 *
 * Please note that adding a single minimum size condition removes the default configuration.
 */
public fun ConditionsHolderBuilder.minimumSize(minSize: Long) {
    condition { content -> content.contentLength?.let { it >= minSize } ?: true }
}

/**
 * Appends a content type condition to the encoder or Compression configuration.
 *
 * Please note that adding a single match condition removes the default configuration.
 */
public fun ConditionsHolderBuilder.matchContentType(vararg mimeTypes: ContentType) {
    condition { content ->
        val contentType = content.contentType ?: return@condition false
        mimeTypes.any { contentType.match(it) }
    }
}

/**
 * Appends a content type exclusion condition to the encoder or Compression configuration.
 *
 * Please note that adding a single match condition removes the default configuration.
 */
public fun ConditionsHolderBuilder.excludeContentType(vararg mimeTypes: ContentType) {
    condition { content ->
        val contentType = content.contentType
            ?: response.headers[HttpHeaders.ContentType]?.let { ContentType.parse(it) }
            ?: return@condition true

        mimeTypes.none { excludePattern -> contentType.match(excludePattern) }
    }
}

/**
 * Configures default compression options
 */
private fun ConditionsHolderBuilder.defaultConditions() {
    excludeContentType(
        ContentType.Video.Any,
        ContentType.Image.JPEG,
        ContentType.Image.PNG,
        ContentType.Audio.Any,
        ContentType.MultiPart.Any,
        ContentType.Text.EventStream
    )

    minimumSize(DEFAULT_MINIMAL_COMPRESSION_SIZE)
}
