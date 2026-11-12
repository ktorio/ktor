/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.jvm.javaio

import io.ktor.utils.io.*
import io.ktor.utils.io.pool.*
import kotlinx.coroutines.*
import kotlinx.io.*
import java.io.InputStream
import java.nio.ByteBuffer
import kotlin.coroutines.CoroutineContext

/**
 * Creates a [ByteReadChannel] that reads from this stream on demand in the given [context].
 * Reads **block** the executing thread. Use a dispatcher suitable for blocking I/O
 * in [context], such as [Dispatchers.IO] used by default.
 *
 * The channel closes the stream at EOF or on cancellation.
 * The [ByteReadChannel.cancel] call returns without waiting for stream closure.
 *
 * The stream must support closure during an active read.
 * Cancellation and closure do not guarantee that a blocking read stops.
 *
 * ### Structured concurrency
 *
 * If [context] contains a [Job], it closes the stream on cancellation.
 * If that job is active when the channel is created, it also waits for stream closure before completing.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.utils.io.jvm.javaio.toByteReadChannel)
 */
@Suppress("UNUSED_PARAMETER")
public fun InputStream.toByteReadChannel(
    context: CoroutineContext = Dispatchers.IO,
    pool: ObjectPool<ByteBuffer>
): ByteReadChannel = RawSourceChannel(asSource(), context)

/**
 * Creates a [ByteReadChannel] that reads from this stream on demand in the given [context].
 * Reads **block** the executing thread. Use a dispatcher suitable for blocking I/O
 * in [context], such as [Dispatchers.IO] used by default.
 *
 * The channel closes the stream at EOF or on cancellation.
 * The [ByteReadChannel.cancel] call returns without waiting for stream closure.
 *
 * The stream must support closure during an active read.
 * Cancellation and closure do not guarantee that a blocking read stops.
 *
 * ### Structured concurrency
 *
 * If [context] contains a [Job], it closes the stream on cancellation.
 * If that job is active when the channel is created, it also waits for stream closure before completing.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.utils.io.jvm.javaio.toByteReadChannel)
 */
@Suppress("UNUSED_PARAMETER")
@JvmName("toByteReadChannelWithArrayPool")
public fun InputStream.toByteReadChannel(
    context: CoroutineContext = Dispatchers.IO,
    pool: ObjectPool<ByteArray> = ByteArrayPool
): ByteReadChannel = RawSourceChannel(asSource(), context)
