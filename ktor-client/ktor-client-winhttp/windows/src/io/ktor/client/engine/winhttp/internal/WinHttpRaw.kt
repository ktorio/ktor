package io.ktor.client.engine.winhttp.internal

import io.ktor.client.call.UnsupportedContentTypeException
import io.ktor.client.engine.mergeHeaders
import io.ktor.client.request.HttpRequest
import io.ktor.http.content.OutgoingContent
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.io.readRemaining
import kotlinx.coroutines.io.writer
import kotlinx.io.core.readBytes
import kotlin.coroutines.coroutineContext

internal class WinHttpResponseData(
    val status: Int,
    val version: String,
    val headers: String
)

internal suspend fun OutgoingContent.toByteArray(): ByteArray? = when (this@toByteArray) {
    is OutgoingContent.ByteArrayContent -> bytes()
    is OutgoingContent.WriteChannelContent -> GlobalScope.writer(coroutineContext) {
        writeTo(channel)
    }.channel.readRemaining().readBytes()
    is OutgoingContent.ReadChannelContent -> readFrom().readRemaining().readBytes()
    is OutgoingContent.NoContent -> null
    else -> throw UnsupportedContentTypeException(this@toByteArray)
}

internal fun HttpRequest.headersToList(): List<String> {
    val result = mutableListOf<String>()

    mergeHeaders(headers, content) { key, value ->
        val header = "$key: $value"
        result.add(header)
    }

    return result
}
