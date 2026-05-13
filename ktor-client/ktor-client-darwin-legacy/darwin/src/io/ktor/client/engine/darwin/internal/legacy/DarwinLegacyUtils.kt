/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.darwin.internal.legacy

import io.ktor.client.call.*
import io.ktor.http.content.*
import io.ktor.utils.io.*
import kotlinx.cinterop.*
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.io.readByteArray
import platform.Foundation.*
import platform.posix.memcpy

@OptIn(UnsafeNumber::class)
internal val NSURLSessionTask.id: ULong get() = this.taskIdentifier.toULong()

@OptIn(DelicateCoroutinesApi::class)
internal suspend fun OutgoingContent.toNSData(): NSData? = when (this) {
    is OutgoingContent.ByteArrayContent -> bytes().toNSData()
    is OutgoingContent.WriteChannelContent -> GlobalScope.writer(Dispatchers.Unconfined) {
        writeTo(channel)
    }.channel.readRemaining().readByteArray().toNSData()
    is OutgoingContent.ReadChannelContent -> readFrom().readRemaining().readByteArray().toNSData()
    is OutgoingContent.NoContent -> null
    is OutgoingContent.ContentWrapper -> delegate().toNSData()
    is OutgoingContent.ProtocolUpgrade -> throw UnsupportedContentTypeException(this)
}

@OptIn(UnsafeNumber::class, ExperimentalForeignApi::class)
internal fun ByteArray.toNSData(): NSData = NSMutableData().apply {
    if (isEmpty()) return@apply
    this@toNSData.usePinned {
        appendBytes(it.addressOf(0), size.convert())
    }
}

@OptIn(UnsafeNumber::class, ExperimentalForeignApi::class)
internal fun NSData.toByteArray(): ByteArray {
    val result = ByteArray(length.toInt())
    if (result.isEmpty()) return result

    result.usePinned {
        memcpy(it.addressOf(0), bytes, length)
    }

    return result
}

/**
 * Writes the entire content of [NSData] to this channel without intermediate allocations.
 * Writes directly from NSData's byte pointer to avoid creating an intermediate ByteArray.
 */
@OptIn(UnsafeNumber::class, ExperimentalForeignApi::class)
internal suspend fun ByteWriteChannel.writeFully(data: NSData) {
    val length = data.length.toLong()
    if (length == 0L) return

    val bytes = data.bytes ?: return
    @Suppress("UNCHECKED_CAST")
    writeFully(bytes as CPointer<ByteVar>, 0, length)
}

/**
 * Executes the given block function on this resource and then releases it correctly whether an
 * exception is thrown or not.
 */
@OptIn(ExperimentalForeignApi::class)
internal inline fun <T : CPointed, R> CPointer<T>.use(block: (CPointer<T>) -> R): R {
    try {
        return block(this)
    } finally {
        CFBridgingRelease(this)
    }
}
