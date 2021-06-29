/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http.content

import io.ktor.http.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import java.io.*
import java.nio.file.*
import java.util.jar.*

/**
 * Represents an [OutgoingContent] for a resource inside a Jar file
 *
 * @param jarFile is an instance of [File] representing a Jar
 * @param resourcePath is an instance of a resource inside a Jar file
 */
public class JarFileContent(
    public val jarFile: File,
    public val resourcePath: String,
    override val contentType: ContentType
) : OutgoingContent.ReadChannelContent() {

    private val normalized = Paths.get(resourcePath).normalize().toString().replace(File.separatorChar, '/')
    private val jarEntry by lazy(LazyThreadSafetyMode.NONE) { jar.getJarEntry(resourcePath) }
    private val jar by lazy(LazyThreadSafetyMode.NONE) { JarFile(jarFile) }

    public val isFile: Boolean by lazy(LazyThreadSafetyMode.NONE) { !jarEntry.isDirectory }

    public constructor(zipFilePath: Path, resourcePath: String, contentType: ContentType) : this(
        zipFilePath.toFile(),
        resourcePath,
        contentType
    )

    init {
        require(!normalized.startsWith("..")) { "Bad resource relative path $resourcePath" }
        versions += LastModifiedVersion(jarEntry.lastModifiedTime)
    }

    override val contentLength: Long? get() = jarEntry?.size

    override fun readFrom(): ByteReadChannel = jar.getInputStream(jarEntry)?.toByteReadChannel(pool = KtorDefaultPool)
        ?: throw IOException("Resource $normalized not found")
}
