/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.quic.tls

import at.favre.lib.crypto.*
import io.ktor.network.quic.bytes.*
import io.ktor.network.quic.consts.*
import io.ktor.network.quic.util.*
import java.nio.*
import java.nio.charset.*
import javax.crypto.*
import javax.crypto.spec.*

internal class CryptoKeys(secret: ByteArray, version: UInt32, val debugLabel: String? = null) {
    private val key: ByteArray
    val iv: ByteArray
    private val hp: ByteArray

    private val logger = logger()

    init {
        if (version != QUICVersion.V1) {
            error("Unsupported version")
        }

        key = hkdfExpandLabel(secret, TLSConstants.V1.KEY_LABEL.labeled(version), 16)
        iv = hkdfExpandLabel(secret, TLSConstants.V1.IV_LABEL.labeled(version), 12)
        hp = hkdfExpandLabel(secret, TLSConstants.V1.HP_LABEL.labeled(version), 16)

        debugLog("init keys")
    }

    private fun String.labeled(version: UInt32): String {
        val prefix = when (version) {
            QUICVersion.V1 -> "quic"
            else -> unreachable()
        }
        return "$prefix $this"
    }

    fun encrypt(message: ByteArray, associatedData: ByteArray, nonce: ByteArray): ByteArray {
        val parameterSpec = GCMParameterSpec(128, nonce)
        writeCipher.init(Cipher.ENCRYPT_MODE, keySpec, parameterSpec)
        writeCipher.updateAAD(associatedData)
        return writeCipher.doFinal(message)
    }

    fun decrypt(message: ByteArray, associatedData: ByteArray, nonce: ByteArray): ByteArray {
        val parameterSpec = GCMParameterSpec(128, nonce)
        writeCipher.init(Cipher.DECRYPT_MODE, keySpec, parameterSpec)
        writeCipher.updateAAD(associatedData)
        return writeCipher.doFinal(message)
    }

    fun headerProtectionMask(sample: ByteArray): Long {
        return headerProtectionCipher.doFinal(sample).let { array ->
            debugLog("Sample: ${sample.toDebugString()}")
            debugLog("Mask: ${array.toDebugString()}")
            var i = 0
            var long = 0L
            while (i < 5) {
                long = long or (array[i].toUByte().toLong() shl ((4 - i) * 8))
                i++
            }
            long
        }
    }

    private val headerProtectionCipher by lazy {
        debugLog("Call init hp cipher")
        Cipher.getInstance("AES/ECB/NoPadding")?.apply {
            val keySpec = SecretKeySpec(hp, "AES")
            init(Cipher.ENCRYPT_MODE, keySpec)
        } ?: error("Expected cipher hp")
    }

    private val writeCipher by lazy {
        Cipher.getInstance("AES/GCM/NoPadding") ?: error("Expected cipher")
    }

    private val keySpec by lazy {
        SecretKeySpec(key, "AES")
    }

    companion object {
        private val ISO_8859_1 = Charset.forName("ISO-8859-1")

        fun initial(secret: ByteArray, version: UInt32, isServer: Boolean, debugLabel: String? = null): CryptoKeys {
            val withLabel = hkdfExpandLabel(secret, if (isServer) "server in" else "client in", 32)
            return CryptoKeys(withLabel, version, debugLabel)
        }

        private fun hkdfExpandLabel(secret: ByteArray, label: String, length: Short): ByteArray {
            val context = "".toByteArray(ISO_8859_1)
            val labelBytes = label.toByteArray(ISO_8859_1)

            val prefix = "tls13 ".toByteArray(ISO_8859_1)

            val hkdfLabel = ByteBuffer.allocate(
                /*capacity =*/ 2 + 1 + prefix.size + labelBytes.size + 1 + context.size
            )

            hkdfLabel.putShort(length)
            hkdfLabel.put((prefix.size + labelBytes.size).toByte())
            hkdfLabel.put(prefix)
            hkdfLabel.put(labelBytes)
            hkdfLabel.put(context.size.toByte())
            hkdfLabel.put(context)
            val hkdf = HKDF.fromHmacSha256()
            return hkdf.expand(secret, hkdfLabel.array(), length.toInt())
        }
    }

    private fun debugLog(message: String) {
        logger.debug("[$debugLabel] $message")
    }
}
