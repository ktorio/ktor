/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.http.content

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.util.*
import io.ktor.utils.io.*
import java.io.File
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.http.content.resolveResource)
 *
 * @param path is a relative path to the resource
 * @param resourcePackage is a base package the path to be appended to
 * @param mimeResolve is a function that resolves content type by file extension, optional
 *
 * @return [LocalFileContent] or [JarFileContent] or `null`
 */
@OptIn(InternalAPI::class)
public fun ApplicationCall.resolveResource(
    path: String,
    resourcePackage: String? = null,
    classLoader: ClassLoader = application.environment.classLoader,
    mimeResolve: (String) -> ContentType = { ContentType.defaultForFileExtension(it) }
): OutgoingContent.ReadChannelContent? {
    if (path.endsWith("/") || path.endsWith("\\")) {
        return null
    }

    val normalizedPath = normalisedPath(resourcePackage, path)

    for (url in classLoader.getResources(normalizedPath).asSequence()) {
        resourceClasspathResource(url, normalizedPath) { mimeResolve(it.path.extension()) }?.let { content ->
            return content
        }
    }

    return null
}

private val resourceCache by lazy { ConcurrentHashMap<ClassLoader, ConcurrentHashMap<String, URL>>() }

/**
 * Two-level cache: resourcePackage -> (path -> normalizedPath)
 * Using nested maps avoids string concatenation for cache key creation on every lookup.
 */
private val normalizedPathCache by lazy { ConcurrentHashMap<String, ConcurrentHashMap<String, String>>() }

@OptIn(InternalAPI::class)
internal fun Application.resolveResource(
    path: String,
    resourcePackage: String? = null,
    classLoader: ClassLoader = environment.classLoader,
    mimeResolve: (URL) -> ContentType
): Pair<URL, OutgoingContent.ReadChannelContent>? {
    if (path.endsWith("/") || path.endsWith("\\")) {
        return null
    }

    val normalizedPath = normalisedPath(resourcePackage, path)
    val classLoaderCache = resourceCache.getOrPut(classLoader) { ConcurrentHashMap() }
    val resolveContent: (URL) -> Pair<URL, OutgoingContent.ReadChannelContent>? = { url ->
        resourceClasspathResource(url, normalizedPath, mimeResolve)?.let { url to it }
    }
    return classLoaderCache[normalizedPath]?.let(resolveContent)
        ?: classLoader.getResources(normalizedPath).asSequence()
            .firstNotNullOfOrNull(resolveContent)?.also { (url) ->
                classLoaderCache[normalizedPath] = url
            }
}

/**
 * Attempt to find a local file or a file inside of zip. This is not required but very good to have
 * to improve performance and unnecessary [java.io.InputStream] creation.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.http.content.resourceClasspathResource)
 */
@InternalAPI
public fun resourceClasspathResource(
    url: URL,
    path: String,
    mimeResolve: (URL) -> ContentType
): OutgoingContent.ReadChannelContent? {
    return when (url.protocol) {
        "file" -> {
            val file = File(url.path.decodeURLPart())
            if (file.isFile) LocalFileContent(file, mimeResolve(url)) else null
        }

        "jar" -> {
            if (path.endsWith("/")) {
                null
            } else {
                val zipFile = findContainingJarFile(url.toString())
                if (zipFile == null) {
                    return URIFileContent(url, mimeResolve(url))
                }
                JarFileContent(zipFile, path, mimeResolve(url)).takeIf { it.isFile }
            }
        }

        "jrt", "resource" -> {
            URIFileContent(url, mimeResolve(url))
        }

        else -> null
    }
}

/**
 * Returns the local jar file containing the resource pointed to by the given URL,
 * or `null` if URL is not a standard JAR URL.
 *
 * The syntax of a JAR URL is: `jar:<url>!/{entry}`
 * Example: `jar:file:/path/to/app.jar!/entry/inside/jar`
 *
 * Examples:
 * - `jar:file:/dist/app.jar!/static/index.html`              → returns `/dist/app.jar`
 * - `jar:file:/dist/app.jar!`                                → returns `/dist/app.jar`
 * - `jar:file:/outer.jar!/lib/dep.jar!/x.css`                → throws IllegalArgumentException (nested)
 * - `jar:nested:/path/to/app.jar/!BOOT-INF/classes/!/static` → returns `null` (non-local container)
 */
