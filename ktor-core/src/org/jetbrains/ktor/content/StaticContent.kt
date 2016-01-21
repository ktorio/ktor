package org.jetbrains.ktor.content

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.routing.*
import java.io.*
import java.net.*
import java.nio.file.*

fun RoutingEntry.serveStatic(resource: String = "") {
    route("{path...}") {
        handle {
            resolveClasspathWithPath(resource, parameters["path"]!!)?.let {
                response.send(it)
            } ?: ApplicationCallResult.Unhandled
        }
    }
}

fun ApplicationCall.resolveLocalFile(contextPath: String, baseDir: File, mimeResolve: (String) -> ContentType = { defaultContentType(it) }): LocalFileContent? {
    val path = request.path()
    if (!path.startsWith(contextPath)) {
        return null
    }

    val sub = path.removePrefix(contextPath)
    if (!sub.startsWith('/')) {
        return null
    }

    val relativePath = sub.removePrefix("/").replace("/", File.separator)
    val canonicalBase = baseDir.canonicalFile
    val destination = File(canonicalBase, relativePath).canonicalFile

    if (!destination.startsWith(canonicalBase)) {
        return null
    }

    if (!destination.exists()) {
        return null
    }

    return LocalFileContent(destination, mimeResolve(destination.extension))
}

private fun File.safeAppend(relativePath: Path): File {
    val normalized = relativePath.normalize()
    if (normalized.startsWith("..")) {
        throw IllegalArgumentException("Bad relative path $relativePath")
    }

    return File(this, normalized.toString())
}

private fun Path.safeAppend(relativePath: Path): Path {
    val normalized = relativePath.normalize()
    if (normalized.startsWith("..")) {
        throw IllegalArgumentException("Bad relative path $relativePath")
    }

    return resolve(relativePath)
}

fun ApplicationCall.resolveClasspathWithPath(basePackage: String, path: String, mimeResolve: (String) -> ContentType = { defaultContentType(it) }): StreamContentProvider? {
    val packagePath = basePackage.replace('.', '/').appendIfNotEndsWith("/") + path.removePrefix("/")
    val normalizedPath = Paths.get(packagePath).normalize()
    val normalizedResource = normalizedPath.toString().replace(File.separatorChar, '/')

    if (normalizedPath.startsWith("..")) {
        return null
    }

    for (uri in javaClass.classLoader.getResources(normalizedResource).asSequence().map { it.toURI() }) {
        if (uri.scheme == "file") {
            val file = File(uri.path)
            return if (file.exists()) LocalFileContent(file, mimeResolve(file.extension)) else null
        } else if (uri.scheme == "jar") {
            return ResourceFileContent(findContainingZipFile(uri), normalizedResource, javaClass.classLoader, mimeResolve(uri.schemeSpecificPart.extension()))
        }
    }

    return null
}

fun ApplicationCall.resolveClasspathResource(contextPath: String, basePackage: String, mimeResolve: (String) -> ContentType = { defaultContentType(it) }): StreamContentProvider? {
    val path = request.path()
    if (!path.startsWith(contextPath)) {
        return null
    }

    val sub = path.removePrefix(contextPath)
    if (!sub.startsWith('/')) {
        return null
    }

    return resolveClasspathWithPath(basePackage, sub, mimeResolve)
}

tailrec
internal fun findContainingZipFile(uri: URI): File {
    if (uri.scheme == "file") {
        return File(uri.path.substringBefore("!"))
    } else {
        return findContainingZipFile(URI(uri.rawSchemeSpecificPart))
    }
}

private fun String.appendIfNotEndsWith(suffix: String) = if (endsWith(suffix)) this else this + suffix

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

class ResourceFileContent(val zipFile: File, val path: String, val classLoader: ClassLoader, override val contentType: ContentType = defaultContentType(path.extension())) : HasContentType, StreamContentProvider, HasLastModified {
    private val normalized = Paths.get(path).normalize().toString().replace(File.separatorChar, '/')

    init {
        require(!normalized.startsWith("..")) { "Bad resource relative path $path" }
    }

    override val lastModified: Long
        get() = zipFile.lastModified()

    override fun stream() = classLoader.getResourceAsStream(normalized) ?: throw IOException("Resource $normalized not found")
}

class URIFileContent(val uri: URI, override val contentType: ContentType = defaultContentType(uri.path.extension())): HasContentType, StreamContentProvider {
    override fun stream() = uri.toURL().openStream()
}

private fun defaultContentType(extension: String) = ContentTypeByExtension.lookupByExtension(extension).firstOrNull() ?: ContentType.Application.OctetStream
private fun String.extension() = split("/\\").last().substringAfter(".")
private fun Path.extension() = fileName.toString().substringAfter(".")
