package org.jetbrains.ktor.content

import org.jetbrains.ktor.cio.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.util.*
import java.io.*
import java.nio.file.*
import java.time.*

class LocalFileContent(val file: File,
                       override val contentType: ContentType = ContentType.defaultForFile(file),
                       override val expires: LocalDateTime? = null,
                       override val cacheControl: CacheControl? = null) : FinalContent.ReadChannelContent(), Resource {


    override val contentLength: Long
        get() = file.length()

    override val versions: List<Version>
        get() = listOf(LastModifiedVersion(Files.getLastModifiedTime(file.toPath())))

    override val headers by lazy { super<Resource>.headers }

    // TODO: consider using WriteChannelContent to avoid piping
    // Or even make it dual-content so host implementation can choose
    override fun readFrom() = file.readChannel()
}

fun LocalFileContent(baseDir: File,
                     relativePath: String,
                     contentType: ContentType = ContentType.defaultForFilePath(relativePath),
                     expires: LocalDateTime? = null,
                     cacheControl: CacheControl? = null) = LocalFileContent(baseDir.combineSafe(relativePath), contentType, expires, cacheControl)

fun LocalFileContent(baseDir: Path,
                     relativePath: Path,
                     contentType: ContentType = ContentType.defaultForFile(relativePath),
                     expires: LocalDateTime? = null,
                     cacheControl: CacheControl? = null) = LocalFileContent(baseDir.combineSafe(relativePath), contentType, expires, cacheControl)
