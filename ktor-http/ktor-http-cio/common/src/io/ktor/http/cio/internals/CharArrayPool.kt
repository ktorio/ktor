package io.ktor.http.cio.internals

import io.ktor.util.*
import kotlinx.io.pool.*

private const val CHAR_ARRAY_POOL_SIZE = 4096

/**
 * Number of characters that a array from the pool can store
 */
@KtorExperimentalAPI
const val CHAR_BUFFER_ARRAY_LENGTH: Int = 4096 / 2

internal val CharArrayPool: ObjectPool<CharArray> = object : DefaultPool<CharArray>(CHAR_ARRAY_POOL_SIZE) {
    override fun produceInstance(): CharArray = CharArray(CHAR_BUFFER_ARRAY_LENGTH)
}
