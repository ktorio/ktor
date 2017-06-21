package org.jetbrains.ktor.auth

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.util.*

class Authentication(val pipeline: AuthenticationPipeline) {
    init {
        pipeline.intercept(AuthenticationPipeline.RequestAuthentication) { context ->
            val principal = context.principal
            if (principal == null) {
                val challenges = context.challenge.challenges
                if (challenges.isNotEmpty()) {
                    val challengePhase = PipelinePhase("Challenge")
                    val challengePipeline = Pipeline(challengePhase, challenges)
                    challengePipeline.intercept(challengePhase) { challenge ->
                        if (challenge.success)
                            finish()
                    }
                    val challenge = challengePipeline.execute(call, context.challenge)
                    if (challenge.success)
                        finish()
                }
            }
        }
    }

    companion object Feature : ApplicationFeature<ApplicationCallPipeline, AuthenticationPipeline, Authentication> {
        val authenticationPhase = PipelinePhase("Authenticate")

        override val key = AttributeKey<Authentication>("Authentication")

        override fun install(pipeline: ApplicationCallPipeline, configure: AuthenticationPipeline.() -> Unit): Authentication {
            val authenticationPipeline = AuthenticationPipeline().apply(configure)
            val feature = Authentication(authenticationPipeline)
            pipeline.phases.insertAfter(ApplicationCallPipeline.Infrastructure, authenticationPhase)
            pipeline.intercept(authenticationPhase) {
                val authenticationContext = AuthenticationContext.from(call)
                feature.pipeline.execute(call, authenticationContext)
                if (authenticationContext.challenge.success)
                    finish()
            }
            return feature
        }
    }

}

class AuthenticationPipeline() : Pipeline<AuthenticationContext>(CheckAuthentication, RequestAuthentication) {
    companion object {
        val CheckAuthentication = PipelinePhase("CheckAuthentication")
        val RequestAuthentication = PipelinePhase("RequestAuthentication")
    }
}


fun ApplicationCallPipeline.authentication(procedure: AuthenticationPipeline.() -> Unit): Authentication {
    return install(Authentication, procedure)
}

class AuthenticationProcedureChallenge {
    internal val register = mutableListOf<Pair<NotAuthenticatedCause, suspend PipelineContext<AuthenticationProcedureChallenge>.(AuthenticationProcedureChallenge) -> Unit>>()

    val challenges: List<suspend PipelineContext<AuthenticationProcedureChallenge>.(AuthenticationProcedureChallenge) -> Unit>
        get() = register.filter { it.first !is NotAuthenticatedCause.Error }.sortedBy {
            when (it.first) {
                NotAuthenticatedCause.InvalidCredentials -> 1
                NotAuthenticatedCause.NoCredentials -> 2
                else -> throw NoWhenBranchMatchedException("${it.first}")
            }
        }.map { it.second }

    @Volatile
    var success = false
        private set

    fun success() {
        success = true
    }

    override fun toString(): String = "AuthenticationProcedureChallenge"
}

sealed class NotAuthenticatedCause {
    object NoCredentials : NotAuthenticatedCause()
    object InvalidCredentials : NotAuthenticatedCause()
    open class Error(val cause: String) : NotAuthenticatedCause()
}


val ApplicationCall.authentication: AuthenticationContext
    get() = AuthenticationContext.from(this)


inline fun <reified P : Principal> ApplicationCall.principal() = authentication.principal<P>()
