package io.ktor.util

import kotlinx.coroutines.*
import org.khronos.webgl.*
import kotlin.js.*

private const val NONCE_SIZE_IN_BYTES = 8

@InternalAPI
actual fun generateNonce(): String {
    val buffer = ByteArray(NONCE_SIZE_IN_BYTES)
    return hex(crypto.getRandomValues(buffer))
}

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
        val snapshot = state.reduce { a, b -> a + b }
        val digestBuffer = crypto.subtle.digest(name, snapshot).asDeferred().await()
        val digestView = DataView(digestBuffer)
        return ByteArray(digestView.byteLength) { digestView.getUint8(it) }
    }
}

private external object crypto {
    val subtle: SubtleCrypto

    fun getRandomValues(array: ByteArray): ByteArray
}

private external class SubtleCrypto {
    fun digest(algoName: String, buffer: ByteArray): Promise<ArrayBuffer>
}

/**
 * Compute SHA-1 hash for the specified [bytes]
 */
@KtorExperimentalAPI
actual fun sha1(bytes: ByteArray): ByteArray {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
}
