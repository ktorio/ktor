package org.jetbrains.ktor.content

import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.nio.*
import org.jetbrains.ktor.util.*
import java.io.*
import java.nio.file.*

class LocalFileContent(val file: File, override val contentType: ContentType = defaultContentType(file.extension)) : FinalContent.ChannelContent(), Resource {

    constructor(baseDir: File, relativePath: String, contentType: ContentType = defaultContentType(relativePath.extension())) : this(baseDir.safeAppend(Paths.get(relativePath)), contentType)
    constructor(baseDir: File, vararg relativePath: String, contentType: ContentType = defaultContentType(relativePath.last().extension())) : this(baseDir.safeAppend(Paths.get("", *relativePath)), contentType)
    constructor(baseDir: Path, relativePath: Path, contentType: ContentType = defaultContentType(relativePath.fileName.extension())) : this(baseDir.safeAppend(relativePath).toFile(), contentType)

    override val attributes = Attributes()

    override val contentLength: Long
        get() = file.length()

    override val versions: List<Version>
        get() = listOf(LastModifiedVersion(Files.getLastModifiedTime(file.toPath())))

    override val headers by lazy { super.headers }

    override fun channel() = file.asyncReadOnlyFileChannel()

    override val expires = null
    override val cacheControl = null
}

