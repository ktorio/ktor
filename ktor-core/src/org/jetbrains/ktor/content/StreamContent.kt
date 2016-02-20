package org.jetbrains.ktor.content

import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.util.*
import java.io.*
import java.nio.channels.*
import java.time.*

interface StreamContent {
    fun stream(out : OutputStream): Unit
}

/**
 * Does almost the same as [StreamContent] except it is suitable for async streaming so this is why it is preferred.
 */
interface StreamContentProvider {
    fun stream(): InputStream
}

interface ChannelContentProvider {
    fun channel(): AsynchronousByteChannel
    val seekable: Boolean
}

interface Resource {
    val contentType: ContentType
    val versions: List<Version>
    val expires: LocalDateTime?
    val cacheControl: CacheControl?
    val attributes: Attributes
    val contentLength: Long?
}
