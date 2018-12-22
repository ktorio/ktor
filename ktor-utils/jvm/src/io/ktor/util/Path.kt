package io.ktor.util

import java.io.*
import java.nio.file.*

/**
 * Finds an extension of the given Path
 *
 * Extension is a substring of a [Path.fileName] after last dot
 */
val Path.extension: String get() = fileName.toString().substringAfterLast(".")

/**
 * Append a [relativePath] safely that means that adding any extra `..` path elements will not let
 * access anything out of the reference directory (unless you have symbolic or hard links or multiple mount points)
 */
@KtorExperimentalAPI
fun File.combineSafe(relativePath: String): File = combineSafe(Paths.get(relativePath))

/**
 * Append a [relativePath] safely that means that adding any extra `..` path elements will not let
 * access anything out of the reference directory (unless you have symbolic or hard links or multiple mount points)
 */
@KtorExperimentalAPI
fun File.combineSafe(relativePath: Path): File {
    val normalized = relativePath.normalizeAndRelativize()
    if (normalized.startsWith("..")) {
        throw InvalidPathException(relativePath.toString(), "Bad relative path $relativePath")
    }
    check(!normalized.isAbsolute) { "Bad relative path $relativePath" }

    return File(this, normalized.toString())
}

/**
 * Append a [relativePath] safely that means that adding any extra `..` path elements will not let
 * access anything out of the reference directory (unless you have symbolic or hard links or multiple mount points)
 */
@KtorExperimentalAPI
fun Path.combineSafe(relativePath: Path): File {
    val normalized = relativePath.normalizeAndRelativize()
    if (normalized.startsWith("..")) {
        throw InvalidPathException(relativePath.toString(), "Bad relative path $relativePath")
    }
    check(!normalized.isAbsolute) { "Bad relative path $relativePath"}

    return resolve(normalized).toFile()
}

/**
 * Remove all redundant `.` and `..` path elements. Leading `..` are also considered redundant.
 */
@KtorExperimentalAPI
fun Path.normalizeAndRelativize(): Path =
    root?.relativize(this)?.normalize()?.dropLeadingTopDirs() ?: normalize().dropLeadingTopDirs()

private fun Path.dropLeadingTopDirs(): Path {
    val startIndex = indexOfFirst { it.toString() != ".." }
    if (startIndex == 0) return this
    return subpath(startIndex, nameCount)
}
