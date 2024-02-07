/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls

import io.ktor.utils.io.core.*
import java.security.cert.*

internal val readTLSCertificate = BytePacketParser<List<Certificate>> { input ->
    val certificatesChainLength = input.readTripleByteLength()
    var certificateBase = 0
    buildList {
        val factory = CertificateFactory.getInstance("X.509")!!

        while (certificateBase < certificatesChainLength) {
            val certificateLength = input.readTripleByteLength()
            if (certificateLength > (certificatesChainLength - certificateBase)) {
                throw TLSException("Certificate length is too big")
            }
            if (certificateLength > input.remaining) throw TLSException("Certificate length is too big")

            val certificate = ByteArray(certificateLength)
            input.readFully(certificate)
            certificateBase += certificateLength + 3

            add(factory.generateCertificate(certificate.inputStream()))
        }
    }
}

internal val writeTLSCertificate = BytePacketWriter<List<Certificate>> { output, value ->
    val certBytes = value.map { it.encoded }
    output.writeTripleByteLength(certBytes.sumOf { it.size + 3 })
    for (cert in certBytes) {
        output.writeTripleByteLength(cert.size)
        output.writeFully(cert)
    }
}
