/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls

import io.ktor.network.tls.extensions.*
import io.ktor.utils.io.core.*
import java.security.*
import java.security.interfaces.*
import java.security.spec.*

internal val readCurveParams = BytePacketParser { input ->
    val type = input.readByte().toInt() and 0xff
    when (ServerKeyExchangeType.byCode(type)) {
        ServerKeyExchangeType.NamedCurve -> {
            val curveId = input.readShort()
            NamedCurve.fromCode(curveId) ?: throw TLSException("Unknown EC id")
        }
        ServerKeyExchangeType.ExplicitPrime -> error("ExplicitPrime server key exchange type is not yet supported")
        ServerKeyExchangeType.ExplicitChar -> error("ExplicitChar server key exchange type is not yet supported")
    }
}

internal val writeCurveParams = BytePacketWriter { output, value: NamedCurve ->
    output.writeByte(ServerKeyExchangeType.NamedCurve.code.toByte())
    output.writeShort(value.code)
}


internal fun generateECKeys(curve: NamedCurve, point: ECPoint): EncryptionInfo {
    val keys = KeyPairGenerator.getInstance("EC")!!.run {
        initialize(ECGenParameterSpec(curve.name))
        generateKeyPair()!!
    }

    val publicKey = keys.public as ECPublicKey
    val factory = KeyFactory.getInstance("EC")!!
    val public = factory.generatePublic(ECPublicKeySpec(point, publicKey.params!!))!!

    return EncryptionInfo(public, keys.public, keys.private)
}
