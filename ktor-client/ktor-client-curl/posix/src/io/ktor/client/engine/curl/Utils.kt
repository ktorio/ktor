package io.ktor.client.engine.curl

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*
import kotlinx.cinterop.*
import kotlinx.io.charsets.*
import platform.posix.*
import kotlin.native.concurrent.*

internal fun ByteArray.copyToBuffer(buffer: CPointer<ByteVar>, size: ULong, position: Int = 0) {
    this.usePinned { pinned ->
        memcpy(buffer, pinned.addressOf(position), size)
    }
}

@Deprecated("We should come up with a proper ByteArray.toString(Charset) for native eventually.")
internal fun ByteArray.toString(charset: Charset = Charsets.UTF_8): String {
    if (charset != Charsets.UTF_8) throw CurlHttpRequestException("Unsupported charset: $charset")
    return this.stringFromUtf8()
}

internal inline fun <T: Any> T.stableCPointer() = StableRef.create(this).asCPointer()

internal inline fun <reified T: Any> COpaquePointer.fromCPointer() = this.asStableRef<T>().get()

internal fun List<ByteArray>.parseResponseHeaders(charset: Charset = Charsets.UTF_8): Headers {
    val headers = mutableMapOf<String, MutableList<String>>()
    this.forEach { byteArray ->
        val header: String = byteArray.toString(charset)

        // TODO: We need a better way to parse headers.
        // TODO: And also we need a support for multiline headers.
        if (header.contains(":")) {
            val split = header.split(":", limit = 2)
            val key = split[0]
            val value = split[1]
            val valueList: MutableList<String> = headers.getOrPut(key) { mutableListOf<String>() }
            valueList.add(value)
        }
    }
    return HeadersImpl(headers)
}

internal fun HttpRequest.toCurlRequest(): CurlRequest {
    val curlRequestData = CurlRequestData(
        url = url.toString(),              // TODO: May be we need some other way to convert URL for libcurl?
        method = method.value,
        headers = headersToCurl(this),      // TODO: curl_slist_free_all(headerList)
        content = content.toCurlByteArray()
    )

    return CurlRequest(listOf(curlRequestData), ListenerKey().freeze())
}

internal fun OutgoingContent.toCurlByteArray(): ByteArray? {
    val body = when (this) {
        is OutgoingContent.ByteArrayContent -> this.bytes()
        //is OutgoingContent.WriteChannelContent -> writer(dispatcher) {
        //    this.writeTo(channel)
        //}.channel.readRemaining().readBytes()
        //is OutgoingContent.ReadChannelContent -> this.readFrom().readRemaining().readBytes()
        is OutgoingContent.NoContent -> null
        else -> throw UnsupportedContentTypeException(this)
    }
    return body
}

class CurlHttpRequestException(cause: String) : IllegalStateException(cause)

class CurlIllegalStateException(cause: String) : IllegalStateException(cause)

class CurlEngineCreationException(cause: String): IllegalStateException(cause)

class CurlUnsupportedProtocolException(protocolId: UInt): IllegalArgumentException("Unsupported protocol $protocolId")
