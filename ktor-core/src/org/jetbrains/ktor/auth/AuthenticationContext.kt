package org.jetbrains.ktor.auth

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.util.*
import java.util.*
import kotlin.reflect.*

class AuthenticationContext internal constructor() {
    private val collectedCredentials = ArrayList<Credential>()
    private val collectedPrincipals = ArrayList<Principal>()
    private val collectedFailures = HashMap<Credential, MutableList<Throwable>>()

    val credentials: List<Credential>
        get() = synchronized(this) { collectedCredentials.toList() }

    val principals: List<Principal>
        get() = synchronized(this) { collectedPrincipals.toList() }

    val failures: Map<Credential, List<Throwable>>
        get() = synchronized(this) { collectedFailures.mapValues { it.value.toList() } }

    inline fun <reified K : Credential> credentials(): List<K> = credentials(K::class)

    @Suppress("UNCHECKED_CAST")
    fun <K : Credential> credentials(type: KClass<K>): List<K> = synchronized(this) {
        collectedCredentials.filter { type.java.isInstance(it) } as List<K>
    }

    inline fun <reified P : Principal> principals(): List<P> = principals(P::class)
    inline fun <reified P : Principal> principal(): P? = principals<P>().singleOrNull()

    @Suppress("UNCHECKED_CAST")
    fun <P : Principal> principals(type: KClass<P>): List<P> = synchronized(this) {
        collectedPrincipals.filter { type.java.isInstance(it) } as List<P>
    }

    fun addCredential(credential: Credential) {
        synchronized(this) {
            collectedCredentials.add(credential)
        }
    }

    fun addPrincipals(principals: List<Principal>) {
        if (principals.isNotEmpty()) {
            synchronized(this) {
                collectedPrincipals.addAll(principals)
            }
        }
    }

    fun removePrincipals(principals: List<Principal>) {
        if (principals.isNotEmpty()) {
            synchronized(this) {
                collectedPrincipals.removeAll(principals)
            }
        }
    }

    fun addPrincipal(principal: Principal) {
        synchronized(this) {
            collectedPrincipals.add(principal)
        }
    }

    fun addFailure(credential: Credential, t: Throwable) {
        synchronized(this) {
            collectedFailures.getOrPut(credential) { ArrayList() }.add(t)
        }
    }

    fun hasPrincipals(): Boolean = synchronized(this) { collectedPrincipals.isNotEmpty() }

    companion object {
        val AttributeKey = AttributeKey<AuthenticationContext>("AuthContext")
        internal fun from(call: ApplicationCall) = call.attributes.computeIfAbsent(AttributeKey) { AuthenticationContext() }
    }
}

val ApplicationCall.authentication: AuthenticationContext
    get() = AuthenticationContext.from(this)

val ApplicationCall.principals: List<Principal>
    get() = authentication.principals

inline fun <reified P : Principal> ApplicationCall.principals() = authentication.principals<P>()
