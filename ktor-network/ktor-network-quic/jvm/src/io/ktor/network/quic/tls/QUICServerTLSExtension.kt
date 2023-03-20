/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.quic.tls

import io.ktor.network.quic.connections.*
import net.luminis.tls.extension.*

internal class QUICServerTLSExtension(
    val transportParameters: TransportParameters,
) : Extension() {
    override fun getBytes(): ByteArray {
        return transportParameters.toBytes()
    }
}
