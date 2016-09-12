package org.jetbrains.ktor.features.http

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.nio.*
import org.jetbrains.ktor.request.*
import org.jetbrains.ktor.util.*
import kotlin.comparisons.*

object CompressionAttributes {
    val preventCompression = AttributeKey<Boolean>("preventCompression")
}

class Compression {
    var options = CompressionOptions()
        private set

    fun configure(block: CompressionBuilder.() -> Unit) {
        options = CompressionBuilder().apply(block).build()
    }

    fun configureDefault(block: CompressionBuilder.() -> Unit = {}) {
        configure {
            gzip()
            deflate()
            identity()

            block()
        }
    }
}

data class CompressionOptions(
        val compressorRegistry: Map<String, CompressionEncoderConfig> = emptyMap(),
        val conditions: List<ApplicationCall.(FinalContent) -> Boolean> = emptyList()
)

data class CompressionEncoderConfig(val name: String,
                                    val encoder: CompressionEncoder,
                                    val conditions: List<ApplicationCall.(FinalContent) -> Boolean>,
                                    val priority: Double)

object CompressionSupport : ApplicationFeature<ApplicationCallPipeline, Compression, Compression> {
    override val key = AttributeKey<Compression>("Compression")
    private val Comparator = compareBy<Pair<CompressionEncoderConfig, HeaderValue>>({ it.second.quality }, { it.first.priority }).reversed()

    override fun install(pipeline: ApplicationCallPipeline, configure: Compression.() -> Unit): Compression {
        val compressionObj = Compression()
        compressionObj.configureDefault()

        compressionObj.configure()

        pipeline.intercept(ApplicationCallPipeline.Infrastructure) { call ->
            val acceptEncodingRaw = call.request.acceptEncoding()
            val options = compressionObj.options

            if (acceptEncodingRaw != null && !call.isCompressionProhibited()) {
                val encoders = parseHeaderValue(acceptEncodingRaw)
                        .filter { it.value == "*" || it.value in options.compressorRegistry }
                        .flatMap { header ->
                            when (header.value) {
                                "*" -> options.compressorRegistry.values.map { it to header }
                                else -> options.compressorRegistry[header.value]?.let { listOf(it to header) } ?: emptyList()
                            }
                        }
                        .sortedWith(Comparator)
                        .map { it.first }

                if (encoders.isNotEmpty()) {
                    call.response.pipeline.intercept(RespondPipeline.After) {
                        val message = subject.message
                        if (message is FinalContent
                                && message !is CompressedResponse
                                && options.conditions.all { it(call, message) }
                                && !call.isCompressionProhibited()
                                && message.contentEncoding().let { it == null || it == "identity" }
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
            }
        }

        return compressionObj
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

private fun FinalContent.contentEncoding(): String? {
    if (this is CompressedResponse) {
        return encoding
    }

    return headers[HttpHeaders.ContentEncoding]
}

private fun ApplicationCall.isCompressionProhibited() = CompressionAttributes.preventCompression in attributes

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

class CompressionBuilder internal constructor() : ConditionsHolderBuilder {
    val encoders = hashMapOf<String, CompressionEncoderBuilder>()
    override val conditions = arrayListOf<ApplicationCall.(FinalContent) -> Boolean>()

    fun build() = CompressionOptions(
            compressorRegistry = encoders.mapValues { it.value.build() },
            conditions = conditions.toList()
    )
}

class CompressionEncoderBuilder internal constructor(val name: String, val encoder: CompressionEncoder) : ConditionsHolderBuilder {
    override val conditions = arrayListOf<ApplicationCall.(FinalContent) -> Boolean>()
    var priority: Double = 1.0

    fun build(): CompressionEncoderConfig {
        return CompressionEncoderConfig(name, encoder, conditions.toList(), priority)
    }
}

fun CompressionBuilder.encoder(name: String, encoder: CompressionEncoder, block: CompressionEncoderBuilder.() -> Unit = {}) {
    require(name.isNotBlank()) { "encoder name couldn't be blank" }

    if (name in encoders) {
        throw IllegalArgumentException("Encoder $name is already registered")
    }

    encoders[name] = CompressionEncoderBuilder(name, encoder).apply(block)
}

fun CompressionBuilder.gzip(block: CompressionEncoderBuilder.() -> Unit = {}): Unit = encoder("gzip", GzipEncoder, block)
fun CompressionBuilder.deflate(block: CompressionEncoderBuilder.() -> Unit = {}): Unit = encoder("deflate", DeflateEncoder) {
    priority = 0.9
    block()
}

fun CompressionBuilder.identity(block: CompressionEncoderBuilder.() -> Unit = {}): Unit = encoder("identity", IdentityEncoder, block)

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
