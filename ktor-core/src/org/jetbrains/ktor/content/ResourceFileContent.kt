package org.jetbrains.ktor.content

import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.util.*
import org.jetbrains.ktor.util.Attributes
import java.io.*
import java.nio.file.*
import java.util.jar.*

class ResourceFileContent(val zipFile: File, val resourcePath: String, val classLoader: ClassLoader, override val contentType: ContentType = defaultContentType(resourcePath.extension())) : Resource, FinalContent.StreamContentProvider() {
    private val normalized = Paths.get(resourcePath).normalize().toString().replace(File.separatorChar, '/')

    constructor(zipFilePath: Path, resourcePath: String, classLoader: ClassLoader, contentType: ContentType = defaultContentType(resourcePath.extension())) : this(zipFilePath.toFile(), resourcePath, classLoader, contentType)

    init {
        require(!normalized.startsWith("..")) { "Bad resource relative path $resourcePath" }
    }

    override val attributes = Attributes()

    override val versions: List<Version>
        get() =  listOf(LastModifiedVersion(Files.getLastModifiedTime(zipFile.toPath())))

    override val contentLength: Long?
        get() = JarFile(zipFile).use { it.getJarEntry(resourcePath)?.size }


    override val headers: ValuesMap
        get() = super.headers

    override fun stream() = classLoader.getResourceAsStream(normalized) ?: throw IOException("Resource $normalized not found")

    override val expires = null
    override val cacheControl = null
}
