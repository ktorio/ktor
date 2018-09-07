package io.ktor.client.request.forms

import io.ktor.http.*
import io.ktor.http.content.*
import kotlinx.coroutines.io.*
import kotlinx.io.charsets.*
import kotlinx.io.core.*
import kotlin.random.*

/**
 * [OutgoingContent] with [formData] for application/x-www-form-urlencoded formatted request.
 */
class FormDataContent(
    val formData: Parameters
) : OutgoingContent.ByteArrayContent() {
    private val content = formData.formUrlEncode().toByteArray()

    override val contentLength: Long = content.size.toLong()
    override val contentType: ContentType = ContentType.Application.FormUrlEncoded.withCharset(Charsets.UTF_8)

    override fun bytes(): ByteArray = content
}

/**
 * [OutgoingContent] with [parts] for multipart/form-data formatted request.
 */
class MultiPartFormDataContent(
    private val parts: List<PartData>
) : OutgoingContent.WriteChannelContent() {
    private val boundary: String = generateBoundary()

    override val contentType: ContentType = ContentType.MultiPart.FormData.withParameter("boundary", boundary)

    override suspend fun writeTo(channel: ByteWriteChannel) {
        try {
            if (parts.isEmpty()) return

            channel.writeStringUtf8("\r\n\r\n")
            parts.forEach {
                channel.writeStringUtf8("--$boundary\r\n")
                for ((key, values) in it.headers.entries()) {
                    channel.writeStringUtf8("$key: ${values.joinToString(";")}\r\n")
                }
                channel.writeStringUtf8("\r\n")
                when (it) {
                    // TODO: replace with writeFully(input)
                    is PartData.FileItem -> channel.writeFully(it.provider().readBytes())
                    is PartData.FormItem -> channel.writeStringUtf8(it.value)
                    is PartData.BinaryItem -> channel.writeFully(it.provider().readBytes())
                }
                channel.writeStringUtf8("\r\n")
            }

            channel.writeStringUtf8("--$boundary--\r\n\r\n")
        } catch (cause: Throwable) {
            channel.close(cause)
        } finally {
            parts.forEach { it.dispose() }
            channel.close()
        }
    }
}

private fun generateBoundary(): String = buildString {
    repeat(32) {
        append(Random.nextInt().toString(16))
    }
}.take(70)
