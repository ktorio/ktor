package io.ktor.auth

import io.ktor.application.*
import io.ktor.util.*
import io.ktor.util.pipeline.*

/**
 * Represents authentication challenging procedure requested by authentication mechanism
 */
class AuthenticationProcedureChallenge {
    internal val register = mutableListOf<
        Pair<AuthenticationFailedCause,
            PipelineInterceptor<AuthenticationProcedureChallenge, ApplicationCall>>>()

    /**
     * List of currently installed challenges except errors
     */
    val challenges: List<PipelineInterceptor<AuthenticationProcedureChallenge, ApplicationCall>>
        get() = register.filter { it.first !is AuthenticationFailedCause.Error }.sortedBy {
            when (it.first) {
                AuthenticationFailedCause.InvalidCredentials -> 1
                AuthenticationFailedCause.NoCredentials -> 2
                else -> throw NoWhenBranchMatchedException("${it.first}")
            }
        }.map { it.second }

    /**
     * List of currently installed challenges for errors
     */
    @KtorExperimentalAPI
    val errorChallenges: List<PipelineInterceptor<AuthenticationProcedureChallenge, ApplicationCall>>
        get() = register.filter { it.first is AuthenticationFailedCause.Error }.map { it.second }

    /**
     * Represents if a challenge was successfully sent to the client and challenging should be stopped
     */
    @Volatile
    var completed = false
        private set

    /**
     * Completes a challenging procedure
     */
    fun complete() {
        completed = true
    }

    override fun toString(): String = "AuthenticationProcedureChallenge"
}
