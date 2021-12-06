/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.pipeline.*

/**
 * Represents a form-based authentication provider
 */
public class FormAuthenticationProvider internal constructor(config: Configuration) : AuthenticationProvider(config) {
    internal val userParamName: String = config.userParamName

    internal val passwordParamName: String = config.passwordParamName

    internal val challenge: FormAuthChallengeFunction = config.challengeFunction

    internal val authenticationFunction: AuthenticationFunction<UserPasswordCredential> =
        config.authenticationFunction

    /**
     * Form auth provider configuration
     */
    public class Configuration internal constructor(name: String?) : AuthenticationProvider.Configuration(name) {
        internal var authenticationFunction: AuthenticationFunction<UserPasswordCredential> = { null }

        internal var challengeFunction: FormAuthChallengeFunction = {
            call.respond(UnauthorizedResponse())
        }

        /**
         * POST parameter to fetch for a user name
         */
        public var userParamName: String = "user"

        /**
         * POST parameter to fetch for a user password
         */
        public var passwordParamName: String = "password"

        /**
         * Configure challenge (response to send back) if authentication failed.
         */
        public fun challenge(function: FormAuthChallengeFunction) {
            challengeFunction = function
        }

        /**
         * Configure redirect challenge if authentication failed
         */
        public fun challenge(redirectUrl: String) {
            challenge {
                call.respondRedirect(redirectUrl)
            }
        }

        /**
         * Configure redirect challenge if authentication failed
         */
        public fun challenge(redirect: Url) {
            challenge(redirect.toString())
        }

        /**
         * Sets a validation function that will check given [UserPasswordCredential] instance and return [Principal],
         * or null if credential does not correspond to an authenticated principal
         */
        public fun validate(body: suspend ApplicationCall.(UserPasswordCredential) -> Principal?) {
            authenticationFunction = body
        }

        internal fun build() = FormAuthenticationProvider(this)
    }
}

/**
 * Installs Form Authentication mechanism
 */
public fun Authentication.Configuration.form(
    name: String? = null,
    configure: FormAuthenticationProvider.Configuration.() -> Unit
) {
    val provider = FormAuthenticationProvider.Configuration(name).apply(configure).build()

    provider.pipeline.intercept(AuthenticationPipeline.RequestAuthentication) { context ->
        val postParameters = call.receiveOrNull<Parameters>()
        val username = postParameters?.get(provider.userParamName)
        val password = postParameters?.get(provider.passwordParamName)

        val credentials = if (username != null && password != null) UserPasswordCredential(username, password) else null
        val principal = credentials?.let { (provider.authenticationFunction)(call, it) }

        if (principal != null) {
            context.principal(principal)
        } else {
            val cause =
                if (credentials == null) AuthenticationFailedCause.NoCredentials
                else AuthenticationFailedCause.InvalidCredentials

            context.challenge(formAuthenticationChallengeKey, cause) {
                provider.challenge(this, credentials)
                if (!it.completed && call.response.status() != null) {
                    it.complete()
                }
            }
        }
    }

    register(provider)
}

/**
 * Specifies what to send back if form authentication fails.
 */
public typealias FormAuthChallengeFunction =
    suspend PipelineContext<*, ApplicationCall>.(UserPasswordCredential?) -> Unit

private val formAuthenticationChallengeKey: Any = "FormAuth"
