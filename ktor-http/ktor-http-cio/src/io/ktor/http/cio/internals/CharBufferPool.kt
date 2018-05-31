package io.ktor.http.cio.internals

import kotlinx.io.pool.*
import java.nio.*

private const val CHAR_BUFFER_POOL_SIZE = 4096
internal const val CHAR_BUFFER_BYTES = 4096
internal const val CHAR_BUFFER_LENGTH = 4096 / 2

internal val CharBufferPool: ObjectPool<CharBuffer> =
        object : DefaultPool<CharBuffer>(CHAR_BUFFER_POOL_SIZE) {
            override fun produceInstance(): CharBuffer =
                    ByteBuffer.allocateDirect(CHAR_BUFFER_BYTES).asCharBuffer()

            override fun clearInstance(instance: CharBuffer): CharBuffer =
                    instance.also { it.clear() }
        }
