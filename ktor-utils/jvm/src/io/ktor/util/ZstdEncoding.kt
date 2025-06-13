/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util

import com.github.luben.zstd.ZstdOutputStream
import io.ktor.util.cio.KtorDefaultPool
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.jvm.javaio.toOutputStream
import io.ktor.utils.io.pool.ObjectPool
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.writer
import jdk.internal.net.http.common.Log.channel
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import java.nio.ByteBuffer
import kotlin.coroutines.CoroutineContext

@OptIn(DelicateCoroutinesApi::class)
public fun ByteReadChannel.encoded(
    pool: ObjectPool<ByteBuffer> = KtorDefaultPool,
    coroutineContext: CoroutineContext = Dispatchers.Unconfined
): ByteReadChannel = GlobalScope.writer(coroutineContext, autoFlush = true) {
    this@encoded.encodeTo(channel, pool)
}.channel

private suspend fun ByteReadChannel.encodeTo(
    destination: ByteWriteChannel,
    pool: ObjectPool<ByteBuffer> = KtorDefaultPool
) {
    val zstdSteam = ZstdOutputStream(destination.toOutputStream())
    val buf = pool.borrow()

    try {
        while (!isClosedForRead) {
            buf.clear()
            if (readAvailable(buf) <= 0) continue
            buf.flip()

            zstdSteam.write(buf.array(), buf.position(), buf.remaining())
            buf.position(buf.limit())
        }

        zstdSteam.flush()
    } finally {
        pool.recycle(buf)
    }
}
