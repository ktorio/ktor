/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.quic.tls

import io.ktor.network.quic.errors.*
import net.luminis.tls.*
import net.luminis.tls.alert.*

internal fun TlsProtocolException.toError(): QUICTransportError {
    val code = when (this) {
        is ErrorAlert -> alertDescription().value
        else -> error("Unexpected alert from TLS component")
    }

    return CryptoHandshakeError_v1(code.toUByte()).invoke("${alertDescription()} - $message")
}
