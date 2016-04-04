package org.jetbrains.ktor.auth

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.interception.*
import org.jetbrains.ktor.pipeline.*

class AuthenticationProcedure() {
    val pipeline = Pipeline<AuthenticationProcedureContext>()

    fun authenticate(authenticator: PipelineContext<AuthenticationProcedureContext>.(AuthenticationProcedureContext) -> Unit) {
        pipeline.intercept(authenticator)
    }
}

fun InterceptApplicationCall.authentication(procedure: AuthenticationProcedure.() -> Unit) {
    val authenticationProcedure = AuthenticationProcedure().apply(procedure)
    intercept {
        val context = AuthenticationProcedureContext(call)
        call.fork(authenticationProcedure.pipeline, context,
                attach = { p, s -> },
                detach = { p, s ->
                    val principal = s.subject.principal
                    if (principal == null) {
                        val challenges = s.subject.challenges
                        if (challenges.isNotEmpty()) {
                            val pipeline = Pipeline<AuthenticationProcedureChallenge>(challenges)
                            call.fork(pipeline, AuthenticationProcedureChallenge(), { p, s -> }, { p, s ->
                                p.finish()
                            })
                        }
                    } else {
                        s.subject.call.authentication.addPrincipal(principal)
                    }
                    p.proceed()
                })
    }
}

class AuthenticationProcedureChallenge {
    override fun toString(): String = "AuthenticationProcedureChallenge"
}

class AuthenticationProcedureContext(val call: ApplicationCall) {
    var principal: Principal? = null
    val challenges = mutableListOf<PipelineContext<AuthenticationProcedureChallenge>.(AuthenticationProcedureChallenge) -> Unit>()

    fun principal(principal: Principal) {
        require(this.principal == null)
        this.principal = principal
    }

    fun challenge(function: PipelineContext<AuthenticationProcedureChallenge>.(AuthenticationProcedureChallenge) -> Unit) {
        challenges.add(function)
    }

    override fun toString(): String {
        return "AuthenticationProcedureContext(call=$call)"
    }
}