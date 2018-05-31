package io.ktor.network.tls.extensions

import io.ktor.network.tls.*
import kotlinx.io.core.*

internal enum class TLSExtensionType(val code: Short) {
    SERVER_NAME(0),
    MAX_FRAGMENT_LENGTH(1),
    CLIENT_CERTIFICATE_URL(2),
    TRUSTED_CA_KEYS(3),
    TRUNCATED_HMAC(4),
    STATUS_REQUEST(5),

    ELLIPTIC_CURVES(10),
    EC_POINT_FORMAT(11),
    SIGNATURE_ALGORITHMS(13);

    companion object {
        fun byCode(code: Int): TLSExtensionType =
            values().find { it.code == code.toShort() } ?: throw TLSException("Unknown server hello extension type: $code")
    }
}

internal class TLSExtension(
    val type: TLSExtensionType,
    val length: Int,
    val packet: ByteReadPacket
)
