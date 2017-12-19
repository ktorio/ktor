package io.ktor.cio

import kotlinx.coroutines.experimental.io.*
import kotlinx.coroutines.experimental.io.jvm.javaio.*
import java.io.*
import java.nio.charset.*

suspend fun ByteWriteChannel.write(string: String, charset: Charset = Charsets.UTF_8) =
        writeFully(string.toByteArray(charset))

fun ByteWriteChannel.bufferedWriter(charset: Charset = Charsets.UTF_8): BufferedWriter =
        toOutputStream().bufferedWriter(charset)

fun ByteWriteChannel.writer(charset: Charset = Charsets.UTF_8): Writer =
        toOutputStream().writer(charset)

