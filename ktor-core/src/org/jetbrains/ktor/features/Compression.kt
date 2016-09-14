package org.jetbrains.ktor.features

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.nio.*
import org.jetbrains.ktor.request.*
import org.jetbrains.ktor.util.*
import kotlin.comparisons.*

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

    fun intercept(call: ApplicationCall) {
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

        call.response.pipeline.intercept(RespondPipeline.After) {
            val message = subject.message
            if (message is FinalContent
                    && message !is CompressedResponse
                    && options.conditions.all { it(call, message) }
                    && !call.isCompressionSuppressed()
                    && message.headers[HttpHeaders.ContentEncoding].let { it == null || it == "identity" }
            ) {
                val channel = when (message) {
                    is FinalContent.ChannelContent -> message.channel()
                    is FinalContent.StreamContentProvider -> message.stream().asAsyncChannel()
                    else -> proceed()
                }

                val encoderOptions = encoders.firstOrNull { it.conditions.all { it(call, message) } }

                if (encoderOptions != null) {
                    call.respond(CompressedResponse(channel, message.headers, encoderOptions.name, encoderOptions.encoder))
                }
            }
        }
    }

    private class CompressedResponse(val delegateChannel: ReadChannel, val delegateHeaders: ValuesMap, val encoding: String, val encoder: CompressionEncoder) : FinalContent.ChannelContent() {
        override fun channel() = encoder.open(delegateChannel)
        override val headers by lazy {
            ValuesMap.build(true) {
                appendAll(delegateHeaders.filter { name, value ->
                    !name.equals(HttpHeaders.ContentLength, true)
                })

                append(HttpHeaders.ContentEncoding, encoding)
            }
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
            pipeline.intercept(ApplicationCallPipeline.Infrastructure) { feature.intercept(call) }
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
}

object GzipEncoder : CompressionEncoder {
    override fun open(delegate: ReadChannel) = delegate.deflated(true)
}

object DeflateEncoder : CompressionEncoder {
    override fun open(delegate: ReadChannel) = delegate.deflated(false)
}

object IdentityEncoder : CompressionEncoder {
    override fun open(delegate: ReadChannel) = delegate
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
