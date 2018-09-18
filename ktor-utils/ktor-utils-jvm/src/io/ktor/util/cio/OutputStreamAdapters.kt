package io.ktor.util.cio

import kotlinx.coroutines.io.*
import kotlinx.coroutines.io.jvm.javaio.*
import java.io.*
import java.nio.charset.*

suspend fun ByteWriteChannel.write(string: String, charset: Charset = Charsets.UTF_8) =
        writeFully(string.toByteArray(charset))

fun ByteWriteChannel.bufferedWriter(charset: Charset = Charsets.UTF_8): BufferedWriter =
        toOutputStream().bufferedWriter(charset)

fun ByteWriteChannel.writer(charset: Charset = Charsets.UTF_8): Writer =
        toOutputStream().writer(charset)

