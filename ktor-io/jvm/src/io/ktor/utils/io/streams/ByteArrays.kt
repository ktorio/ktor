package io.ktor.utils.io.streams

import io.ktor.utils.io.pool.*

internal const val ByteArrayPoolBufferSize = 4096

internal val ByteArrayPool = object : DefaultPool<ByteArray>(128) {
    final override fun produceInstance(): ByteArray {
        return ByteArray(ByteArrayPoolBufferSize)
    }

    final override fun validateInstance(instance: ByteArray) {
        require(instance.size == ByteArrayPoolBufferSize) { "Unable to recycle buffer of wrong size: ${instance.size} != 4096" }
        super.validateInstance(instance)
    }
}
