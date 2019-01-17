package io.ktor.network.tls

import io.ktor.network.tls.extensions.*
import java.security.*

internal class CertificateInfo(
    val types: ByteArray,
    val hashAndSign: Array<HashAndSign>,
    val authorities: Set<Principal>
)
