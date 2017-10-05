package io.ktor.util

import java.io.*
import java.nio.file.*

fun Path.extension() = fileName.toString().substringAfter(".")

fun File.combineSafe(relativePath: String): File = combineSafe(Paths.get(relativePath))

fun File.combineSafe(relativePath: Path): File {
    val normalized = relativePath.normalizeAndRelativize()
    if (normalized.startsWith("..")) {
        throw InvalidPathException(relativePath.toString(), "Bad relative path")
    }

    return File(this, normalized.toString())
}

fun Path.combineSafe(relativePath: Path): File {
    val normalized = relativePath.normalizeAndRelativize()
    if (normalized.startsWith("..")) {
        throw InvalidPathException(relativePath.toString(), "Bad relative path")
    }

    return resolve(normalized).toFile()
}

fun Path.normalizeAndRelativize(): Path = root?.relativize(this)?.normalize() ?: normalize()