package io.ktor.auth

import io.ktor.application.*
import io.ktor.pipeline.*
import io.ktor.util.*

/**
 * Authentication feature supports pluggable mechanisms for checking and challenging a client to provide credentials
 *
 * @param pipeline keeps pipeline for this instance of a [Authentication]
 */
class Authentication(val pipeline: AuthenticationPipeline) {
    private val challengePhase = PipelinePhase("Challenge")

    init {
        // Install challenging interceptor, it installs last
        pipeline.intercept(AuthenticationPipeline.RequestAuthentication) { context ->
            val principal = context.principal
            if (principal != null) return@intercept

            val challenges = context.challenge.challenges
            if (challenges.isEmpty()) return@intercept

            val challengePipeline = Pipeline<AuthenticationProcedureChallenge, ApplicationCall>(challengePhase)
            for (challenge in challenges) {
                challengePipeline.intercept(challengePhase) {
                    challenge(it)
                    if (it.completed)
                        finish() // finish challenge pipeline if it has been completed
                }
            }

            val challenge = challengePipeline.execute(call, context.challenge)
            if (challenge.completed)
                finish() // finish authentication pipeline if challenge has been completed
        }
    }

    /**
     * Installable feature for [Authentication].
     */
    companion object Feature : ApplicationFeature<ApplicationCallPipeline, AuthenticationPipeline, Authentication> {
        private val authenticationPhase = PipelinePhase("Authenticate")

        override val key = AttributeKey<Authentication>("Authentication")

        override fun install(pipeline: ApplicationCallPipeline, configure: AuthenticationPipeline.() -> Unit): Authentication {
            val authenticationPipeline = AuthenticationPipeline().apply(configure)
            val feature = Authentication(authenticationPipeline)
            pipeline.insertPhaseAfter(ApplicationCallPipeline.Infrastructure, authenticationPhase)
            pipeline.intercept(authenticationPhase) {
                // don't run authentication if any filters signals it is not needed
                if (authenticationPipeline.skipWhen.any { skip -> skip(call) })
                    return@intercept

                val authenticationContext = AuthenticationContext.from(call)
                feature.pipeline.execute(call, authenticationContext)
                if (authenticationContext.challenge.completed)
                    finish() // finish call pipeline if authentication challenge has been completed
            }
            return feature
        }
    }
}

/**
 * Installs authentication into `this` pipeline
 */
fun ApplicationCallPipeline.authentication(configuration: AuthenticationPipeline.() -> Unit): Authentication {
    return install(Authentication, configuration)
}

/**
 * Retrieves an [AuthenticationContext] for `this` call
 */
val ApplicationCall.authentication: AuthenticationContext
    get() = AuthenticationContext.from(this)

/**
 * Retrieves authenticated [Principal] for `this` call
 */
inline fun <reified P : Principal> ApplicationCall.principal() = authentication.principal<P>()
