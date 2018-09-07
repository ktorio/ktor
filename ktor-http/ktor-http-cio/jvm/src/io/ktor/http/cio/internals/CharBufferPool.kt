package io.ktor.http.cio.internals

import io.ktor.util.*
import kotlinx.io.pool.*
import java.nio.*

private const val CHAR_BUFFER_POOL_SIZE = 4096

/**
 * Char buffer size in bytes used in internal buffer pool
 */
@KtorExperimentalAPI
const val CHAR_BUFFER_BYTES: Int = 4096

/**
 * Number of characters that a buffer from the pool can store
 */
@KtorExperimentalAPI
const val CHAR_BUFFER_LENGTH: Int = 4096 / 2

internal val CharBufferPool: ObjectPool<CharBuffer> =
        object : DefaultPool<CharBuffer>(CHAR_BUFFER_POOL_SIZE) {
            override fun produceInstance(): CharBuffer =
                    ByteBuffer.allocateDirect(CHAR_BUFFER_BYTES).asCharBuffer()

            override fun clearInstance(instance: CharBuffer): CharBuffer =
                    instance.also { it.clear() }
        }
