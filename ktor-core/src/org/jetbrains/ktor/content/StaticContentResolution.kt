package org.jetbrains.ktor.content

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.request.*
import org.jetbrains.ktor.util.*
import java.io.*
import java.net.*
import java.nio.file.*

/**
 * @param contextPath is a path prefix to be cut from the request path
 * @param baseDir is a base directory the path to be appended to
 * @param mimeResolve is a function that resolves content type by file extension, optional
 *
 * @return [LocalFileContent] or `null` if the file is out of the [baseDir] or doesn't exist
 */
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

/**
 * @param path is a relative path to the resource
 * @param basePackage is a base package the path to be appended to
 * @param mimeResolve is a function that resolves content type by file extension, optional
 *
 * @return [LocalFileContent] or [ResourceFileContent] or `null`
 */
fun ApplicationCall.resolveClasspathWithPath(basePackage: String, path: String, mimeResolve: (String) -> ContentType = { defaultContentType(it) }): Resource? {
    val packagePath = basePackage.replace('.', '/').appendIfNotEndsWith("/") + path.removePrefix("/")
    val normalizedPath = Paths.get(packagePath).normalizeAndRelativize()
    val normalizedResource = normalizedPath.toString().replace(File.separatorChar, '/')

    // note: we don't need to check for .. in the normalizedPath because all .. get replaced with //

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

/**
 * @param contextPath is a path prefix to be cut from the request path
 * @param basePackage is a base package the path to be appended to
 * @param mimeResolve is a function that resolves content type by file extension, optional
 *
 * @return [LocalFileContent] or [ResourceFileContent] or `null`
 */
fun ApplicationCall.resolveClasspathResource(contextPath: String, basePackage: String, mimeResolve: (String) -> ContentType = { defaultContentType(it) }): Resource? {
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
