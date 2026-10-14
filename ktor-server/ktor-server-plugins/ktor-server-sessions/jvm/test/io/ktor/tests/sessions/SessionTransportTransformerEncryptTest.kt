/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.sessions

import io.ktor.server.sessions.*
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.test.*

class SessionTransportTransformerEncryptTest {

    @Test
    fun `accepts 32-byte AES-256 encryption key`() {
        // AES-CBC requires a 16-byte IV regardless of key length. Constructing the transformer
        // with a 32-byte key used to fail with InvalidAlgorithmParameterException because the
        // IV was generated using the key size instead of the cipher block size.
        SessionTransportTransformerEncrypt(ByteArray(32), ByteArray(32))
    }

    @Test
    fun `transformWrite generates 16-byte IV for AES-256`() {
        val transformer = SessionTransportTransformerEncrypt(ByteArray(32), ByteArray(32))
        val encoded = transformer.transformWrite("hello")
        val iv = encoded.substringBefore('/').hexToByteArray()
        assertEquals(16, iv.size)
    }

    @Test
    fun `round-trip preserves payload for AES-128, AES-192 and AES-256`() {
        for (keyLength in intArrayOf(16, 24, 32)) {
            val transformer = SessionTransportTransformerEncrypt(ByteArray(keyLength), ByteArray(32))
            val original = "session-value-$keyLength"
            val encoded = transformer.transformWrite(original)
            val decoded = transformer.transformRead(encoded)
            assertEquals(original, decoded, "round-trip failed for AES-${keyLength * 8}")
        }
    }

    @Test
    fun `uses cipher block size for non-AES algorithm (DESede)`() {
        val encryptionKeySpec = SecretKeySpec(ByteArray(24), "DESede")
        val signKeySpec = SecretKeySpec(ByteArray(32), "HmacSHA256")

        val transformer = SessionTransportTransformerEncrypt(
            encryptionKeySpec = encryptionKeySpec,
            signKeySpec = signKeySpec,
        )

        val original = "non-aes-session-value"
        val encoded = transformer.transformWrite(original)
        val iv = encoded.substringBefore('/').hexToByteArray()

        assertEquals(8, iv.size, "DESede should use an 8-byte IV matching its block size")
        assertEquals(original, transformer.transformRead(encoded))
    }

    @Test
    fun `rejects cookie with tampered IV (CBC bit-flip forgery)`() {
        val transformer = SessionTransportTransformerEncrypt(encryptKey, signKey)
        val original = """{"userId":42,"role":"user"}"""
        val encoded = transformer.transformWrite(original)

        val forged = flipFirstBlock(encoded, original.toByteArray(), """{"userId": 1,"role":"user"}""".toByteArray())

        assertNull(transformer.transformRead(forged))
    }

    @Test
    fun `rejects tampered IV even with backwardCompatibleRead`() {
        val transformer = SessionTransportTransformerEncrypt(encryptKey, signKey, backwardCompatibleRead = true)
        val original = """{"userId":42,"role":"user"}"""
        val encoded = transformer.transformWrite(original)

        val forged = flipFirstBlock(encoded, original.toByteArray(), """{"userId": 1,"role":"user"}""".toByteArray())

        assertNull(transformer.transformRead(forged))
    }

    @Test
    fun `rejects cookie with IV of unexpected length`() {
        val transformer = SessionTransportTransformerEncrypt(encryptKey, signKey)
        val encoded = transformer.transformWrite("hello")
        val iv = encoded.substringBefore('/')
        val rest = encoded.substringAfter('/')

        assertNull(transformer.transformRead("${iv.dropLast(2)}/$rest"))
        assertNull(transformer.transformRead("${iv}00/$rest"))
    }

    @Test
    fun `rejects cookie with tampered ciphertext or mac`() {
        val transformer = SessionTransportTransformerEncrypt(encryptKey, signKey)
        val encoded = transformer.transformWrite("hello")
        val iv = encoded.substringBefore('/')
        val encrypted = encoded.substringAfter('/').substringBefore(':').hexToByteArray()
        val mac = encoded.substringAfter(':').hexToByteArray()

        encrypted[0] = (encrypted[0].toInt() xor 1).toByte()
        assertNull(transformer.transformRead("$iv/${encrypted.toHexString()}:${mac.toHexString()}"))

        encrypted[0] = (encrypted[0].toInt() xor 1).toByte()
        mac[0] = (mac[0].toInt() xor 1).toByte()
        assertNull(transformer.transformRead("$iv/${encrypted.toHexString()}:${mac.toHexString()}"))
    }

    @Test
    fun `legacy cookie with MAC over ciphertext only is rejected by default`() {
        val transformer = SessionTransportTransformerEncrypt(encryptKey, signKey)
        val legacy = legacyCookie("hello") { _, encrypted, _ -> encrypted }

        assertNull(transformer.transformRead(legacy))
    }

    @Test
    fun `legacy cookies are accepted with backwardCompatibleRead`() {
        val transformer = SessionTransportTransformerEncrypt(encryptKey, signKey, backwardCompatibleRead = true)

        val ciphertextOnly = legacyCookie("hello") { _, encrypted, _ -> encrypted }
        assertEquals("hello", transformer.transformRead(ciphertextOnly))

        val plaintextMac = legacyCookie("hello") { _, _, decrypted -> decrypted }
        assertEquals("hello", transformer.transformRead(plaintextMac))
    }

    private val encryptKey = ByteArray(16) { it.toByte() }
    private val signKey = ByteArray(32) { (it + 100).toByte() }

    private fun flipFirstBlock(encoded: String, original: ByteArray, target: ByteArray): String {
        val iv = encoded.substringBefore('/').hexToByteArray()
        val forgedIv =
            ByteArray(iv.size) { i -> (iv[i].toInt() xor original[i].toInt() xor target[i].toInt()).toByte() }
        return "${forgedIv.toHexString()}/${encoded.substringAfter('/')}"
    }

    private fun legacyCookie(
        value: String,
        macInput: (iv: ByteArray, encrypted: ByteArray, decrypted: ByteArray) -> ByteArray
    ): String {
        val iv = ByteArray(16) { (it * 3).toByte() }
        val decrypted = value.toByteArray()
        val encrypted = Cipher.getInstance("AES/CBC/PKCS5PADDING").run {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(encryptKey, "AES"), IvParameterSpec(iv))
            doFinal(decrypted)
        }
        val mac = Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(signKey, "HmacSHA256"))
            doFinal(macInput(iv, encrypted, decrypted))
        }
        return "${iv.toHexString()}/${encrypted.toHexString()}:${mac.toHexString()}"
    }
}
