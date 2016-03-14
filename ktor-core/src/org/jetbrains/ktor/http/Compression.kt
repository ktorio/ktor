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
                              var defaultEncoding: String = "deflate",
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
            if (encoding != null && encoder != null) {
                call.response.headers.intercept { name, value, next ->
                    if (name.equals(HttpHeaders.ContentLength, true)) {
                        call.attributes.put(CompressionAttributes.interceptedContentLength, value.toLong())
                    } else {
                        next(name, value)
                    }
                }

                call.response.interceptStream { content, stream ->
                    if (conditions.all { it(call) } && CompressionAttributes.preventCompression !in attributes) {
                        call.response.headers.append(HttpHeaders.ContentEncoding, encoding)
                        stream {
                            encoder.open(this).apply {
                                content()
                                close()
                            }
                        }
                    } else {
                        stream {
                            content()
                        }
                    }
                }

                call.interceptRespond { obj ->
                    if (conditions.all { it(call) } && CompressionAttributes.preventCompression !in attributes) {
                        if (obj is ChannelContentProvider) {
                            call.response.headers.append(HttpHeaders.ContentEncoding, encoding)

                            respond(object : ChannelContentProvider {
                                override fun channel() = obj.channel().deflated()
                            })
                        }
                    }
                }
            }
        }
    }
}

private fun String.handleStar(options: CompressionOptions) = if (this == "*") options.defaultEncoding else this

interface CompressionEncoder {
    fun open(stream: OutputStream): OutputStream
}

private object GzipEncoder : CompressionEncoder {
    override fun open(stream: OutputStream): OutputStream = GZIPOutputStream(stream)
}

private object DeflateEncoder : CompressionEncoder {
    override fun open(stream: OutputStream): OutputStream = DeflaterOutputStream(stream, Deflater(Deflater.BEST_COMPRESSION, true))
}

private object IdentityEncoder : CompressionEncoder {
    override fun open(stream: OutputStream): OutputStream = stream
}

private fun minSizeCondition(options: CompressionOptions): ApplicationCall.() -> Boolean = {
    val contentLength = attributes.getOrNull(CompressionAttributes.interceptedContentLength)

    contentLength == null || contentLength >= options.minSize
}

private fun compressStreamCondition(options: CompressionOptions): ApplicationCall.() -> Boolean = {
    val contentLength = attributes.getOrNull(CompressionAttributes.interceptedContentLength)

    options.compressStream || contentLength != null
}
