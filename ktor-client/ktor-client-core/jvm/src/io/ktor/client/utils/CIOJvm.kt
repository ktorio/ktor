package io.ktor.client.utils

import io.ktor.util.*
import kotlinx.coroutines.*
import kotlinx.io.pool.*
import java.nio.*

@Suppress("KDocMissingDocumentation", "unused")
@Deprecated(
    "Binary compatibility",
    level = DeprecationLevel.HIDDEN
)
val HTTP_CLIENT_THREAD_COUNT: Int = 2

@Suppress("KDocMissingDocumentation", "unused")
@Deprecated(
    "Binary compatibility",
    level = DeprecationLevel.HIDDEN
)
val HTTP_CLIENT_DEFAULT_DISPATCHER: CoroutineDispatcher get() = Dispatchers.IO

/**
 * Singleton pool of [ByteBuffer] objects used for [HttpClient].
 */

val HttpClientDefaultPool = ByteBufferPool()

@InternalAPI
class ByteBufferPool : DefaultPool<ByteBuffer>(DEFAULT_HTTP_POOL_SIZE) {
    override fun produceInstance(): ByteBuffer = ByteBuffer.allocate(DEFAULT_HTTP_BUFFER_SIZE)!!

    override fun clearInstance(instance: ByteBuffer): ByteBuffer = instance.apply { clear() }
}
