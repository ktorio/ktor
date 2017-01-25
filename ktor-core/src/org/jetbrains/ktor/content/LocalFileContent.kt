package org.jetbrains.ktor.content

import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.cio.*
import org.jetbrains.ktor.util.*
import java.io.*
import java.nio.file.*
import java.time.*

class LocalFileContent(val file: File,
                       override val contentType: ContentType = defaultContentType(file.extension),
                       override val expires: LocalDateTime? = null,
                       override val cacheControl: CacheControl? = null) : FinalContent.ChannelContent(), Resource {

    constructor(baseDir: File,
                relativePath: String,
                contentType: ContentType = defaultContentType(relativePath.extension()),
                expires: LocalDateTime? = null,
                cacheControl: CacheControl? = null) : this(baseDir.safeAppend(Paths.get(relativePath)), contentType, expires, cacheControl)

    constructor(baseDir: File,
                vararg relativePath: String,
                contentType: ContentType = defaultContentType(relativePath.last().extension()),
                expires: LocalDateTime? = null,
                cacheControl: CacheControl? = null) : this(baseDir.safeAppend(Paths.get("", *relativePath)), contentType, expires, cacheControl)

    constructor(baseDir: Path,
                relativePath: Path,
                contentType: ContentType = defaultContentType(relativePath.fileName.extension()),
                expires: LocalDateTime? = null,
                cacheControl: CacheControl? = null) : this(baseDir.safeAppend(relativePath).toFile(), contentType, expires, cacheControl)

    override val contentLength: Long
        get() = file.length()

    override val versions: List<Version>
        get() = listOf(LastModifiedVersion(Files.getLastModifiedTime(file.toPath())))

    override val headers by lazy { super.headers }

    override fun channel() = file.asyncReadOnlyFileChannel()
}

