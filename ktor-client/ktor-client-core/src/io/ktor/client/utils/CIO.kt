package io.ktor.client.utils

import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.io.*
import kotlinx.io.pool.*


val DEFAULT_HTTP_POOL_SIZE = 1000
val DEFAULT_HTTP_BUFFER_SIZE = 4096
val HTTP_CLIENT_THREAD_COUNT = 4

val HTTP_CLIENT_DEFAULT_DISPATCHER by lazy {
    newFixedThreadPoolContext(HTTP_CLIENT_THREAD_COUNT, "ktor-client-dispatcher")
}

object HttpClientDefaultPool : DefaultPool<ByteBuffer>(DEFAULT_HTTP_POOL_SIZE) {
    override fun produceInstance(): ByteBuffer = ByteBuffer.allocate(DEFAULT_HTTP_BUFFER_SIZE)!!

    override fun clearInstance(instance: ByteBuffer): ByteBuffer = instance.apply { clear() }
}
