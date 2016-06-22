package org.jetbrains.ktor.http

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.nio.*
import org.jetbrains.ktor.util.*

object CompressionAttributes {
    val preventCompression = AttributeKey<Boolean>("preventCompression")
}

data class CompressionOptions(var minSize: Long = 0L,
                              var compressStream: Boolean = true,
                              var defaultEncoding: String = "gzip",
                              val compressorRegistry: MutableMap<String, CompressionEncoder> = hashMapOf(),
                              val conditions: MutableList<ApplicationCall.(FinalContent) -> Boolean> = arrayListOf()
)


object CompressionSupport : ApplicationFeature<CompressionOptions> {
    override val name: String
        get() = "Compression"

    override val key = AttributeKey<CompressionOptions>("compression-key")

    override fun install(application: Application, configure: CompressionOptions.() -> Unit): CompressionOptions {
        val options = CompressionOptions()
        options.configure()

        val supportedEncodings = setOf("gzip", "deflate", "identity", "*") + options.compressorRegistry.keys
        val encoders = mapOf("gzip" to GzipEncoder, "deflate" to DeflateEncoder, "identity" to IdentityEncoder) + options.compressorRegistry
        val conditions = listOf(minSizeCondition(options), compressStreamCondition(options), { obj ->
            obj.contentEncoding().let { it == null || it == "identity" }
        }) + options.conditions

        application.intercept(ApplicationCallPipeline.Infrastructure) { call ->
            val acceptEncodingRaw = call.request.acceptEncoding()
            if (acceptEncodingRaw != null) {
                val encoding = parseAndSortHeader(acceptEncodingRaw)
                        .firstOrNull { it.value in supportedEncodings }
                        ?.value
                        ?.handleStar(options)

                val encoder = encoding?.let { encoders[it] }
                if (encoding != null && encoder != null && !call.isCompressionProhibited()) {
                    call.respond.intercept(RespondPipeline.After) {
                        val message = subject.message
                        if (message is FinalContent && message !is CompressedResponse && conditions.all { it(call, message) } && !call.isCompressionProhibited()) {
                            val channel = when (message) {
                                is FinalContent.ChannelContent -> message.channel()
                                is FinalContent.StreamContentProvider -> message.stream().asAsyncChannel()
                                else -> proceed()
                            }

                            call.respond(CompressedResponse(channel, message.headers, encoding, encoder))
                        }
                    }
                }
            }
        }

        return options
    }
}

private class CompressedResponse(val delegateChannel: AsyncReadChannel, val delegateHeaders: ValuesMap, val encoding: String, val encoder: CompressionEncoder) : FinalContent.ChannelContent() {
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

private fun String.handleStar(options: CompressionOptions) = if (this == "*") options.defaultEncoding else this

interface CompressionEncoder {
    fun open(delegate: AsyncReadChannel): AsyncReadChannel
}

private object GzipEncoder : CompressionEncoder {
    override fun open(delegate: AsyncReadChannel) = delegate.deflated(true)
}

private object DeflateEncoder : CompressionEncoder {
    override fun open(delegate: AsyncReadChannel) = delegate.deflated(false)
}

private object IdentityEncoder : CompressionEncoder {
    override fun open(delegate: AsyncReadChannel) = delegate
}

private fun minSizeCondition(options: CompressionOptions): ApplicationCall.(FinalContent) -> Boolean = { obj ->
    obj.contentLength()?.let { it >= options.minSize } ?: true
}

private fun compressStreamCondition(options: CompressionOptions): ApplicationCall.(FinalContent) -> Boolean = { obj ->
    options.compressStream || obj.contentLength() != null
}
