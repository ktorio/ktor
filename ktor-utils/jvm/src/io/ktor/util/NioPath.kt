/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util

import java.io.File
import java.nio.file.InvalidPathException
import java.nio.file.Path

/**
 * Append a [relativePath] safely that means that adding any extra `..` path elements will not let
 * access anything out of the reference directory (unless you have symbolic or hard links or multiple mount points)
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.combineSafe)
 */
public fun Path.combineSafe(relativePath: Path): Path {
    val normalized = relativePath.normalizeAndRelativize()
    if (normalized.startsWith("..")) {
        throw InvalidPathException(relativePath.toString(), "Relative path $relativePath beginning with .. is invalid")
    }
    check(!normalized.isAbsolute) { "Bad relative path $relativePath" }

    // On JVM 8 ZipPath implementation misses this check,
    // so we have to check the path is not empty to not get an `ArrayIndexOutOfBoundsException`
    if (nameCount == 0) return normalized

    return resolve(normalized)
}

/**
 * Remove all redundant `.` and `..` path elements. Leading `..` are also considered redundant.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.normalizeAndRelativize)
 */
public fun Path.normalizeAndRelativize(): Path =
    root?.relativize(this)?.normalize()?.dropLeadingTopDirs() ?: normalize().dropLeadingTopDirs()

private fun Path.dropLeadingTopDirs(): Path {
    val startIndex = indexOfFirst { it.toString() != ".." }
    if (startIndex <= 0) return this
    return subpath(startIndex, nameCount)
}

/**
 * Append a [relativePath] safely that means that adding any extra `..` path elements will not let
 * access anything out of the reference directory (unless you have symbolic or hard links or multiple mount points)
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.combineSafe)
 */
public fun File.combineSafe(relativePath: Path): File {
    val normalized = relativePath.normalizeAndRelativize()
    if (normalized.startsWith("..")) {
        throw InvalidPathException(relativePath.toString(), "Relative path $relativePath beginning with .. is invalid")
    }
    check(!normalized.isAbsolute) { "Bad relative path $relativePath" }

    return File(this, normalized.toString())
}
