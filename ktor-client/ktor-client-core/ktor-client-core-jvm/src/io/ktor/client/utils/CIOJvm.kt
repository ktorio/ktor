package io.ktor.client.utils

import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.scheduling.*
import kotlinx.io.pool.*
import java.nio.*

private val cpuCount: Int = Runtime.getRuntime().availableProcessors()

actual val HTTP_CLIENT_THREAD_COUNT: Int = maxOf(2, (cpuCount * 2 / 3))

actual val HTTP_CLIENT_DEFAULT_DISPATCHER: CoroutineDispatcher by lazy {
    ExperimentalCoroutineDispatcher(HTTP_CLIENT_THREAD_COUNT)
}

/**
 * Singleton pool of [ByteBuffer] objects used for [HttpClient].
 */
object HttpClientDefaultPool : DefaultPool<ByteBuffer>(DEFAULT_HTTP_POOL_SIZE) {
    override fun produceInstance(): ByteBuffer = ByteBuffer.allocate(DEFAULT_HTTP_BUFFER_SIZE)!!

    override fun clearInstance(instance: ByteBuffer): ByteBuffer = instance.apply { clear() }
}
