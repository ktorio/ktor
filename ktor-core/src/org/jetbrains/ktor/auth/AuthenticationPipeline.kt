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
    val authenticationProcedure = AuthenticationProcedure().apply(procedure).apply {
        pipeline.intercept { procedure ->
            val principal = procedure.principal
            if (principal == null) {
                val challenges = procedure.challenges
                if (challenges.isNotEmpty()) {
                    val challengePipeline = Pipeline(challenges)
                    challengePipeline.intercept { finishAll() }
                    procedure.call.fork(AuthenticationProcedureChallenge(), challengePipeline)
                }
            } else {
                procedure.call.authentication.addPrincipal(principal)
            }
        }
    }
    intercept {
        val context = AuthenticationProcedureContext(call)
        call.fork(context, authenticationProcedure.pipeline)
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