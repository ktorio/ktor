package org.jetbrains.ktor.auth

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.util.*
import java.util.*
import kotlin.properties.*

class AuthenticationContext {
    var principal by Delegates.vetoable<Principal?>(null) { _, old, _ -> require(old == null); true }
    val errors = HashMap<Any, NotAuthenticatedCause>()
    val challenge = AuthenticationProcedureChallenge()

    fun principal(principal: Principal) {
        this.principal = principal
    }

    inline fun <reified T : Principal> principal(): T? = principal as? T

    fun error(key: Any, cause: NotAuthenticatedCause) {
        errors[key] = cause
    }

    suspend fun challenge(key: Any,
                          cause: NotAuthenticatedCause,
                          function: suspend PipelineContext<AuthenticationProcedureChallenge>.(AuthenticationProcedureChallenge) -> Unit) {
        error(key, cause)
        challenge.register.add(cause to function)
    }

    companion object {
        val AttributeKey = AttributeKey<AuthenticationContext>("AuthContext")

        internal fun from(call: ApplicationCall) = call.attributes.computeIfAbsent(AttributeKey) { AuthenticationContext() }
    }
}