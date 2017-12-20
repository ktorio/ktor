package io.ktor.content

import io.ktor.cio.*
import io.ktor.http.*
import io.ktor.util.*
import java.io.*
import java.nio.file.*
import java.util.jar.*

class JarFileContent(val jarFile: File,
                     val resourcePath: String,
                     val classLoader: ClassLoader,
                     override val contentType: ContentType) : OutgoingContent.ReadChannelContent(), VersionedContent {

    private val normalized = Paths.get(resourcePath).normalize().toString().replace(File.separatorChar, '/')
    private val jarEntry by lazy(LazyThreadSafetyMode.NONE) {
        JarFile(jarFile).getJarEntry(resourcePath)
    }

    constructor(zipFilePath: Path, resourcePath: String, classLoader: ClassLoader, contentType: ContentType)
            : this(zipFilePath.toFile(), resourcePath, classLoader, contentType)

    init {
        require(!normalized.startsWith("..")) { "Bad resource relative path $resourcePath" }
    }

    override val versions: List<Version>
        get() = listOf(LastModifiedVersion(jarEntry.lastModifiedTime))

    override val contentLength: Long? get() = jarEntry?.size

    override val headers by lazy(LazyThreadSafetyMode.NONE) {
        ValuesMap.build(true) {
            contentType(contentType)
            contentLength?.let { contentLength(it) }
        }
    }

    override fun readFrom() = classLoader.getResourceAsStream(normalized)?.toByteReadChannel() ?: throw IOException("Resource $normalized not found")
}
