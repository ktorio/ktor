/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http.content

import io.ktor.http.*
import io.ktor.util.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import java.io.*
import java.nio.file.*

/**
 * OutgoingContent representing a local [file] with a specified [contentType], [expires] date and [caching]
 *
 * @param file specifies the File to be served to a client
 */
public class LocalFileContent(
    public val file: File,
    override val contentType: ContentType = ContentType.defaultForFile(file)
) : OutgoingContent.ReadChannelContent() {

    override val contentLength: Long get() = file.length()

    init {
        versions += LastModifiedVersion(Files.getLastModifiedTime(file.toPath()))
    }

    // TODO: consider using WriteChannelContent to avoid piping
    // Or even make it dual-content so engine implementation can choose
    override fun readFrom(): ByteReadChannel = file.readChannel()

    override fun readFrom(range: LongRange): ByteReadChannel = file.readChannel(range.start, range.endInclusive)
}

/**
 * Creates an instance of [LocalFileContent] for a file designated by [relativePath] in a [baseDir]
 */
public fun LocalFileContent(
    baseDir: File,
    relativePath: String,
    contentType: ContentType = ContentType.defaultForFilePath(relativePath)
): LocalFileContent =
    LocalFileContent(baseDir.combineSafe(relativePath), contentType)

/**
 * Creates an instance of [LocalFileContent] for a file designated by [relativePath] in a [baseDir]
 */
public fun LocalFileContent(
    baseDir: Path,
    relativePath: Path,
    contentType: ContentType = ContentType.defaultForFile(relativePath)
): LocalFileContent =
    LocalFileContent(baseDir.combineSafe(relativePath), contentType)
