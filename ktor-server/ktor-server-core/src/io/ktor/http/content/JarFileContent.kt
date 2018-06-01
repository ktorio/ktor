package io.ktor.http.content

import io.ktor.cio.*
import io.ktor.http.*
import java.io.*
import java.nio.file.*
import java.util.jar.*

/**
 * Represents an [OutgoingContent] for a resource inside a Jar file
 *
 * @param jarFile is an instance of [File] representing a Jar
 * @param resourcePath is an instance of a resource inside a Jar file
 */
class JarFileContent(
        val jarFile: File,
        val resourcePath: String,
        override val contentType: ContentType
) : OutgoingContent.ReadChannelContent() {

    private val normalized = Paths.get(resourcePath).normalize().toString().replace(File.separatorChar, '/')
    private val jarEntry by lazy(LazyThreadSafetyMode.NONE) { jar.getJarEntry(resourcePath) }
    private val jar by lazy(LazyThreadSafetyMode.NONE) { JarFile(jarFile) }

    constructor(zipFilePath: Path, resourcePath: String, contentType: ContentType)
            : this(zipFilePath.toFile(), resourcePath, contentType)

    init {
        require(!normalized.startsWith("..")) { "Bad resource relative path $resourcePath" }
        versions += LastModifiedVersion(jarEntry.lastModifiedTime)
    }

    override val contentLength: Long? get() = jarEntry?.size

    override fun readFrom() = jar.getInputStream(jarEntry)?.toByteReadChannel()
            ?: throw IOException("Resource $normalized not found")
}
