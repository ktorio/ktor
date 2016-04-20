package org.jetbrains.ktor.http

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.nio.*
import org.jetbrains.ktor.util.*
import java.io.*
import java.util.zip.*

object CompressionAttributes {
    val preventCompression = AttributeKey<Boolean>("preventCompression")
    val interceptedContentLength = AttributeKey<Long>("contentLength")
}

data class CompressionOptions(var minSize: Long = 0L,
                              var compressStream: Boolean = true,
                              var defaultEncoding: String = "gzip",
                              val compressorRegistry: MutableMap<String, CompressionEncoder> = hashMapOf(),
                              val conditions: MutableList<ApplicationCall.() -> Boolean> = arrayListOf()
)

fun Application.setupCompression() {
    setupCompression { }
}

fun Application.setupCompression(configure: CompressionOptions.() -> Unit) {
    val options = CompressionOptions()
    options.configure()
    val supportedEncodings = setOf("gzip", "deflate", "identity", "*") + options.compressorRegistry.keys
    val encoders = mapOf("gzip" to GzipEncoder, "deflate" to DeflateEncoder, "identity" to IdentityEncoder) + options.compressorRegistry
    val conditions = listOf(minSizeCondition(options), compressStreamCondition(options)) + options.conditions

    intercept { call ->
        val acceptEncodingRaw = call.request.acceptEncoding()
        if (acceptEncodingRaw != null) {
            val encoding = parseAndSortHeader(acceptEncodingRaw)
                    .firstOrNull { it.value in supportedEncodings }
                    ?.value
                    ?.handleStar(options)

            val encoder = encoding?.let { encoders[it] }
            if (encoding != null && encoder != null && !call.isCompressedProhibited()) {
                call.response.headers.intercept { name, value, next ->
                    if (name.equals(HttpHeaders.ContentLength, true)) {
                        call.attributes.put(CompressionAttributes.interceptedContentLength, value.toLong())
                    } else {
                        next(name, value)
                    }
                }

                call.interceptRespond(0) { obj ->
                    if (conditions.all { it(call) } && !call.isCompressedProhibited()) {
                        if (obj is CompressedChannelProvider) {
                            proceed()
                        }
                        if (obj is ChannelContentProvider) {
                            call.response.headers.append(HttpHeaders.ContentEncoding, encoding)
                            call.respond(CompressedChannelProvider(obj.channel(), encoder))
                        }
                    }
                }
            }
        }
    }
}

private fun ApplicationCall.isCompressedProhibited() = CompressionAttributes.preventCompression in attributes

private class CompressedChannelProvider(val delegate: AsyncReadChannel, val encoder: CompressionEncoder) : ChannelContentProvider {
    override fun channel() = encoder.open(delegate)
}

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

private fun minSizeCondition(options: CompressionOptions): ApplicationCall.() -> Boolean = {
    val contentLength = attributes.getOrNull(CompressionAttributes.interceptedContentLength)

    contentLength == null || contentLength >= options.minSize
}

private fun compressStreamCondition(options: CompressionOptions): ApplicationCall.() -> Boolean = {
    val contentLength = attributes.getOrNull(CompressionAttributes.interceptedContentLength)

    options.compressStream || contentLength != null
}
