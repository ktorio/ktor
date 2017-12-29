package io.ktor.http.cio.internals

import kotlinx.io.pool.*
import java.nio.*

internal const val CHAR_BUFFER_POOL_SIZE = 4096
internal const val CHAR_BUFFER_SIZE = 4096

internal val CharBufferPool: ObjectPool<CharBuffer> =
        object : DefaultPool<CharBuffer>(CHAR_BUFFER_POOL_SIZE) {
            override fun produceInstance(): CharBuffer =
                    ByteBuffer.allocateDirect(CHAR_BUFFER_SIZE).asCharBuffer()

            override fun clearInstance(instance: CharBuffer): CharBuffer =
                    instance.also { it.clear() }
        }
