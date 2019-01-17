package io.ktor.network.tls

import java.security.*

internal data class EncryptionInfo(
    val serverPublic: PublicKey,
    val clientPublic: PublicKey,
    val clientPrivate: PrivateKey
)
