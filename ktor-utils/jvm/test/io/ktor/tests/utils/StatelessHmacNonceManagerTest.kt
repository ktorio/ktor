package io.ktor.tests.utils

import io.ktor.util.*
import kotlinx.coroutines.*
import kotlin.test.*

class StatelessHmacNonceManagerTest {
    private val nonceValues = listOf("11111111", "22222222", "33333333")
    private val nonceSequence = nonceValues.iterator()
    private val key = "test-key".toByteArray()
    private val manager = StatelessHmacNonceManager(key) { nonceSequence.next() }

    @Test
    fun smokeTest(): Unit = runBlocking {
        val nonce = manager.newNonce()
        assertTrue(manager.verifyNonce(nonce))
    }

    @Test
    fun testContains(): Unit = runBlocking {
        assertTrue(nonceValues[0] in manager.newNonce())
        assertTrue(nonceValues[1] in manager.newNonce())
        assertTrue(nonceValues[2] in manager.newNonce())
    }

    @Test
    fun testIllegalValues(): Unit = runBlocking {
        assertFalse(manager.verifyNonce(""))
        assertFalse(manager.verifyNonce("+"))
        assertFalse(manager.verifyNonce("++"))
        assertFalse(manager.verifyNonce("+++"))
        assertFalse(manager.verifyNonce("1"))
        assertFalse(manager.verifyNonce("1777777777777777777777777777"))
        assertFalse(manager.verifyNonce("1777777777+777777777777777777"))
        assertFalse(manager.verifyNonce("1777777777+77777777+7777777777"))
        assertFalse(manager.verifyNonce("1777777777+77777777+7777777777"))

        val managerWithTheSameKey = StatelessHmacNonceManager(key) { "some-other-nonce" }
        assertTrue(manager.verifyNonce(managerWithTheSameKey.newNonce()))

        val managerWithAnotherKey = StatelessHmacNonceManager("some-other-key".toByteArray()) { nonceValues[0] }
        assertFalse(manager.verifyNonce(managerWithAnotherKey.newNonce()))
    }
}
