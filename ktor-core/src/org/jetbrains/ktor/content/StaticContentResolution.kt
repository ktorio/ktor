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

private val defaultClassLoader = ApplicationCall::class.java.classLoader

/**
 * @param path is a relative path to the resource
 * @param basePackage is a base package the path to be appended to
 * @param mimeResolve is a function that resolves content type by file extension, optional
 *
 * @return [LocalFileContent] or [ResourceFileContent] or `null`
 */
fun ApplicationCall.resolveClasspathWithPath(basePackage: String, path: String, mimeResolve: (String) -> ContentType = { defaultContentType(it) }): Resource? {
    return resolveClasspathWithPath(basePackage, path, defaultClassLoader, mimeResolve)
}

private fun resolveClasspathWithPath(basePackage: String, path: String, classLoader: ClassLoader = defaultClassLoader, mimeResolve: (String) -> ContentType = { defaultContentType(it) }): Resource? {
    val packagePath = basePackage.replace('.', '/').appendPathPart(path)
    val normalizedPath = Paths.get(packagePath).normalizeAndRelativize()
    val normalizedResource = normalizedPath.toString().replace(File.separatorChar, '/')

    // note: we don't need to check for .. in the normalizedPath because all .. get replaced with //

    for (url in classLoader.getResources(normalizedResource).asSequence()) {
        if (url.protocol == "file") {
            val file = File(decodeURL(url.path))
            return if (file.exists()) LocalFileContent(file, mimeResolve(file.extension)) else null
        } else if (url.protocol == "jar") {
            return ResourceFileContent(findContainingZipFile(url.toString()), normalizedResource, classLoader, mimeResolve(url.path.extension()))
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

internal fun findContainingZipFile(url: String): File {
    if (url.startsWith("jar:file:")) {
        val jarPathSeparator = url.indexOf("!", startIndex = 9)
        require(jarPathSeparator != -1) { "Jar path requires !/ separator but it is: $url" }

        return File(decodeURL(url.substring(9, jarPathSeparator)))
    }

    throw IllegalArgumentException("Only local jars are supported (jar:file:)")
}

private fun decodeURL(s: String): String {
    if (s.indexOfAny(charArrayOf('%', '+')) == -1) { // for local file paths it can run much faster if no urlencoded characters (very likely for server-side apps)
        return s
    } else return URLDecoder.decode(s, "UTF-8")
}

private fun String.appendPathPart(part: String): String {
    val count = (if (isNotEmpty() && this[length - 1] == '/') 1 else 0) +
            (if (part.isNotEmpty() && part[0] == '/') 1 else 0)

    return when (count) {
        2 -> this + part.removePrefix("/")
        1 -> this + part
        else -> StringBuilder(length + part.length + 1).apply { append(this@appendPathPart); append('/'); append(part) }.toString()
    }
}