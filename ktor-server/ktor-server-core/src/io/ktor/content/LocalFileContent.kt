package io.ktor.content

import io.ktor.cio.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.coroutines.experimental.io.*
import java.io.*
import java.nio.file.*
import java.time.*

/**
 * OutgoingContent representing a local [file] with a specified [contentType], [expires] date and [cacheControl]
 *
 * @param file specifies the File to be served to a client
 */
class LocalFileContent(val file: File,
                       override val contentType: ContentType = ContentType.defaultForFile(file),
                       override val expires: LocalDateTime? = null,
                       override val cacheControl: CacheControl? = null) : OutgoingContent.ReadChannelContent(), Resource {


    override val contentLength: Long
        get() = file.length()

    override val versions: List<Version>
        get() = listOf(LastModifiedVersion(Files.getLastModifiedTime(file.toPath())))

    override val headers by lazy(LazyThreadSafetyMode.NONE) { super<Resource>.headers }

    // TODO: consider using WriteChannelContent to avoid piping
    // Or even make it dual-content so engine implementation can choose
    override fun readFrom(): ByteReadChannel = file.readChannel()

    override fun readFrom(range: LongRange): ByteReadChannel = file.readChannel(range.start, range.endInclusive)
}

/**
 * Creates an instance of [LocalFileContent] for a file designated by [relativePath] in a [baseDir]
 */
fun LocalFileContent(baseDir: File,
                     relativePath: String,
                     contentType: ContentType = ContentType.defaultForFilePath(relativePath),
                     expires: LocalDateTime? = null,
                     cacheControl: CacheControl? = null) = LocalFileContent(baseDir.combineSafe(relativePath), contentType, expires, cacheControl)

/**
 * Creates an instance of [LocalFileContent] for a file designated by [relativePath] in a [baseDir]
 */
fun LocalFileContent(baseDir: Path,
                     relativePath: Path,
                     contentType: ContentType = ContentType.defaultForFile(relativePath),
                     expires: LocalDateTime? = null,
                     cacheControl: CacheControl? = null) = LocalFileContent(baseDir.combineSafe(relativePath), contentType, expires, cacheControl)
