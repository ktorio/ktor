package org.jetbrains.ktor.auth

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.interception.*
import org.jetbrains.ktor.pipeline.*

class AuthenticationProcedure() {
    val pipeline = Pipeline<AuthenticationProcedureContext>()

    init {
        pipeline.intercept {
            onFinish {
                val principal = subject.principal
                if (principal == null) {
                    for (challenge in subject.challenges) {
                        challenge()
                    }
                } else {
                    subject.call.authentication.addPrincipal(principal)
                }
            }
        }
    }



    fun authenticate(authenticator: PipelineContext<AuthenticationProcedureContext>.(AuthenticationProcedureContext) -> Unit) {
        pipeline.intercept(authenticator)
    }
}

fun InterceptApplicationCall.authentication(procedure: AuthenticationProcedure.() -> Unit) {
    val authenticationProcedure = AuthenticationProcedure().apply(procedure)
    intercept {
        fork(AuthenticationProcedureContext(call), authenticationProcedure.pipeline) { master ->
            if (subject.rejected)
                master.finish()
            else
                master.proceed()
        }
    }
}

class AuthenticationProcedureContext(val call: ApplicationCall) {
    var principal: Principal? = null
    val challenges = mutableListOf<() -> Unit>()

    fun principal(principal: Principal) {
        require(this.principal == null)
        this.principal = principal
    }

    fun challenge(function: () -> Unit) {
        challenges.add(function)
    }

    val rejected: Boolean
        get() = principal == null && challenges.any()
}