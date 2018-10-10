package io.ktor.util

import kotlinx.coroutines.*

private const val NONCE_SIZE_IN_BYTES = 8

@InternalAPI
actual fun generateNonce(): String = TODO()

@InternalAPI
actual fun Digest(name: String): Digest = object : Digest {
    private val state = mutableListOf<ByteArray>()
    override fun plusAssign(bytes: ByteArray) {
        state += bytes
    }

    override fun reset() {
        state.clear()
    }

    override suspend fun build(): ByteArray {
        TODO()
    }
}
