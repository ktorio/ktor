/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.core

import io.ktor.utils.io.*
import kotlinx.io.Buffer
import kotlin.math.*

/**
 * Append at most [maxSize] bytes from the specified [other] buffer into this using the end gap reservation if required.
 * @return number of bytes copied
 * @throws IllegalArgumentException if not enough space including an end gap
 */
@Deprecated(
    IO_DEPRECATION_MESSAGE,
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("write(other, min(other.size, maxSize.toLong())")
)
internal fun Buffer.writeBufferAppend(other: Buffer, maxSize: Int): Int {
    val byteCount = min(other.size, maxSize.toLong())
    write(other, byteCount)
    return byteCount.toInt()
}
