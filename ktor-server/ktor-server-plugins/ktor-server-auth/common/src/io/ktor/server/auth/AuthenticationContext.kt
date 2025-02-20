/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth

import io.ktor.server.application.*
import io.ktor.util.*
import kotlin.reflect.*

/**
 * An authentication context for a call.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.AuthenticationContext)
 *
 * @param call instance of [ApplicationCall] this context is for.
 */
public class AuthenticationContext(call: ApplicationCall) {

    public var call: ApplicationCall = call
        private set

    private val errors = HashMap<Any, AuthenticationFailedCause>()

    @Suppress("PropertyName")
    internal val _principal: CombinedPrincipal = CombinedPrincipal()

    /**
     * Retrieves an authenticated principal, or returns `null` if a user isn't authenticated.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.AuthenticationContext.principal)
     */
    @Deprecated("Use accessor methods instead", level = DeprecationLevel.ERROR)
    public var principal: Any?
        get() = _principal.principals.firstOrNull()?.second
        set(value) {
            check(value != null)
            _principal.add(null, value)
        }

    /**
     * All registered errors during auth procedure (only [AuthenticationFailedCause.Error]).
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.AuthenticationContext.allErrors)
     */
    public val allErrors: List<AuthenticationFailedCause.Error>
        get() = errors.values.filterIsInstance<AuthenticationFailedCause.Error>()

    /**
     * All authentication failures during auth procedure including missing or invalid credentials.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.AuthenticationContext.allFailures)
     */
    public val allFailures: List<AuthenticationFailedCause>
        get() = errors.values.toList()

    /**
     * Appends an error to the errors list. Overwrites if already registered for the same [key].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.AuthenticationContext.error)
     */
    public fun error(key: Any, cause: AuthenticationFailedCause) {
        errors[key] = cause
    }

    /**
     * Gets an [AuthenticationProcedureChallenge] for this context.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.AuthenticationContext.challenge)
     */
    public val challenge: AuthenticationProcedureChallenge = AuthenticationProcedureChallenge()

    /**
     * Sets an authenticated principal for this context.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.AuthenticationContext.principal)
     */
    public fun principal(principal: Any) {
        _principal.add(null, principal)
    }

    /**
     * Sets an authenticated principal for this context from provider with name [provider].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.AuthenticationContext.principal)
     */
    public fun principal(provider: String? = null, principal: Any) {
        _principal.add(provider, principal)
    }

    /**
     * Retrieves a principal of the type [T] from provider with name [provider], if any.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.AuthenticationContext.principal)
     */
    public inline fun <reified T : Any> principal(provider: String? = null): T? {
        return principal(provider, T::class)
    }

    /**
     * Retrieves a principal of the type [T], if any.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.AuthenticationContext.principal)
     */
    public fun <T : Any> principal(provider: String?, klass: KClass<T>): T? {
        return _principal.get(provider, klass)
    }

    /**
     * Requests a challenge to be sent to the client if none of mechanisms can authenticate a user.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.AuthenticationContext.challenge)
     */
    public fun challenge(
        key: Any,
        cause: AuthenticationFailedCause,
        function: ChallengeFunction
    ) {
        error(key, cause)
        challenge.register.add(cause to function)
    }

    public companion object {
        private val AttributeKey = AttributeKey<AuthenticationContext>("AuthContext")

        internal fun from(call: ApplicationCall): AuthenticationContext {
            val existingContext = call.attributes.getOrNull(AttributeKey)
            if (existingContext != null) {
                existingContext.call = call
                return existingContext
            }
            val context = AuthenticationContext(call)
            call.attributes.put(AttributeKey, context)
            return context
        }
    }
}
