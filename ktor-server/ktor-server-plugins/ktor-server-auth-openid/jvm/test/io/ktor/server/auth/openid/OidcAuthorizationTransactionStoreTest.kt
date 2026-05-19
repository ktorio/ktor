/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalTime::class)

package io.ktor.server.auth.openid

import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.TestTimeSource

class OidcAuthorizationTransactionStoreTest {

    @Test
    fun `replaces previous transaction for same authorization session`() {
        val store = OidcAuthorizationTransactionStore()

        store.put("authorization-session", "state-1", transaction("nonce-1"))
        store.put("authorization-session", "state-2", transaction("nonce-2"))

        assertNull(store["authorization-session", "state-1"])
        assertEquals("nonce-2", store["authorization-session", "state-2"]?.nonce)
    }

    @Test
    fun `stale remove does not delete current transaction`() {
        val store = OidcAuthorizationTransactionStore()

        store.put("authorization-session", "state-1", transaction("nonce-1"))
        store.put("authorization-session", "state-2", transaction("nonce-2"))

        assertNull(store.remove("authorization-session", "state-1"))
        assertEquals("nonce-2", store["authorization-session", "state-2"]?.nonce)
    }

    @Test
    fun `expires transactions by ttl`() {
        val timeSource = TestTimeSource()
        val store = OidcAuthorizationTransactionStore(
            ttl = 1.seconds,
            timeSource = timeSource,
        )

        store.put("authorization-session", "state", transaction("nonce"))
        timeSource += 2.seconds

        assertNull(store["authorization-session", "state"])
        assertNull(store.remove("authorization-session", "state"))
    }

    @Test
    fun `prunes expired transactions on interval without breaking live entries`() {
        val timeSource = TestTimeSource()
        val store = OidcAuthorizationTransactionStore(
            ttl = 1.seconds,
            timeSource = timeSource,
            pruneInterval = 1,
        )

        store.put("authorization-session-1", "expired-1", transaction("nonce-1"))
        store.put("authorization-session-2", "expired-2", transaction("nonce-2"))
        store.put("authorization-session-3", "expired-3", transaction("nonce-3"))
        timeSource += 2.seconds

        store.put("authorization-session-4", "live", transaction("nonce-4"))

        assertNull(store["authorization-session-1", "expired-1"])
        assertNull(store["authorization-session-2", "expired-2"])
        assertNull(store["authorization-session-3", "expired-3"])
        assertEquals("nonce-4", store["authorization-session-4", "live"]?.nonce)
    }

    @Test
    fun `expires transaction before same authorization session is reused`() {
        val timeSource = TestTimeSource()
        val store = OidcAuthorizationTransactionStore(
            ttl = 1.seconds,
            timeSource = timeSource,
        )

        store.put("authorization-session", "expired", transaction("nonce-expired"))
        timeSource += 2.seconds
        store.put("authorization-session", "new", transaction("nonce-new"))

        assertNull(store["authorization-session", "expired"])
        assertEquals("nonce-new", store["authorization-session", "new"]?.nonce)
    }

    @Test
    fun `binds transactions to authorization session and state`() {
        val store = OidcAuthorizationTransactionStore()

        store.put("authorization-session-1", "state", transaction("nonce-1"))
        store.put("authorization-session-2", "state", transaction("nonce-2"))

        assertEquals("nonce-1", store["authorization-session-1", "state"]?.nonce)
        assertEquals("nonce-2", store["authorization-session-2", "state"]?.nonce)
        assertNull(store["missing-authorization-session", "state"])
    }

    @Test
    fun `new transaction does not replace transaction from different authorization session`() {
        val store = OidcAuthorizationTransactionStore()

        store.put("authorization-session-1", "state-1", transaction("nonce-1"))
        store.put("authorization-session-2", "state-2", transaction("nonce-2"))
        store.put("authorization-session-1", "state-3", transaction("nonce-3"))

        assertNull(store["authorization-session-1", "state-1"])
        assertEquals("nonce-3", store["authorization-session-1", "state-3"]?.nonce)
        assertEquals("nonce-2", store["authorization-session-2", "state-2"]?.nonce)
    }

    @Test
    fun `supports concurrent put and remove`() {
        val store = OidcAuthorizationTransactionStore()
        val failures = ConcurrentLinkedQueue<Throwable>()
        val threads = List(8) { worker ->
            thread {
                runCatching {
                    repeat(500) { index ->
                        val authorizationSessionId = "$worker-$index"
                        val state = "$worker-$index"
                        val nonce = "nonce-$state"
                        store.put(authorizationSessionId, state, transaction(nonce))
                        val removed = store.remove(authorizationSessionId, state)
                        assertTrue(removed == null || removed.nonce == nonce)
                    }
                }.onFailure(failures::add)
            }
        }

        threads.forEach { it.join() }

        assertTrue(failures.isEmpty(), failures.joinToString(separator = "\n") { it.stackTraceToString() })
    }

    private fun transaction(nonce: String): OidcAuthorizationTransaction =
        OidcAuthorizationTransaction(nonce = nonce)
}
