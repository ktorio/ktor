package io.ktor.network.tls

import javax.crypto.*

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
