package org.jetbrains.ktor.content

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.nio.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.util.*
import org.jetbrains.ktor.util.Attributes
import java.io.*
import java.net.*
import java.nio.file.*
import java.util.jar.*

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

class URIFileContent(val uri: URI, override val contentType: ContentType = defaultContentType(uri.path.extension())): FinalContent.StreamContentProvider(), Resource {
    constructor(url: URL, contentType: ContentType = defaultContentType(url.path.extension())) : this(url.toURI(), contentType)

    override val headers: ValuesMap
        get() = super.headers

    override fun stream() = uri.toURL().openStream()

    override val versions: List<Version>
        get() = emptyList()

    override val expires = null
    override val cacheControl = null
    override val attributes = Attributes()
    override val contentLength = null
}

fun Route.serveClasspathResources(basePackage: String = "") {
    route("{path...}") {
        handle {
            call.resolveClasspathWithPath(basePackage, call.parameters.getAll("path")!!.joinToString(File.separator))?.let {
                call.respond(it)
            }
        }
    }
}

fun Route.serveFileSystem(baseDir: Path) = serveFileSystem(baseDir.toFile())

fun Route.serveFileSystem(baseDir: File) {
    route("{path...}") {
        handle {
            val message = LocalFileContent(baseDir, call.parameters.getAll("path")!!.joinToString(File.separator))
            if (!message.file.isFile) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                call.respond(message)
            }
        }
    }
}

internal fun defaultContentType(extension: String) = ContentTypeByExtension.lookupByExtension(extension).firstOrNull() ?: ContentType.Application.OctetStream
private fun String.extension() = split("/\\").last().substringAfter(".")
private fun Path.extension() = fileName.toString().substringAfter(".")

private fun File.safeAppend(relativePath: Path): File {
    val normalized = relativePath.normalizeAndRelativize()
    if (normalized.startsWith("..")) {
        throw IllegalArgumentException("Bad relative path $relativePath")
    }

    return File(this, normalized.toString())
}

private fun Path.safeAppend(relativePath: Path): Path {
    val normalized = relativePath.normalizeAndRelativize()
    if (normalized.startsWith("..")) {
        throw IllegalArgumentException("Bad relative path $relativePath")
    }

    return resolve(normalized)
}

internal fun Path.normalizeAndRelativize() = root?.relativize(this)?.normalize() ?: normalize()