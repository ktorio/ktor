@file:Suppress("DeprecatedCallableAddReplaceWith")

package io.ktor.content

import io.ktor.http.*
import kotlinx.coroutines.experimental.io.*
import kotlinx.coroutines.experimental.io.jvm.javaio.*
import kotlinx.io.pool.*
import java.io.*
import java.nio.charset.*

@Deprecated("Use receive<ByteReadChannel>(), receive<MultiPartData>() or receive<InputStream>() instead",
        level = DeprecationLevel.ERROR)
interface IncomingContent : HttpMessage {
    fun readChannel(): ByteReadChannel
    fun multiPartData(): MultiPartData
    fun inputStream(): InputStream = readChannel().toInputStream()
}

@Deprecated("Use receive<ByteReadChannel>(), receive<MultiPartData>() or receive<InputStream>() instead",
        level = DeprecationLevel.ERROR)
@Suppress("OverridingDeprecatedMember", "DEPRECATION_ERROR", "UNUSED_PARAMETER")
suspend fun IncomingContent.readText(pool: ObjectPool<ByteBuffer>, charset: Charset? = null): String =
        throw UnsupportedOperationException("IncomingContent is no longer supported")

@Deprecated("Use receive<ByteReadChannel>(), receive<MultiPartData>() or receive<InputStream>() instead",
        level = DeprecationLevel.ERROR)
@Suppress("OverridingDeprecatedMember", "DEPRECATION_ERROR")
suspend fun IncomingContent.readText(): String = throw UnsupportedOperationException("IncomingContent is no longer supported")

@Deprecated("Use receive<ByteReadChannel>(), receive<MultiPartData>() or receive<InputStream>() instead",
        level = DeprecationLevel.ERROR)
@Suppress("OverridingDeprecatedMember", "DEPRECATION_ERROR")
suspend fun IncomingContent.readText(
        charset: Charset
): String = throw UnsupportedOperationException("IncomingContent is no longer supported")
