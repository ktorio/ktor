/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.oidc

import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class OidcStateCodecTest {

    @Test
    fun `round trips with fixed key`() {
        val codec = codec()
        val transaction = OidcAuthorizationTransaction(nonce = "nonce-1", codeVerifier = "verifier-1")

        val encoded = codec.encode("state-1", transaction)
        val decoded = codec.decode(encoded, "state-1")

        assertEquals("nonce-1", decoded?.nonce)
        assertEquals("verifier-1", decoded?.codeVerifier)
    }

    @Test
    fun `tampered iv ciphertext and tag are treated as absent`() {
        val codec = codec()
        val encoded = codec.encode(
            state = "state-1",
            transaction = OidcAuthorizationTransaction(nonce = "nonce-1", codeVerifier = "verifier-1"),
        )

        listOf(
            tamperEncoded(encoded, index = 0),
            tamperEncoded(encoded, index = 12),
            tamperEncoded(encoded, index = -1),
        ).forEach { tampered ->
            assertNull(codec.decode(tampered, "state-1"))
        }
    }

    @Test
    fun `wrong state is treated as absent`() {
        val codec = codec()
        val encoded = codec.encode(
            state = "state-1",
            transaction = OidcAuthorizationTransaction(nonce = "nonce-1", codeVerifier = "verifier-1"),
        )

        assertNull(codec.decode(encoded, "state-2"))
    }

    @Test
    fun `expired payload is treated as absent`() {
        val clock = TestClock(1_000L)
        val codec = codec(clock = clock)
        val encoded = codec.encode(
            state = "state-1",
            transaction = OidcAuthorizationTransaction(nonce = "nonce-1", codeVerifier = "verifier-1"),
        )

        clock.epochMs += AuthorizationTransactionTtl.inWholeMilliseconds + 1

        assertNull(codec.decode(encoded, "state-1"))
    }

    @Test
    fun `rotating key accepts cookie encrypted with previous key`() {
        val previous = fixedKey(2)
        val current = fixedKey(3)
        val writer = codec(key = OidcStateEncryptionKey.of(previous))
        val reader = codec(key = OidcStateEncryptionKey.rotating(current, previous))
        val transaction = OidcAuthorizationTransaction(nonce = "nonce-1", codeVerifier = "verifier-1")

        val encoded = writer.encode("state-1", transaction)
        val decoded = reader.decode(encoded, "state-1")

        assertEquals("nonce-1", decoded?.nonce)
        assertEquals("verifier-1", decoded?.codeVerifier)
    }

    @Test
    fun `encryption uses fresh iv`() {
        val codec = codec()
        val transaction = OidcAuthorizationTransaction(nonce = "nonce-1", codeVerifier = "verifier-1")

        val first = codec.encode("state-1", transaction)
        val second = codec.encode("state-1", transaction)

        assertNotEquals(first, second)
    }

    private fun codec(
        key: OidcStateEncryptionKey = OidcStateEncryptionKey.of(fixedKey(1)),
        clock: Clock = TestClock(1_000L),
    ) = OidcStateCodec(
        encryptionKey = key,
        clock = clock,
    )

    private fun tamperEncoded(value: String, index: Int): String {
        val bytes = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).decode(value)
        val tampered = bytes.copyOf()
        val resolvedIndex = if (index < 0) tampered.lastIndex else index
        tampered[resolvedIndex] = (tampered[resolvedIndex].toInt() xor 1).toByte()
        return Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(tampered)
    }

    private fun fixedKey(seed: Int): ByteArray =
        ByteArray(OidcStateEncryptionKey.KEY_SIZE) { index -> (seed + index).toByte() }

    private class TestClock(var epochMs: Long) : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(epochMs)
    }
}
