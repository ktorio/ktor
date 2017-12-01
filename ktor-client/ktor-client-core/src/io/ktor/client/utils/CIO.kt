package io.ktor.client.utils

import kotlinx.coroutines.experimental.io.*
import kotlinx.io.pool.*


val DEFAULT_HTTP_POOL_SIZE = 1000
val DEFAULT_HTTP_BUFFER_SIZE = 4096

object HttpClientDefaultPool : DefaultPool<ByteBuffer>(DEFAULT_HTTP_POOL_SIZE) {
    override fun produceInstance(): ByteBuffer = ByteBuffer.allocate(DEFAULT_HTTP_BUFFER_SIZE)!!

    override fun clearInstance(instance: ByteBuffer): ByteBuffer = instance.apply { clear() }
}
