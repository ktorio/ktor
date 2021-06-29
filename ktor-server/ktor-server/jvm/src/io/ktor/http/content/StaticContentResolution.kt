/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http.content

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.util.*
import java.io.*
import java.net.*

/**
 * @param path is a relative path to the resource
 * @param resourcePackage is a base package the path to be appended to
 * @param mimeResolve is a function that resolves content type by file extension, optional
 *
 * @return [LocalFileContent] or [JarFileContent] or `null`
 */
public fun ApplicationCall.resolveResource(
    path: String,
    resourcePackage: String? = null,
    classLoader: ClassLoader = application.environment.classLoader,
    mimeResolve: (String) -> ContentType = { ContentType.defaultForFileExtension(it) }
): OutgoingContent? {
    if (path.endsWith("/") || path.endsWith("\\")) {
        return null
    }

    val normalizedPath = (
        resourcePackage.orEmpty().split('.', '/', '\\') +
            path.split('/', '\\')
        ).normalizePathComponents().joinToString("/")

    // note: we don't need to check for .. in the normalizedPath because all .. get replaced with //

    for (url in classLoader.getResources(normalizedPath).asSequence()) {
        resourceClasspathResource(url, normalizedPath, mimeResolve)?.let { content ->
            return content
        }
    }

    return null
}

/**
 * Attempt to find a local file or a file inside of zip. This is not required but very good to have
 * to improve performance and unnecessary [java.io.InputStream] creation.
 */
@InternalAPI
public fun resourceClasspathResource(url: URL, path: String, mimeResolve: (String) -> ContentType): OutgoingContent? {
    return when (url.protocol) {
        "file" -> {
            val file = File(url.path.decodeURLPart())
            if (file.isFile) LocalFileContent(file, mimeResolve(file.extension)) else null
        }
        "jar" -> {
            if (path.endsWith("/")) {
                null
            } else {
                val zipFile = findContainingJarFile(url.toString())
                val content = JarFileContent(zipFile, path, mimeResolve(url.path.extension()))
                if (content.isFile) content else null
            }
        }
        "jrt" -> {
            URIFileContent(url, mimeResolve(url.path.extension()))
        }
        else -> null
    }
}

internal fun findContainingJarFile(url: String): File {
    if (url.startsWith("jar:file:")) {
        val jarPathSeparator = url.indexOf("!", startIndex = 9)
        require(jarPathSeparator != -1) { "Jar path requires !/ separator but it is: $url" }

        return File(url.substring(9, jarPathSeparator).decodeURLPart())
    }

    throw IllegalArgumentException("Only local jars are supported (jar:file:)")
}

private fun String.extension(): String {
    val indexOfName = lastIndexOf('/').takeIf { it != -1 } ?: lastIndexOf('\\').takeIf { it != -1 } ?: 0
    val indexOfDot = indexOf('.', indexOfName)
    return if (indexOfDot >= 0) substring(indexOfDot) else ""
}
