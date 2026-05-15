/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.http.content

import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.io.Writer

/**
 * Executes [block] with this channel represented as a blocking [OutputStream].
 *
 * Blocking operations are dispatched to [dispatcher] so they don't block event loop threads. This function doesn't
 * close the channel after [block] finishes: the caller that owns the [ByteWriteChannel] lifecycle is responsible for
 * closing it from a suspending context.
 *
 * The stream passed to [block] is owned by this function and must not be used after [block] returns.
 */
internal suspend inline fun ByteWriteChannel.withBlockingOutputStream(
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    crossinline block: suspend (OutputStream) -> Unit,
) {
    withContext(dispatcher) {
        val outputStream = toOutputStream()
        block(outputStream)
    }
}

/**
 * Executes [block] with this channel represented as a blocking [Writer] using [charset].
 *
 * Blocking operations are dispatched to [dispatcher] so they don't block event loop threads. The [Writer] is closed
 * inside [dispatcher] to finalize encoder state, but the underlying stream close is suppressed: the caller that owns
 * the [ByteWriteChannel] lifecycle is responsible for closing the channel from a suspending context.
 *
 * The writer passed to [block] is owned by this function and must not be used after [block] returns.
 */
internal suspend inline fun ByteWriteChannel.withBlockingWriter(
    charset: Charset,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    crossinline block: suspend (Writer) -> Unit,
) {
    withContext(dispatcher) {
        val writer = toOutputStream().nonClosing().writer(charset)
        writer.use { block(it) }
    }
}

private fun OutputStream.nonClosing(): OutputStream = NonClosingOutputStream(this)

/** A wrapper preventing calling `runBlocking { flushAndClose() }` which could lead to a deadlock. */
private class NonClosingOutputStream(private val delegate: OutputStream) : OutputStream() {
    override fun write(b: Int) = delegate.write(b)
    override fun write(b: ByteArray) = delegate.write(b)
    override fun write(b: ByteArray, off: Int, len: Int) = delegate.write(b, off, len)
    override fun flush() = delegate.flush()
    override fun close() = Unit
}
