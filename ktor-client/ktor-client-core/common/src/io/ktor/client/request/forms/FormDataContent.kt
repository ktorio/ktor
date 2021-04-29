/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.request.forms

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import kotlin.native.concurrent.*
import kotlin.random.*

@ThreadLocal
private val RN_BYTES = "\r\n".toByteArray()

/**
 * [OutgoingContent] with for application/x-www-form-urlencoded formatted request.
 *
 * @param formData: data to send.
 */
public class FormDataContent(
    public val formData: Parameters
) : OutgoingContent.ByteArrayContent() {
    private val content = formData.formUrlEncode().toByteArray()

    override val contentLength: Long = content.size.toLong()
    override val contentType: ContentType = ContentType.Application.FormUrlEncoded.withCharset(Charsets.UTF_8)

    override fun bytes(): ByteArray = content
}

/**
 * [OutgoingContent] for multipart/form-data formatted request.
 *
 * @param parts: form part data
 */
public class MultiPartFormDataContent(
    parts: List<PartData>
) : OutgoingContent.WriteChannelContent() {
    private val boundary: String = generateBoundary()
    private val BOUNDARY_BYTES = "--$boundary\r\n".toByteArray()
    private val LAST_BOUNDARY_BYTES = "--$boundary--\r\n".toByteArray()

    private val BODY_OVERHEAD_SIZE = LAST_BOUNDARY_BYTES.size
    private val PART_OVERHEAD_SIZE = RN_BYTES.size * 2 + BOUNDARY_BYTES.size

    private val rawParts: List<PreparedPart> = parts.map { part ->
        val headersBuilder = BytePacketBuilder()
        for ((key, values) in part.headers.entries()) {
            headersBuilder.writeText("$key: ${values.joinToString("; ")}")
            headersBuilder.writeFully(RN_BYTES)
        }

        val bodySize = part.headers[HttpHeaders.ContentLength]?.toLong()
        when (part) {
            is PartData.FileItem -> {
                val headers = headersBuilder.build().readBytes()
                val size = bodySize?.plus(PART_OVERHEAD_SIZE)?.plus(headers.size)
                PreparedPart(headers, part.provider, size)
            }
            is PartData.BinaryItem -> {
                val headers = headersBuilder.build().readBytes()
                val size = bodySize?.plus(PART_OVERHEAD_SIZE)?.plus(headers.size)
                PreparedPart(headers, part.provider, size)
            }
            is PartData.FormItem -> {
                val bytes = buildPacket { writeText(part.value) }.readBytes()
                val provider = { buildPacket { writeFully(bytes) } }
                if (bodySize == null) {
                    headersBuilder.writeText("${HttpHeaders.ContentLength}: ${bytes.size}")
                    headersBuilder.writeFully(RN_BYTES)
                }

                val headers = headersBuilder.build().readBytes()
                val size = bytes.size + PART_OVERHEAD_SIZE + headers.size
                PreparedPart(headers, provider, size.toLong())
            }
        }
    }

    override val contentLength: Long?

    override val contentType: ContentType = ContentType.MultiPart.FormData.withParameter("boundary", boundary)

    init {
        var rawLength: Long? = 0
        for (part in rawParts) {
            val size = part.size
            if (size == null) {
                rawLength = null
                break
            }

            rawLength = rawLength?.plus(size)
        }

        if (rawLength != null) {
            rawLength += BODY_OVERHEAD_SIZE
        }

        contentLength = rawLength
    }

    override suspend fun writeTo(channel: ByteWriteChannel) {
        try {
            for (part in rawParts) {
                channel.writeFully(BOUNDARY_BYTES)
                channel.writeFully(part.headers)
                channel.writeFully(RN_BYTES)

                part.provider().use { input ->
                    input.copyTo(channel)
                }

                channel.writeFully(RN_BYTES)
            }

            channel.writeFully(LAST_BOUNDARY_BYTES)
        } catch (cause: Throwable) {
            channel.close(cause)
        } finally {
            channel.close()
        }
    }
}

private fun generateBoundary(): String = buildString {
    repeat(32) {
        append(Random.nextInt().toString(16))
    }
}.take(70)

private class PreparedPart(
    val headers: ByteArray,
    val provider: () -> Input,
    val size: Long?
)

private suspend fun Input.copyTo(channel: ByteWriteChannel) {
    if (this is ByteReadPacket) {
        channel.writePacket(this)
        return
    }

    while (!this@copyTo.endOfInput) {
        channel.write { freeSpace, startOffset, endExclusive ->
            this@copyTo.readAvailable(freeSpace, startOffset, endExclusive - startOffset).toInt()
        }
    }
}
