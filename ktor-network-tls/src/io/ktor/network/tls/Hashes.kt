package io.ktor.network.tls

import io.ktor.http.cio.internals.*
import kotlinx.coroutines.experimental.io.*
import kotlinx.coroutines.experimental.io.packet.*
import java.security.*
import javax.crypto.*
import kotlin.coroutines.experimental.*


internal suspend fun hashMessages(
    messages: List<ByteReadPacket>,
    baseHash: String,
    coroutineContext: CoroutineContext
): ByteArray {
    val messageDigest = MessageDigest.getInstance(baseHash)
    val digestBytes = ByteArray(messageDigest.digestLength)
    val digest = digest(messageDigest, coroutineContext, digestBytes)
    for (packet in messages) {
        digest.channel.writePacket(packet)
    }
    digest.channel.close()
    digest.join()
    return digestBytes
}

private fun digest(
    digest: MessageDigest,
    coroutineContext: CoroutineContext,
    result: ByteArray
): ReaderJob = reader(coroutineContext) {
    digest.reset()
    val buffer = DefaultByteBufferPool.borrow()
    try {
        while (true) {
            buffer.clear()
            val rc = channel.readAvailable(buffer)
            if (rc == -1) break
            buffer.flip()
            digest.update(buffer)
        }

        digest.digest(result, 0, digest.digestLength)
    } finally {
        DefaultByteBufferPool.recycle(buffer)
    }
}

internal fun PRF(secret: SecretKey, label: ByteArray, seed: ByteArray, requiredLength: Int = 12) = P_hash(
    label + seed, Mac.getInstance(secret.algorithm), secret, requiredLength
)

private fun P_hash(seed: ByteArray, mac: Mac, secretKey: SecretKey, requiredLength: Int = 12): ByteArray {
    require(requiredLength >= 12)

    var A = seed
    var result = ByteArray(0)

    while (result.size < requiredLength) {
        mac.reset()
        mac.init(secretKey)
        mac.update(A)
        A = mac.doFinal()

        mac.reset()
        mac.init(secretKey)
        mac.update(A)
        mac.update(seed)

        result += mac.doFinal()
    }

    return result.copyOf(requiredLength)
}
