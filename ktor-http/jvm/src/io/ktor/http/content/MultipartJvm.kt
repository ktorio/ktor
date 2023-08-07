/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.http.content

import io.ktor.util.*
import io.ktor.utils.io.*
import io.ktor.utils.io.bits.*
import io.ktor.utils.io.core.*
import io.ktor.utils.io.pool.*
import kotlinx.coroutines.*
import java.io.*

/**
 * Provides file item's content as an [InputStream]
 */
@Suppress("DeprecatedCallableAddReplaceWith")
@Deprecated("This API uses blocking InputStream. Please use provider() directly.")
public val PartData.FileItem.streamProvider: () -> InputStream get() = { MultipartInput(provider()).asStream() }

@Suppress("DEPRECATION")
private class MultipartInput(
    private val channel: ByteReadChannel
) : Input() {

    override fun fill(destination: Memory, offset: Int, length: Int): Int {
        return runBlocking {
            val buffer = ByteArrayPool.borrow()
            try {
                val rc = channel.readAvailable(buffer, 0, minOf(length, buffer.size)).coerceAtLeast(0)
                destination.storeByteArray(offset, buffer, 0, rc)
                rc
            } finally {
                ByteArrayPool.recycle(buffer)
            }
        }
    }

    override fun closeSource() {
        channel.cancel()
    }
}
