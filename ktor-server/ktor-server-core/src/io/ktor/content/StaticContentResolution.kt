package io.ktor.content

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.util.*
import java.io.*
import java.nio.file.*

/**
 * @param path is a relative path to the resource
 * @param resourcePackage is a base package the path to be appended to
 * @param mimeResolve is a function that resolves content type by file extension, optional
 *
 * @return [LocalFileContent] or [JarFileContent] or `null`
 */
fun ApplicationCall.resolveResource(path: String,
                                    resourcePackage: String? = null,
                                    classLoader: ClassLoader = application.environment.classLoader,
                                    mimeResolve: (String) -> ContentType = { ContentType.defaultForFileExtension(it) }): OutgoingContent? {
    val packagePath = (resourcePackage?.replace('.', '/') ?: "").appendPathPart(path)
    val normalizedPath = Paths.get(packagePath).normalizeAndRelativize()
    val normalizedResource = normalizedPath.toString().replace(File.separatorChar, '/')

    // note: we don't need to check for .. in the normalizedPath because all .. get replaced with //

    for (url in classLoader.getResources(normalizedResource).asSequence()) {
        if (url.protocol == "file") {
            val file = File(decodeURLPart(url.path))
            return if (file.exists()) LocalFileContent(file, mimeResolve(file.extension)) else null
        } else if (url.protocol == "jar") {
            val zipFile = findContainingJarFile(url.toString())
            return JarFileContent(zipFile, normalizedResource, mimeResolve(url.path.extension()))
        }
    }

    return null
}

internal fun findContainingJarFile(url: String): File {
    if (url.startsWith("jar:file:")) {
        val jarPathSeparator = url.indexOf("!", startIndex = 9)
        require(jarPathSeparator != -1) { "Jar path requires !/ separator but it is: $url" }

        return File(decodeURLPart(url.substring(9, jarPathSeparator)))
    }

    throw IllegalArgumentException("Only local jars are supported (jar:file:)")
}

private fun String.extension(): String {
    val indexOfName = lastIndexOf('/').takeIf { it != 1 } ?: lastIndexOf('\\').takeIf { it != 1 } ?: 0
    val indexOfDot = indexOf('.', indexOfName)
    return substring(indexOfDot)
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