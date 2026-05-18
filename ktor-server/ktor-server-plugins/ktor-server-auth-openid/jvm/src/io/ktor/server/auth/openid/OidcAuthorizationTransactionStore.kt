/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.openid

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.util.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeMark
import kotlin.time.TimeSource

private val AuthorizationTransactionTtl: Duration = 10.minutes

internal class OidcAuthorizationTransactionStore(
    private val ttl: Duration = AuthorizationTransactionTtl,
    private val timeSource: TimeSource = TimeSource.Monotonic,
    private val pruneInterval: Int = 64,
) {
    private var putsSincePrune = 0

    init {
        require(pruneInterval > 0) { "pruneInterval must be positive" }
    }

    private val lock = Any()
    private val entries = HashMap<String, Entry>()

    fun put(
        authorizationSessionId: String,
        state: String,
        transaction: OidcAuthorizationTransaction,
    ) {
        synchronized(lock) {
            putsSincePrune++
            if (putsSincePrune >= pruneInterval) {
                pruneExpired()
            }
            entries[authorizationSessionId] = Entry(state, transaction, timeSource.markNow())
        }
    }

    fun remove(authorizationSessionId: String, state: String): OidcAuthorizationTransaction? {
        synchronized(lock) {
            val entry = entries[authorizationSessionId]?.takeIf { it.state == state } ?: return null
            entries.remove(authorizationSessionId)
            return entry.takeUnless { it.isExpired(ttl) }?.transaction
        }
    }

    operator fun get(
        authorizationSessionId: String,
        state: String,
    ): OidcAuthorizationTransaction? {
        synchronized(lock) {
            val entry = entries[authorizationSessionId]?.takeIf { it.state == state } ?: return null
            if (entry.isExpired(ttl)) {
                entries.remove(authorizationSessionId)
                return null
            }
            return entry.transaction
        }
    }

    private fun pruneExpired() {
        putsSincePrune = 0
        val iterator = entries.entries.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().value.isExpired(ttl)) {
                iterator.remove()
            }
        }
    }

    private class Entry(
        val state: String,
        val transaction: OidcAuthorizationTransaction,
        private val createdAt: TimeMark,
    ) {
        fun isExpired(ttl: Duration): Boolean = createdAt.elapsedNow() > ttl
    }
}

internal data class OidcAuthorizationTransaction(
    val nonce: String,
)

internal fun oidcAuthorizationSessionCookieName(providerName: String): String =
    "KTOR_OIDC_${providerName.uppercase()}_AUTHORIZATION_SESSION"

private fun OidcProvider<*>.authorizationSessionCookieName(): String =
    oidcAuthorizationSessionCookieName(name)

private fun OidcProvider<*>.authorizationSessionAttributeKey(): AttributeKey<String> =
    AttributeKey("OidcAuthorizationTransaction:$name:AuthorizationSessionId")

internal suspend fun OidcProvider<*>.createAuthorizationTransaction(
    call: ApplicationCall,
    state: String,
): OidcAuthorizationTransaction {
    val authorizationSessionId = call.request.cookies[authorizationSessionCookieName()] ?: generateNonceSuspend()
    call.attributes.put(authorizationSessionAttributeKey(), authorizationSessionId)
    val transaction = OidcAuthorizationTransaction(
        nonce = generateNonceSuspend(),
    )
    authorizationTransactionStore.put(authorizationSessionId, state, transaction)
    call.response.cookies.append(
        Cookie(
            name = authorizationSessionCookieName(),
            value = authorizationSessionId,
            maxAge = AuthorizationTransactionTtl.inWholeSeconds.toInt(),
            path = "/",
            secure = !call.application.developmentMode,
            httpOnly = true,
            extensions = mapOf("SameSite" to "lax"),
        )
    )
    return transaction
}

internal fun OidcProvider<*>.findAuthorizationTransaction(
    call: ApplicationCall,
    state: String,
): OidcAuthorizationTransaction? {
    val authorizationSessionId = call.authorizationSessionId(this) ?: return null
    return authorizationTransactionStore[authorizationSessionId, state]
}

internal fun OidcProvider<*>.consumeAuthorizationTransaction(
    call: ApplicationCall,
    state: String,
): OidcAuthorizationTransaction? {
    val authorizationSessionId = call.authorizationSessionId(this) ?: return null
    return authorizationTransactionStore.remove(authorizationSessionId, state)
}

private fun ApplicationCall.authorizationSessionId(provider: OidcProvider<*>): String? {
    return attributes.getOrNull(provider.authorizationSessionAttributeKey())
        ?: request.cookies[provider.authorizationSessionCookieName()]
}
