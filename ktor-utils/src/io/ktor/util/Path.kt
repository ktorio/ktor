package io.ktor.util

import java.io.*
import java.nio.file.*

/**
 * Finds an extension of the given Path
 *
 * Extension is a substring of a [Path.fileName] after last dot
 */
val Path.extension get() = fileName.toString().substringAfterLast(".")

fun File.combineSafe(relativePath: String): File = combineSafe(Paths.get(relativePath))

fun File.combineSafe(relativePath: Path): File {
    val normalized = relativePath.normalizeAndRelativize()
    if (normalized.startsWith("..")) {
        throw InvalidPathException(relativePath.toString(), "Bad relative path $relativePath")
    }
    check(!normalized.isAbsolute) { "Bad relative path $relativePath" }

    return File(this, normalized.toString())
}

fun Path.combineSafe(relativePath: Path): File {
    val normalized = relativePath.normalizeAndRelativize()
    if (normalized.startsWith("..")) {
        throw InvalidPathException(relativePath.toString(), "Bad relative path $relativePath")
    }
    check(!normalized.isAbsolute) { "Bad relative path $relativePath"}

    return resolve(normalized).toFile()
}

fun Path.normalizeAndRelativize(): Path = root?.relativize(this)?.normalize() ?: normalize()