package io.ktor.util

import java.io.*

/**
 * Append a [relativePath] safely that means that adding any extra `..` path elements will not let
 * access anything out of the reference directory (unless you have symbolic or hard links or multiple mount points)
 */
@KtorExperimentalAPI
fun File.combineSafe(relativePath: String): File = combineSafe(this, File(relativePath))

/**
 * Remove all redundant `.` and `..` path elements. Leading `..` are also considered redundant.
 */
@KtorExperimentalAPI
fun File.normalizeAndRelativize(): File = normalize().notRooted().dropLeadingTopDirs()

private fun combineSafe(dir: File, relativePath: File): File {
    val normalized = relativePath.normalizeAndRelativize()
    if (normalized.startsWith("..")) {
        throw IllegalArgumentException("Bad relative path $relativePath")
    }
    check(!normalized.isAbsolute) { "Bad relative path $relativePath"}

    return File(dir, normalized.path)
}

private fun File.notRooted(): File {
    if (!isRooted) return this

    var current: File = this

    while (true) {
        val parent = current.parentFile ?: break
        current = parent
    }

    // current = this.root

    return File(path.drop(current.name.length).dropWhile { it == '\\' || it == '/' })
}

private fun File.dropLeadingTopDirs(): File {
    var startIndex = 0
    val path = path!!
    val lastIndex = path.length - 1

    while (startIndex < lastIndex) {
        if (path[startIndex] == '.') {
            val second = path[startIndex + 1]
            if (second == '\\' || second == '/') {
                startIndex += 2 // skip 2 characters: ./ or .\
            } else if (second == '.') {
                if (startIndex + 2 == path.length) {
                    startIndex += 2 // skip the only 2 characters remaining: ..
                } else if (path[startIndex + 2].let { it == '/' || it == '\\' }) {
                    startIndex += 3 // skip 3 characters: ../ or ..\
                }
            } else {
                break
            }
        } else {
            break
        }
    }

    if (startIndex == 0) return this
    if (startIndex >= path.length) return File(".")

    return File(path.substring(startIndex))
}
