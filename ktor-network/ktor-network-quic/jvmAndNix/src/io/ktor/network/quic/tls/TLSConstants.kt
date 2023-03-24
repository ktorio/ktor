/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.quic.tls

internal object TLSConstants {
    object V1 {
        val SALT = byteArrayOf(
            0x38,
            0x76,
            0x2c,
            0xf7.toByte(),
            0xf5.toByte(),
            0x59,
            0x34,
            0xb3.toByte(),
            0x4d,
            0x17,
            0x9a.toByte(),
            0xe6.toByte(),
            0xa4.toByte(),
            0xc8.toByte(),
            0x0c,
            0xad.toByte(),
            0xcc.toByte(),
            0xbb.toByte(),
            0x7f,
            0x0a
        )

        const val KEY_LABEL = "key"
        const val IV_LABEL = "iv"
        const val HP_LABEL = "hp"
    }
}