internal fun findContainingJarFile(url: String): File? {
    if (!url.startsWith(JAR_PREFIX)) return null

    val jarPathSeparator = url.indexOf("!", startIndex = JAR_PREFIX.length)
    if (jarPathSeparator == -1) {
        return null
    }
    val nextJarSeparator = url.indexOf("!", startIndex = jarPathSeparator + 1)
    if (nextJarSeparator != -1) {
        // KTOR-8883 Support nested jars in static resources
        throw IllegalArgumentException("Only local jars are supported (jar:file:)")
    }

    return File(url.substring(JAR_PREFIX.length, jarPathSeparator).decodeURLPart())
}

internal fun String.extension(): String {
    val indexOfName = lastIndexOf('/').takeIf { it != -1 } ?: lastIndexOf('\\').takeIf { it != -1 } ?: 0
    val indexOfDot = indexOf('.', indexOfName)
    return if (indexOfDot >= 0) substring(indexOfDot) else ""
}

private fun normalisedPath(resourcePackage: String?, path: String): String {
    // Check for path traversal before caching
    if (containsPathTraversal(path)) {
        throw BadRequestException("Relative path should not contain path traversing characters: $path")
    }

    // Use two-level cache to avoid string concatenation for cache key on every lookup
    val packageKey = resourcePackage ?: ""
    return normalizedPathCache
        .getOrPut(packageKey) { ConcurrentHashMap() }
        .getOrPut(path) { computeNormalizedPath(resourcePackage, path) }
}

private fun containsPathTraversal(path: String): Boolean {
    var i = 0
    val len = path.length
    while (i < len) {
        // Find start of component (skip separators)
        while (i < len && (path[i] == '/' || path[i] == '\\')) i++
        if (i >= len) break

        // Check for ".." component
        if (i + 1 < len && path[i] == '.' && path[i + 1] == '.') {
            val afterDots = i + 2
            if (afterDots >= len || path[afterDots] == '/' || path[afterDots] == '\\') {
                return true
            }
        }

        // Skip to next separator
        while (i < len && path[i] != '/' && path[i] != '\\') i++
    }
    return false
}

private fun computeNormalizedPath(resourcePackage: String?, path: String): String {
    val components = ArrayList<String>(16)

    // Parse resource package components (split by '.', '/', '\\')
    if (!resourcePackage.isNullOrEmpty()) {
        parseComponents(resourcePackage, components, splitOnDot = true)
    }

    // Parse path components (split by '/', '\\')
    parseComponents(path, components, splitOnDot = false)

    // Normalize and join using index-based iteration to avoid iterator allocation
    return components.normalizePathComponents().joinByIndexToString("/")
}

/**
 * Joins list elements using index-based access instead of iterator to avoid ArrayList$Itr allocation.
 */
private fun List<String>.joinByIndexToString(separator: String): String {
    val size = size
    if (size == 0) return ""
    if (size == 1) return get(0)

    val sb = StringBuilder()
    sb.append(get(0))
    for (i in 1 until size) {
        sb.append(separator)
        sb.append(get(i))
    }
    return sb.toString()
}

private fun parseComponents(input: String, output: MutableList<String>, splitOnDot: Boolean) {
    var start = 0
    val len = input.length

    for (i in 0..len) {
        val isEnd = i == len
        val isSeparator = !isEnd && (input[i] == '/' || input[i] == '\\' || (splitOnDot && input[i] == '.'))

        if (isEnd || isSeparator) {
            if (i > start) {
                output.add(input.substring(start, i))
            }
            start = i + 1
        }
    }
}

private const val JAR_PREFIX = "jar:file:"
