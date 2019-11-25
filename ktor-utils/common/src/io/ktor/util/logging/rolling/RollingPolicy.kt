/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.logging.rolling

import io.ktor.util.*
import kotlin.math.*
import kotlin.time.*

@ExperimentalTime
@KtorExperimentalAPI
class RollingPolicy(
    val maxFileSize: FileSize = 100.MiB,
    val maxTotlalCount: Int = 50,
    val maxTotalSize: FileSize = 1.GiB,
    val keepUntil: Duration = 30.days
)

/**
 * Represents a file size.
 *
 * @property bytesCount is a number of bytes (without multiplier)
 * @property kib is a number of bytes measured in kilobytes
 * @property mib is a number of bytes measured in megabytes
 * @property gib is a number of bytes measured in gigabytes
 */
@KtorExperimentalAPI
class FileSize(val bytesCount: Long) {
    val kib: Long get() = bytesCount / 1024L
    val mib: Long get() = bytesCount / (1024L * 1024)
    val gib: Long get() = bytesCount / (1024L * 1024 * 1024)

    override fun toString(): String {
        return "FileSize(${format()})"
    }
}

private val fileSizeUnits = listOf("B", "KiB", "MiB", "GiB", "TiB")

/**
 * Creates [FileSize] instance based on the specified number of kilobytes.
 */
@KtorExperimentalAPI
val Int.KiB: FileSize
    get() = FileSize(this * 1024L)

/**
 * Creates [FileSize] instance based on the specified number of megabytes.
 */
@KtorExperimentalAPI
val Int.MiB: FileSize
    get() = FileSize(this * 1024L * 1024)

/**
 * Creates [FileSize] instance based on the specified number of gigabytes.
 */
@KtorExperimentalAPI
val Int.GiB: FileSize
    get() = FileSize(this * 1024L * 1024)

@KtorExperimentalAPI
fun FileSize.format(): String = buildString {
    var value = bytesCount
    var remaining = 0
    var unitIndex = 0

    while (value > 1024 && unitIndex < fileSizeUnits.lastIndex) {
        val divided = value / 1024

        remaining = (value % 1024).toInt()
        value = divided
        unitIndex++
    }

    val remFrac = (remaining.toDouble() * 100 / 1024.0).roundToInt()

    append(value.toString())
    append('.')
    append(remFrac.toString())
    append(' ')
    append(fileSizeUnits[unitIndex])
}
