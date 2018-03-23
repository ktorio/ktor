package io.ktor.content

import io.ktor.http.*
import kotlinx.coroutines.experimental.io.*
import kotlinx.coroutines.experimental.io.jvm.javaio.*
import kotlinx.io.pool.*
import kotlinx.io.streams.*
import java.io.*
import java.nio.charset.*

@Deprecated("Use receive<ByteReadChannel>(), receive<MultiPartData>() or receive<InputStream>() instead")
interface IncomingContent : HttpMessage {
    fun readChannel(): ByteReadChannel
    fun multiPartData(): MultiPartData
    fun inputStream(): InputStream = readChannel().toInputStream()
}

@Deprecated("Use receive<ByteReadChannel>(), receive<MultiPartData>() or receive<InputStream>() instead")
@Suppress("OverridingDeprecatedMember", "DEPRECATION", "UNUSED_PARAMETER")
suspend fun IncomingContent.readText(pool: ObjectPool<ByteBuffer>, charset: Charset? = null) = readText(charset ?: charset() ?: Charsets.ISO_8859_1)

@Deprecated("Use receive<ByteReadChannel>(), receive<MultiPartData>() or receive<InputStream>() instead")
@Suppress("OverridingDeprecatedMember", "DEPRECATION")
suspend fun IncomingContent.readText() = readText(charset() ?: Charsets.ISO_8859_1)

@Deprecated("Use receive<ByteReadChannel>(), receive<MultiPartData>() or receive<InputStream>() instead")
@Suppress("OverridingDeprecatedMember", "DEPRECATION")
suspend fun IncomingContent.readText(
        charset: Charset
): String {
    val channel = readChannel()
    if (channel.isClosedForRead) return ""

    val content = channel.readRemaining()

    return try {
        if (charset == Charsets.UTF_8) content.readText()
        else content.inputStream().reader(charset).readText()
    } finally {
        content.release()
    }
}
