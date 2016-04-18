package org.jetbrains.ktor.content

import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.nio.*
import org.jetbrains.ktor.util.*
import java.io.*
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
    fun channel(): AsyncReadChannel
}

interface Resource : HasVersions {
    val contentType: ContentType
    override val versions: List<Version>
    val expires: LocalDateTime?
    val cacheControl: CacheControl?
    val attributes: Attributes
    val contentLength: Long?
}
