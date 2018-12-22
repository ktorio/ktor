package io.ktor.network.tls

import javax.crypto.*
import javax.crypto.spec.*


private val MASTER_SECRET_LABEL = "master secret".toByteArray()
private val KEY_EXPANSION_LABEL = "key expansion".toByteArray()

internal val CLIENT_FINISHED_LABEL = "client finished".toByteArray()
internal val SERVER_FINISHED_LABEL = "server finished".toByteArray()

internal fun ByteArray.clientKey(suite: CipherSuite) =
        SecretKeySpec(this, 2 * suite.macStrengthInBytes, suite.keyStrengthInBytes, suite.jdkCipherName.substringBefore("/"))

internal fun ByteArray.serverKey(suite: CipherSuite) =
        SecretKeySpec(this, 2 * suite.macStrengthInBytes + suite.keyStrengthInBytes, suite.keyStrengthInBytes, suite.jdkCipherName.substringBefore("/"))

internal fun ByteArray.clientIV(suite: CipherSuite) =
        copyOfRange(2 * suite.macStrengthInBytes + 2 * suite.keyStrengthInBytes, 2 * suite.macStrengthInBytes + 2 * suite.keyStrengthInBytes + suite.fixedIvLength)

internal fun ByteArray.serverIV(suite: CipherSuite) =
        copyOfRange(2 * suite.macStrengthInBytes + 2 * suite.keyStrengthInBytes + suite.fixedIvLength, 2 * suite.macStrengthInBytes + 2 * suite.keyStrengthInBytes + 2 * suite.fixedIvLength)

internal fun keyMaterial(masterSecret: SecretKey, seed: ByteArray, keySize: Int, macSize: Int, ivSize: Int): ByteArray {
    val materialSize = 2 * macSize + 2 * keySize + 2 * ivSize
    return PRF(masterSecret, KEY_EXPANSION_LABEL, seed, materialSize)
}

internal fun masterSecret(preMasterSecret: SecretKey, clientRandom: ByteArray, serverRandom: ByteArray): SecretKeySpec =
        PRF(preMasterSecret, MASTER_SECRET_LABEL, clientRandom + serverRandom, 48)
                .let { SecretKeySpec(it, preMasterSecret.algorithm) }
