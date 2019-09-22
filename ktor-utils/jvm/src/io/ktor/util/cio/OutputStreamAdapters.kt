/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.cio

import io.ktor.util.*
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import java.io.*
import java.nio.charset.*

/**
 * Write a [string] in the specified [charset]
 */
@KtorExperimentalAPI
suspend fun ByteWriteChannel.write(string: String, charset: Charset = Charsets.UTF_8) =
        writeFully(string.toByteArray(charset))

/**
 * Open a buffered writer to the channel
 */
@KtorExperimentalAPI
fun ByteWriteChannel.bufferedWriter(charset: Charset = Charsets.UTF_8): BufferedWriter =
        toOutputStream().bufferedWriter(charset)

/**
 * Open a writer to the channel
 */
@KtorExperimentalAPI
fun ByteWriteChannel.writer(charset: Charset = Charsets.UTF_8): Writer =
        toOutputStream().writer(charset)

