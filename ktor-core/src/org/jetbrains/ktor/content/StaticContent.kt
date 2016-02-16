package org.jetbrains.ktor.content

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.routing.*
import java.io.*
import java.net.*
import java.nio.file.*

class LocalFileContent(val file: File, override val contentType: ContentType = defaultContentType(file.extension)) : HasContentType, HasContentLength, StreamContentProvider, HasLastModified {

    constructor(baseDir: File, relativePath: String, contentType: ContentType = defaultContentType(relativePath.extension())) : this(baseDir.safeAppend(Paths.get(relativePath)), contentType)
    constructor(baseDir: File, vararg relativePath: String, contentType: ContentType = defaultContentType(relativePath.last().extension())) : this(baseDir.safeAppend(Paths.get("", *relativePath)), contentType)
    constructor(baseDir: Path, relativePath: Path, contentType: ContentType = defaultContentType(relativePath.fileName.extension())) : this(baseDir.safeAppend(relativePath).toFile(), contentType)

    override val contentLength: Long
        get() = file.length()

    override val lastModified: Long
        get() = file.lastModified()

    override fun stream() = file.inputStream()
}

class ResourceFileContent(val zipFile: File, val resourcePath: String, val classLoader: ClassLoader, override val contentType: ContentType = defaultContentType(resourcePath.extension())) : HasContentType, StreamContentProvider, HasLastModified {
    private val normalized = Paths.get(resourcePath).normalize().toString().replace(File.separatorChar, '/')

    constructor(zipFilePath: Path, resourcePath: String, classLoader: ClassLoader, contentType: ContentType = defaultContentType(resourcePath.extension())) : this(zipFilePath.toFile(), resourcePath, classLoader, contentType)

    init {
        require(!normalized.startsWith("..")) { "Bad resource relative path $resourcePath" }
    }

    override val lastModified: Long
        get() = zipFile.lastModified()

    override fun stream() = classLoader.getResourceAsStream(normalized) ?: throw IOException("Resource $normalized not found")
}

class URIFileContent(val uri: URI, override val contentType: ContentType = defaultContentType(uri.path.extension())): HasContentType, StreamContentProvider {
    constructor(url: URL, contentType: ContentType = defaultContentType(url.path.extension())) : this(url.toURI(), contentType)

    override fun stream() = uri.toURL().openStream()
}

fun RoutingEntry.serveClasspathResources(basePackage: String = "") {
    route("{path...}") {
        handle {
            resolveClasspathWithPath(basePackage, parameters.getAll("path")!!.joinToString(File.separator))?.let {
                response.send(it)
            } ?: ApplicationCallResult.Unhandled
        }
    }
}

fun RoutingEntry.serveFileSystem(baseDir: Path) = serveFileSystem(baseDir.toFile())

fun RoutingEntry.serveFileSystem(baseDir: File) {
    route("{path...}") {
        handle {
            val message = LocalFileContent(baseDir, parameters.getAll("path")!!.joinToString(File.separator))
            if (!message.file.exists()) {
                response.sendError(HttpStatusCode.NotFound)
            } else {
                response.send(message)
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