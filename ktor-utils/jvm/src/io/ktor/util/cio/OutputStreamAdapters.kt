/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util.cio

import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import java.io.*
import java.nio.charset.*

/**
 * Write a [string] in the specified [charset]
 */
@Deprecated(
    "This is going to be removed or renamed.",
    ReplaceWith("writeFully(string.toByteArray(charset))", "io.ktor.utils.io.writeFully")
)
public suspend fun ByteWriteChannel.write(string: String, charset: Charset = Charsets.UTF_8) {
    writeFully(string.toByteArray(charset))
}

/**
 * Open a buffered writer to the channel
 */
public fun ByteWriteChannel.bufferedWriter(charset: Charset = Charsets.UTF_8): BufferedWriter =
    toOutputStream().bufferedWriter(charset)

/**
 * Open a writer to the channel
 */
public fun ByteWriteChannel.writer(charset: Charset = Charsets.UTF_8): Writer =
    toOutputStream().writer(charset)
