/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalKtorApi::class, InternalAPI::class)

package io.ktor.tests.auth

import io.ktor.server.auth.*
import io.ktor.utils.io.*
import kotlinx.serialization.Serializable
import kotlin.io.encoding.Base64
import kotlin.test.*

class AuthenticationStateCodecTest {

    @Serializable
    private data class State(val id: String, val count: Int = 0)

    @Test
    fun `round trips the state`() {
        val codec = codec()

        assertEquals(State("request-1", 2), codec.decode(codec.encode(State("request-1", 2))))
    }

    @Test
    fun `encoded state does not expose the payload`() {
        val encoded = codec().encode(State("request-1"))

        assertFalse("request-1" in base64.decode(encoded).decodeToString())
    }

    @Test
    fun `encryption uses a fresh initialization vector`() {
        val codec = codec()

        assertNotEquals(codec.encode(State("request-1")), codec.encode(State("request-1")))
    }

    @Test
    fun `modified and malformed values are treated as absent`() {
        val codec = codec()
        val encoded = codec.encode(State("request-1"))

        listOf(tamper(encoded, index = 0), tamper(encoded, index = 12), tamper(encoded, index = -1)).forEach {
            assertNull(codec.decode(it))
        }
        assertNull(codec.decode("not base64 !"))
        assertNull(codec.decode(""))
        assertNull(codec.decode(base64.encode(ByteArray(12))))
    }

    @Test
    fun `state encrypted with another key is treated as absent`() {
        val encoded = codec(AuthenticationStateEncryptionKey.of(fixedKey(1))).encode(State("request-1"))

        assertNull(codec(AuthenticationStateEncryptionKey.of(fixedKey(2))).decode(encoded))
    }

    @Test
    fun `rotating key accepts the previous key and encrypts with the current one`() {
        val previous = codec(AuthenticationStateEncryptionKey.of(fixedKey(1)))
        val current = codec(AuthenticationStateEncryptionKey.of(fixedKey(2)))
        val rotating = codec(AuthenticationStateEncryptionKey.rotating(current = fixedKey(2), previous = fixedKey(1)))

        assertEquals(State("request-1"), rotating.decode(previous.encode(State("request-1"))))
        assertEquals(State("request-2"), current.decode(rotating.encode(State("request-2"))))
        assertNull(previous.decode(rotating.encode(State("request-3"))))
    }

    @Test
    fun `key requires 32 bytes`() {
        assertFailsWith<IllegalArgumentException> { AuthenticationStateEncryptionKey.of(ByteArray(16)) }
        assertFailsWith<IllegalArgumentException> {
            AuthenticationStateEncryptionKey.rotating(current = fixedKey(1), previous = ByteArray(31))
        }
    }

    private fun codec(
        key: AuthenticationStateEncryptionKey = AuthenticationStateEncryptionKey.of(fixedKey(1)),
    ) = AuthenticationStateCodec(key, State.serializer())

    private fun tamper(value: String, index: Int): String {
        val bytes = base64.decode(value)
        val resolvedIndex = if (index < 0) bytes.lastIndex else index
        bytes[resolvedIndex] = (bytes[resolvedIndex].toInt() xor 1).toByte()
        return base64.encode(bytes)
    }

    private fun fixedKey(seed: Int): ByteArray =
        ByteArray(AuthenticationStateEncryptionKey.KEY_SIZE) { index -> (seed + index).toByte() }

    private val base64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
}
