package org.jetbrains.ktor.content

import org.jetbrains.ktor.cio.*
import org.jetbrains.ktor.http.*
import java.io.*
import java.nio.file.*
import java.util.jar.*

class ResourceFileContent(val jarFile: File,
                          val resourcePath: String,
                          val classLoader: ClassLoader,
                          override val contentType: ContentType) : Resource, FinalContent.ReadChannelContent() {

    private val normalized = Paths.get(resourcePath).normalize().toString().replace(File.separatorChar, '/')

    constructor(zipFilePath: Path, resourcePath: String, classLoader: ClassLoader, contentType: ContentType)
            : this(zipFilePath.toFile(), resourcePath, classLoader, contentType)

    init {
        require(!normalized.startsWith("..")) { "Bad resource relative path $resourcePath" }
    }

    override val versions: List<Version>
        get() = listOf(LastModifiedVersion(Files.getLastModifiedTime(jarFile.toPath())))

    override val contentLength: Long?
        get() = JarFile(jarFile).use { it.getJarEntry(resourcePath)?.size }


    override val headers by lazy { super<Resource>.headers }

    override fun readFrom() = classLoader.getResourceAsStream(normalized)?.toReadChannel() ?: throw IOException("Resource $normalized not found")

    override val expires = null
    override val cacheControl = null
}
