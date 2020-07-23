/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.utils

import io.ktor.util.*
import io.ktor.utils.io.pool.*
import io.ktor.utils.io.pool.ByteBufferPool
import java.nio.*

/**
 * Singleton pool of [ByteBuffer] objects used for [HttpClient].
 */
@InternalAPI
val HttpClientDefaultPool = ByteBufferPool()

@InternalAPI
@Deprecated(
    level = DeprecationLevel.WARNING,
    message = "ByteBufferPool is moved to `io` module",
    replaceWith = ReplaceWith("ByteBufferPool", "io.ktor.utils.io.pool.ByteBufferPool")
)
class ByteBufferPool : DefaultPool<ByteBuffer>(DEFAULT_HTTP_POOL_SIZE) {
    override fun produceInstance(): ByteBuffer = ByteBuffer.allocate(DEFAULT_HTTP_BUFFER_SIZE)!!

    override fun clearInstance(instance: ByteBuffer): ByteBuffer = instance.apply {
        clear()
        order(ByteOrder.BIG_ENDIAN)
    }
}
