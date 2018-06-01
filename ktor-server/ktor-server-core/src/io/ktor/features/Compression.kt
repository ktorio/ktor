package io.ktor.features

import io.ktor.application.*
import io.ktor.cio.*
import io.ktor.http.content.*
import io.ktor.http.*
import io.ktor.pipeline.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.util.*
import kotlinx.coroutines.experimental.io.*

/**
 * Compression feature configuration
 */
data class CompressionOptions(
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
data class CompressionEncoderConfig(
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
        val priority: Double)

/**
 * Feature to compress a response based on conditions and ability of client to decompress it
 */
class Compression(compression: Configuration) {
    private val options = compression.build()
    private val comparator = compareBy<Pair<CompressionEncoderConfig, HeaderValue>>({ it.second.quality }, { it.first.priority }).reversed()

    private suspend fun interceptor(context: PipelineContext<Any, ApplicationCall>) {
        val call = context.call
        val message = context.subject
        val acceptEncodingRaw = call.request.acceptEncoding()
        if (acceptEncodingRaw == null || call.isCompressionSuppressed())
            return

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

        if (!encoders.isNotEmpty())
            return

        if (message is OutgoingContent
                && message !is CompressedResponse
                && options.conditions.all { it(call, message) }
                && !call.isCompressionSuppressed()
                && message.headers[HttpHeaders.ContentEncoding].let { it == null || it != "identity" }
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

    private class CompressedResponse(val original: OutgoingContent,
                                     val delegateChannel: () -> ByteReadChannel,
                                     val encoding: String,
                                     val encoder: CompressionEncoder) : OutgoingContent.ReadChannelContent() {
        override fun readFrom() = encoder.compress(delegateChannel())
        override val headers by lazy(LazyThreadSafetyMode.NONE) {
            Headers.build {
                appendFiltered(original.headers) { name, _ -> !name.equals(HttpHeaders.ContentLength, true) }
                append(HttpHeaders.ContentEncoding, encoding)
            }
        }

        override val contentType: ContentType? get() = original.contentType
        override val status: HttpStatusCode? get() = original.status
        override fun <T : Any> getProperty(key: AttributeKey<T>) = original.getProperty(key)
        override fun <T : Any> setProperty(key: AttributeKey<T>, value: T?) = original.setProperty(key, value)
    }

    private class CompressedWriteResponse(val original: WriteChannelContent,
                                          val encoding: String,
                                          val encoder: CompressionEncoder) : OutgoingContent.WriteChannelContent() {
        override val headers by lazy(LazyThreadSafetyMode.NONE) {
            Headers.build {
                appendFiltered(original.headers) { name, _ -> !name.equals(HttpHeaders.ContentLength, true) }
                append(HttpHeaders.ContentEncoding, encoding)
            }
        }

        override val contentType: ContentType? get() = original.contentType
        override val status: HttpStatusCode? get() = original.status
        override fun <T : Any> getProperty(key: AttributeKey<T>) = original.getProperty(key)
        override fun <T : Any> setProperty(key: AttributeKey<T>, value: T?) = original.setProperty(key, value)

        override suspend fun writeTo(channel: ByteWriteChannel) {
            encoder.compress(channel).use {
                original.writeTo(this)
            }
        }
    }

    /**
     * `ApplicationFeature` implementation for [Compression]
     */
    companion object Feature : ApplicationFeature<ApplicationCallPipeline, Configuration, Compression> {
        val SuppressionAttribute = AttributeKey<Boolean>("preventCompression")

        override val key = AttributeKey<Compression>("Compression")
        override fun install(pipeline: ApplicationCallPipeline, configure: Configuration.() -> Unit): Compression {
            val config = Configuration().apply(configure)
            if (config.encoders.none())
                config.default()

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
    class Configuration() : ConditionsHolderBuilder {
        val encoders = hashMapOf<String, CompressionEncoderBuilder>()
        override val conditions = arrayListOf<ApplicationCall.(OutgoingContent) -> Boolean>()

        /**
         * Appends an encoder to the configuration
         */
        fun encoder(name: String, encoder: CompressionEncoder, block: CompressionEncoderBuilder.() -> Unit = {}) {
            require(name.isNotBlank()) { "encoder name couldn't be blank" }
            if (name in encoders) {
                throw IllegalArgumentException("Encoder $name is already registered")
            }

            encoders[name] = CompressionEncoderBuilder(name, encoder).apply(block)
        }

        /**
         * Appends default configuration
         */
        fun default() {
            gzip()
            deflate()
            identity()
        }

        /**
         * Builds `CompressionOptions`
         */
        fun build() = CompressionOptions(
                encoders = encoders.mapValues { it.value.build() },
                conditions = conditions.toList()
        )
    }

}

private fun ApplicationCall.isCompressionSuppressed() = Compression.SuppressionAttribute in attributes

/**
 * Represents a Compression encoder
 */
interface CompressionEncoder {
    /**
     * Wraps [readChannel] into a compressing [ByteReadChannel]
     */
    fun compress(readChannel: ByteReadChannel): ByteReadChannel

    /**
     * Wraps [writeChannel] into a compressing [ByteWriteChannel]
     */
    fun compress(writeChannel: ByteWriteChannel): ByteWriteChannel
}

/**
 * Implementation of the gzip encoder
 */
object GzipEncoder : CompressionEncoder {
    override fun compress(readChannel: ByteReadChannel) = readChannel.deflated(true)
    override fun compress(writeChannel: ByteWriteChannel) = writeChannel.deflated(true)
}

/**
 * Implementation of the deflate encoder
 */
object DeflateEncoder : CompressionEncoder {
    override fun compress(readChannel: ByteReadChannel) = readChannel.deflated(false)
    override fun compress(writeChannel: ByteWriteChannel) = writeChannel.deflated(false)
}

/**
 *  Implementation of the identity encoder
 */
object IdentityEncoder : CompressionEncoder {
    override fun compress(readChannel: ByteReadChannel) = readChannel
    override fun compress(writeChannel: ByteWriteChannel) = writeChannel
}

/**
 * Represents a builder for conditions
 */
interface ConditionsHolderBuilder {
    val conditions: MutableList<ApplicationCall.(OutgoingContent) -> Boolean>
}

/**
 * Builder for compression encoder configuration
 */
class CompressionEncoderBuilder internal constructor(val name: String, val encoder: CompressionEncoder) : ConditionsHolderBuilder {
    /**
     * List of conditions for this encoder
     */
    override val conditions = arrayListOf<ApplicationCall.(OutgoingContent) -> Boolean>()

    /**
     * Priority for this encoder
     */
    var priority: Double = 1.0

    /**
     * Builds [CompressionEncoderConfig] instance
     */
    fun build(): CompressionEncoderConfig {
        return CompressionEncoderConfig(name, encoder, conditions.toList(), priority)
    }
}


/**
 * Appends `gzip` encoder
 */
fun Compression.Configuration.gzip(block: CompressionEncoderBuilder.() -> Unit = {}) {
    encoder("gzip", GzipEncoder, block)
}

/**
 * Appends `deflate` encoder with default priority of 0.9
 */
fun Compression.Configuration.deflate(block: CompressionEncoderBuilder.() -> Unit = {}) {
    encoder("deflate", DeflateEncoder) {
        priority = 0.9
        block()
    }
}

/**
 * Appends `identity` encoder
 */
fun Compression.Configuration.identity(block: CompressionEncoderBuilder.() -> Unit = {}) {
    encoder("identity", IdentityEncoder, block)
}

/**
 * Appends a custom condition to the encoder or Compression configuration
 */
fun ConditionsHolderBuilder.condition(predicate: ApplicationCall.(OutgoingContent) -> Boolean) {
    conditions.add(predicate)
}

/**
 * Appends a minimum size condition to the encoder or Compression configuration
 */
fun ConditionsHolderBuilder.minimumSize(minSize: Long) {
    condition { content -> content.contentLength?.let { it >= minSize } ?: true }
}

/**
 * Appends a content type condition to the encoder or Compression configuration
 */
fun ConditionsHolderBuilder.matchContentType(vararg mimeTypes: ContentType) {
    condition { content ->
        val contentType = content.contentType ?: return@condition false
        mimeTypes.any { contentType.match(it) }
    }
}

/**
 * Appends a content type exclusion condition to the encoder or Compression configuration
 */
fun ConditionsHolderBuilder.excludeContentType(vararg mimeTypes: ContentType) {
    condition { content ->
        val contentType = content.contentType ?: return@condition true
        mimeTypes.none { contentType.match(it) }
    }
}
