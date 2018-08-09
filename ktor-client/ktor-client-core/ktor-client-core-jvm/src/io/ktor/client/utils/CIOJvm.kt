package io.ktor.client.utils

import io.ktor.util.*
import kotlinx.coroutines.*
import kotlinx.io.pool.*
import java.nio.*

private val cpuCount: Int = Runtime.getRuntime().availableProcessors()

@Deprecated(
    "HTTP_CLIENT_THREAD_COUNT is deprecated. Use [HttpClientEngineConfig.threadsCount] instead.",
    level = DeprecationLevel.ERROR
)
actual val HTTP_CLIENT_THREAD_COUNT: Int = maxOf(2, (cpuCount * 2 / 3))

@Deprecated(
    "HTTP_CLIENT_DEFAULT_DISPATCHER is deprecated. Use [HttpClient.coroutineContext] instead.",
    level = DeprecationLevel.ERROR
)
actual val HTTP_CLIENT_DEFAULT_DISPATCHER: CoroutineDispatcher get() = Dispatchers.IO

/**
 * Singleton pool of [ByteBuffer] objects used for [HttpClient].
 */

val HttpClientDefaultPool = ByteBufferPool()

@InternalAPI
class ByteBufferPool : DefaultPool<ByteBuffer>(DEFAULT_HTTP_POOL_SIZE) {
    override fun produceInstance(): ByteBuffer = ByteBuffer.allocate(DEFAULT_HTTP_BUFFER_SIZE)!!

    override fun clearInstance(instance: ByteBuffer): ByteBuffer = instance.apply { clear() }
}
