/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.network.tls.extensions

import io.ktor.network.tls.*
import io.ktor.utils.io.core.*

public enum class TLSExtensionType(public val code: Short) {
    SERVER_NAME(0),
    MAX_FRAGMENT_LENGTH(1),
    CLIENT_CERTIFICATE_URL(2),
    TRUSTED_CA_KEYS(3),
    TRUNCATED_HMAC(4),
    STATUS_REQUEST(5),

    ELLIPTIC_CURVES(10),
    EC_POINT_FORMAT(11),
    SIGNATURE_ALGORITHMS(13);

    public companion object {
        public fun byCode(code: Int): TLSExtensionType =
            values().find { it.code == code.toShort() } ?: throw TLSException(
                "Unknown server hello extension type: $code"
            )
    }
}

internal class TLSExtension(
    val type: TLSExtensionType,
    val length: Int,
    val packet: ByteReadPacket
)
