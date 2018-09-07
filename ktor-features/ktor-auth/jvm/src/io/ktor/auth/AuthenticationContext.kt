package io.ktor.auth

import io.ktor.application.*
import io.ktor.util.pipeline.*
import io.ktor.util.*
import java.util.*
import kotlin.properties.*

/**
 * Represents an authentication context for the call
 * @param call instance of [ApplicationCall] this context is for
 */
class AuthenticationContext(val call: ApplicationCall) {
    /**
     * Retrieves authenticated principal, or returns null if no user was authenticated
     */
    var principal by Delegates.vetoable<Principal?>(null) { _, old, _ ->
        require(old == null) { "Principal can be only assigned once" };
        true
    }

    /**
     * Stores authentication failures for keys provided by authentication mechanisms
     */
    val errors = HashMap<Any, AuthenticationFailedCause>()

    /**
     * Appends an error to the [errors]
     */
    fun error(key: Any, cause: AuthenticationFailedCause) {
        errors[key] = cause
    }

    /**
     * Gets an [AuthenticationProcedureChallenge] for this context
     */
    val challenge = AuthenticationProcedureChallenge()

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
