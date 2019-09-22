package io.ktor.utils.io.jvm.javaio

import io.ktor.utils.io.pool.*

internal val ByteArrayPool = object : DefaultPool<ByteArray>(128) {
    override fun produceInstance() = ByteArray(4096)
}