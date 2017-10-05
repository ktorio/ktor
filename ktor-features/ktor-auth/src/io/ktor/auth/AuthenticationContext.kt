package io.ktor.auth

import io.ktor.application.*
import io.ktor.pipeline.*
import io.ktor.util.*
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
                          function: PipelineInterceptor<AuthenticationProcedureChallenge, ApplicationCall>) {
        error(key, cause)
        challenge.register.add(cause to function)
    }

    companion object {
        val AttributeKey = AttributeKey<AuthenticationContext>("AuthContext")

        internal fun from(call: ApplicationCall) = call.attributes.computeIfAbsent(AttributeKey) { AuthenticationContext() }
    }
}