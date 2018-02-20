package io.ktor.client.utils

import io.ktor.network.util.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.io.*
import kotlinx.io.pool.*


private val cpuCount: Int = Runtime.getRuntime().availableProcessors()

/**
 * Maximum number of buffers to be allocated in the [HttpClientDefaultPool].
 */
const val DEFAULT_HTTP_POOL_SIZE = 1000

/**
 * Size of each buffer in the [HttpClientDefaultPool].
 */
const val DEFAULT_HTTP_BUFFER_SIZE = 4096

/**
 * Number of threads used for http clients: A little less than the [cpuCount] and 2 at least.
 */
val HTTP_CLIENT_THREAD_COUNT: Int = maxOf(2, (cpuCount * 2 / 3))

/**
 * Default [IOCoroutineDispatcher] that uses [HTTP_CLIENT_THREAD_COUNT] as the number of threads.
 */
val HTTP_CLIENT_DEFAULT_DISPATCHER by lazy {
    IOCoroutineDispatcher(HTTP_CLIENT_THREAD_COUNT)
}

/**
 * Singleton pool of [ByteBuffer] objects used for [HttpClient].
 */
object HttpClientDefaultPool : DefaultPool<ByteBuffer>(DEFAULT_HTTP_POOL_SIZE) {
    override fun produceInstance(): ByteBuffer = ByteBuffer.allocate(DEFAULT_HTTP_BUFFER_SIZE)!!

    override fun clearInstance(instance: ByteBuffer): ByteBuffer = instance.apply { clear() }
}

/**
 * Utility function to try to execute a suspend [block] of code a number of [attempts] until
 * the block returns true. Returns false if no every call failed.
 */
suspend inline fun tryExecute(attempts: Int, noinline block: suspend () -> Boolean): Boolean {
    repeat(attempts) {
        if (block()) return true
    }

    return false
}