/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth

import io.ktor.server.application.*
import io.ktor.util.*
import kotlin.properties.*

/**
 * Represents an authentication context for the call
 * @param call instance of [ApplicationCall] this context is for
 */
public class AuthenticationContext(public val call: ApplicationCall) {
    private val _errors = HashMap<Any, AuthenticationFailedCause>()

    /**
     * Retrieves authenticated principal, or returns null if no user was authenticated
     */
    public var principal: Principal? by Delegates.vetoable(null) { _, old, _ ->
        require(old == null) { "Principal can be only assigned once" }
        true
    }

    /**
     * Stores authentication failures for keys provided by authentication mechanisms
     */
    @Suppress("unused")
    @Deprecated("Use allErrors, allFailures or error() function instead", level = DeprecationLevel.ERROR)
    public val errors: HashMap<Any, AuthenticationFailedCause>
        get() = _errors

    /**
     * All registered errors during auth procedure (only [AuthenticationFailedCause.Error]).
     */
    public val allErrors: List<AuthenticationFailedCause.Error>
        get() = _errors.values.filterIsInstance<AuthenticationFailedCause.Error>()

    /**
     * All authentication failures during auth procedure including missing or invalid credentials
     */
    public val allFailures: List<AuthenticationFailedCause>
        get() = _errors.values.toList()

    /**
     * Appends an error to the errors list. Overwrites if already registered for the same [key].
     */
    public fun error(key: Any, cause: AuthenticationFailedCause) {
        _errors[key] = cause
    }

    /**
     * Gets an [AuthenticationProcedureChallenge] for this context
     */
    public val challenge: AuthenticationProcedureChallenge = AuthenticationProcedureChallenge()

    /**
     * Sets an authenticated principal for this context.
     *
     * This method may be called only once per context
     */
    public fun principal(principal: Principal) {
        this.principal = principal
    }

    /**
     * Retrieves a principal of type [T], if any
     */
    public inline fun <reified T : Principal> principal(): T? = principal as? T

    /**
     * Requests a challenge to be sent to the client if none of mechanisms can authenticate a user
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

        internal fun from(call: ApplicationCall) =
            call.attributes.computeIfAbsent(AttributeKey) { AuthenticationContext(call) }
    }
}
