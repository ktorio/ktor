package org.jetbrains.ktor.util

import java.io.*
import java.nio.file.*

fun Path.extension() = fileName.toString().substringAfter(".")
fun String.extension() = split("/\\").last().substringAfter(".")
fun File.safeAppend(relativePath: Path): File {
    val normalized = relativePath.normalizeAndRelativize()
    if (normalized.startsWith("..")) {
        throw IllegalArgumentException("Bad relative path $relativePath")
    }

    return File(this, normalized.toString())
}

fun Path.safeAppend(relativePath: Path): Path {
    val normalized = relativePath.normalizeAndRelativize()
    if (normalized.startsWith("..")) {
        throw IllegalArgumentException("Bad relative path $relativePath")
    }

    return resolve(normalized)
}

fun Path.normalizeAndRelativize() = root?.relativize(this)?.normalize() ?: normalize()