/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.auth

import io.ktor.application.*
import io.ktor.util.pipeline.*
import io.ktor.util.*
import kotlin.collections.HashMap
import kotlin.properties.*

/**
 * Represents an authentication context for the call
 * @param call instance of [ApplicationCall] this context is for
 */
class AuthenticationContext(val call: ApplicationCall) {
    private val _errors = HashMap<Any, AuthenticationFailedCause>()

    /**
     * Retrieves authenticated principal, or returns null if no user was authenticated
     */
    var principal: Principal? by Delegates.vetoable<Principal?>(null) { _, old, _ ->
        require(old == null) { "Principal can be only assigned once" }
        true
    }

    /**
     * Stores authentication failures for keys provided by authentication mechanisms
     */
    @Suppress("unused")
    @Deprecated("Use allErrors, allFailures or error() function instead")
    val errors: HashMap<Any, AuthenticationFailedCause> get() = _errors

    /**
     * All registered errors during auth procedure (only [AuthenticationFailedCause.Error]).
     */
    val allErrors: List<AuthenticationFailedCause.Error>
        get() = _errors.values.filterIsInstance<AuthenticationFailedCause.Error>()

    /**
     * All authentication failures during auth procedure including missing or invalid credentials
     */
    val allFailures: List<AuthenticationFailedCause>
        get() = _errors.values.toList()

    /**
     * Appends an error to the errors list. Overwrites if already registered for the same [key].
     */
    fun error(key: Any, cause: AuthenticationFailedCause) {
        _errors[key] = cause
    }

    /**
     * Gets an [AuthenticationProcedureChallenge] for this context
     */
    val challenge: AuthenticationProcedureChallenge = AuthenticationProcedureChallenge()

    /**
     * Sets an authenticated principal for this context.
     *
     * This method may be called only once per context
     */
    fun principal(principal: Principal) {
        this.principal = principal
    }

    /**
     * Retrieves a principal of type [T], if any
     */
    inline fun <reified T : Principal> principal(): T? = principal as? T

    /**
     * Requests a challenge to be sent to the client if none of mechanisms can authenticate a user
     */
    fun challenge(
        key: Any,
        cause: AuthenticationFailedCause,
        function: PipelineInterceptor<AuthenticationProcedureChallenge, ApplicationCall>
    ) {
        error(key, cause)
        challenge.register.add(cause to function)
    }

    companion object {
        private val AttributeKey = AttributeKey<AuthenticationContext>("AuthContext")

        internal fun from(call: ApplicationCall) =
            call.attributes.computeIfAbsent(AttributeKey) { AuthenticationContext(call) }
    }
}
