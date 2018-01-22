package io.ktor.client.utils

import io.ktor.network.util.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.io.*
import kotlinx.io.pool.*


private val cpuCount: Int = Runtime.getRuntime().availableProcessors()

const val DEFAULT_HTTP_POOL_SIZE = 1000
const val DEFAULT_HTTP_BUFFER_SIZE = 4096

val HTTP_CLIENT_THREAD_COUNT: Int = maxOf(2, (cpuCount * 2 / 3))

val HTTP_CLIENT_DEFAULT_DISPATCHER by lazy {
    IOCoroutineDispatcher(HTTP_CLIENT_THREAD_COUNT)
}

object HttpClientDefaultPool : DefaultPool<ByteBuffer>(DEFAULT_HTTP_POOL_SIZE) {
    override fun produceInstance(): ByteBuffer = ByteBuffer.allocate(DEFAULT_HTTP_BUFFER_SIZE)!!

    override fun clearInstance(instance: ByteBuffer): ByteBuffer = instance.apply { clear() }
}
