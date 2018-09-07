package io.ktor.auth

import io.ktor.application.*
import io.ktor.util.pipeline.*

/**
 * Represents authentication [Pipeline] for checking and requesting authentication
 */
class AuthenticationPipeline() : Pipeline<AuthenticationContext, ApplicationCall>(CheckAuthentication, RequestAuthentication) {

    companion object {
        /**
         * Phase for checking if user is already authenticated before all mechanisms kicks in
         */
        val CheckAuthentication = PipelinePhase("CheckAuthentication")

        /**
         * Phase for authentications mechanisms to plug into
         */
        val RequestAuthentication = PipelinePhase("RequestAuthentication")
    }
}