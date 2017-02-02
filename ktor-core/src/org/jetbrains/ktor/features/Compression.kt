package org.jetbrains.ktor.features

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.cio.*
import org.jetbrains.ktor.request.*
import org.jetbrains.ktor.util.*

data class CompressionOptions(
        val encoders: Map<String, CompressionEncoderConfig> = emptyMap(),
        val conditions: List<ApplicationCall.(FinalContent) -> Boolean> = emptyList()
)

data class CompressionEncoderConfig(val name: String,
                                    val encoder: CompressionEncoder,
                                    val conditions: List<ApplicationCall.(FinalContent) -> Boolean>,
                                    val priority: Double)

class Compression(compression: Configuration) {
    private val options = compression.build()
    private val comparator = compareBy<Pair<CompressionEncoderConfig, HeaderValue>>({ it.second.quality }, { it.first.priority }).reversed()

    suspend fun interceptor(call: ApplicationCall) {
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

        call.response.pipeline.intercept(RespondPipeline.ContentEncoding) {
            val message = subject.message
            if (message is FinalContent
                    && message !is CompressedResponse
                    && options.conditions.all { it(call, message) }
                    && !call.isCompressionSuppressed()
                    && message.headers[HttpHeaders.ContentEncoding].let { it == null || it == "identity" }
                    ) {

                val encoderOptions = encoders.firstOrNull { it.conditions.all { it(call, message) } }

                val channel: () -> ReadChannel = when (message) {
                    is FinalContent.ReadChannelContent ->  ({ message.readFrom() })
                    is FinalContent.WriteChannelContent -> {
                        if (encoderOptions != null) {
                            subject.message = CompressedWriteResponse(message, encoderOptions.name, encoderOptions.encoder)
                        }
                        return@intercept
                    }
                    is FinalContent.NoContent -> return@intercept
                    is FinalContent.ByteArrayContent -> ({ ByteBufferReadChannel(message.bytes()) })
                }

                if (encoderOptions != null) {
                    subject.message = CompressedResponse(channel, message.headers, encoderOptions.name, encoderOptions.encoder)
                }
            }
        }
    }

    private class CompressedResponse(val delegateChannel: () -> ReadChannel, val delegateHeaders: ValuesMap, val encoding: String, val encoder: CompressionEncoder) : FinalContent.ReadChannelContent() {
        override fun readFrom() = encoder.open(delegateChannel())
        override val headers by lazy {
            ValuesMap.build(true) {
                appendFiltered(delegateHeaders) { name, _ -> !name.equals(HttpHeaders.ContentLength, true) }
                append(HttpHeaders.ContentEncoding, encoding)
            }
        }
    }

    private class CompressedWriteResponse(val delegate: WriteChannelContent, val encoding: String, val encoder: CompressionEncoder) : FinalContent.WriteChannelContent() {
        override val headers by lazy {
            ValuesMap.build(true) {
                appendFiltered(delegate.headers) { name, _ -> !name.equals(HttpHeaders.ContentLength, true) }
                append(HttpHeaders.ContentEncoding, encoding)
            }
        }

        override suspend fun writeTo(channel: WriteChannel) {
            delegate.writeTo(encoder.open(channel))
        }
    }

    companion object Feature : ApplicationFeature<ApplicationCallPipeline, Configuration, Compression> {
        val SuppressionAttribute = AttributeKey<Boolean>("preventCompression")

        override val key = AttributeKey<Compression>("Compression")
        override fun install(pipeline: ApplicationCallPipeline, configure: Configuration.() -> Unit): Compression {
            val config = Configuration().apply(configure)
            if (config.encoders.none())
                config.default()

            val feature = Compression(config)
            pipeline.intercept(ApplicationCallPipeline.Infrastructure) { feature.interceptor(call) }
            return feature
        }
    }

    class Configuration() : ConditionsHolderBuilder {
        val encoders = hashMapOf<String, CompressionEncoderBuilder>()
        override val conditions = arrayListOf<ApplicationCall.(FinalContent) -> Boolean>()

        fun encoder(name: String, encoder: CompressionEncoder, block: CompressionEncoderBuilder.() -> Unit = {}) {
            require(name.isNotBlank()) { "encoder name couldn't be blank" }
            if (name in encoders) {
                throw IllegalArgumentException("Encoder $name is already registered")
            }

            encoders[name] = CompressionEncoderBuilder(name, encoder).apply(block)
        }

        fun default() {
            gzip()
            deflate()
            identity()
        }

        fun build() = CompressionOptions(
                encoders = encoders.mapValues { it.value.build() },
                conditions = conditions.toList()
        )
    }

}

private fun ApplicationCall.isCompressionSuppressed() = Compression.SuppressionAttribute in attributes

interface CompressionEncoder {
    fun open(delegate: ReadChannel): ReadChannel
    fun open(delegate: WriteChannel): WriteChannel
}

object GzipEncoder : CompressionEncoder {
    override fun open(delegate: ReadChannel) = delegate.deflated(true)
    override fun open(delegate: WriteChannel) = delegate.deflated(true)
}

object DeflateEncoder : CompressionEncoder {
    override fun open(delegate: ReadChannel) = delegate.deflated(false)
    override fun open(delegate: WriteChannel) = delegate.deflated(false)
}

object IdentityEncoder : CompressionEncoder {
    override fun open(delegate: ReadChannel) = delegate
    override fun open(delegate: WriteChannel) = delegate
}

interface ConditionsHolderBuilder {
    val conditions: MutableList<ApplicationCall.(FinalContent) -> Boolean>
}

class CompressionEncoderBuilder internal constructor(val name: String, val encoder: CompressionEncoder) : ConditionsHolderBuilder {
    override val conditions = arrayListOf<ApplicationCall.(FinalContent) -> Boolean>()
    var priority: Double = 1.0

    fun build(): CompressionEncoderConfig {
        return CompressionEncoderConfig(name, encoder, conditions.toList(), priority)
    }
}


fun Compression.Configuration.gzip(block: CompressionEncoderBuilder.() -> Unit = {}): Unit = encoder("gzip", GzipEncoder, block)
fun Compression.Configuration.deflate(block: CompressionEncoderBuilder.() -> Unit = {}): Unit = encoder("deflate", DeflateEncoder) {
    priority = 0.9
    block()
}

fun Compression.Configuration.identity(block: CompressionEncoderBuilder.() -> Unit = {}): Unit = encoder("identity", IdentityEncoder, block)

fun ConditionsHolderBuilder.condition(predicate: ApplicationCall.(FinalContent) -> Boolean) {
    conditions.add(predicate)
}

fun ConditionsHolderBuilder.minSize(minSize: Long) {
    condition { it.contentLength()?.let { it >= minSize } ?: true }
}

fun ConditionsHolderBuilder.mimeTypeShouldMatch(vararg mimeTypes: ContentType) {
    condition {
        it.contentType()?.let { mimeType ->
            mimeTypes.any {
                mimeType.match(it)
            }
        } ?: false
    }
}

fun ConditionsHolderBuilder.excludeMimeTypeMatch(mimeType: ContentType) {
    condition {
        it.contentType()?.let { actual ->
            !actual.match(mimeType)
        } ?: true
    }
}
