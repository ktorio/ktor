/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*

/**
 * Represents a form-based authentication provider
 */
public class FormAuthenticationProvider internal constructor(config: Config) : AuthenticationProvider(config) {
    private val userParamName: String = config.userParamName

    private val passwordParamName: String = config.passwordParamName

    private val challengeFunction: FormAuthChallengeFunction = config.challengeFunction

    private val authenticationFunction: AuthenticationFunction<UserPasswordCredential> =
        config.authenticationFunction

    override suspend fun onAuthenticate(context: AuthenticationContext) {
        val call = context.call
        val postParameters = call.receiveOrNull<Parameters>()
        val username = postParameters?.get(userParamName)
        val password = postParameters?.get(passwordParamName)

        val credentials = if (username != null && password != null) UserPasswordCredential(username, password) else null
        val principal = credentials?.let { (authenticationFunction)(call, it) }

        if (principal != null) {
            context.principal(principal)
            return
        }
        val cause = when (credentials) {
            null -> AuthenticationFailedCause.NoCredentials
            else -> AuthenticationFailedCause.InvalidCredentials
        }

        @Suppress("NAME_SHADOWING")
        context.challenge(formAuthenticationChallengeKey, cause) { challenge, call ->
            challengeFunction(FormAuthChallengeContext(call), credentials)
            if (!challenge.completed && call.response.status() != null) {
                challenge.complete()
            }
        }
    }

    /**
     * Form auth provider configuration
     */
    public class Config internal constructor(name: String?) : AuthenticationProvider.Config(name) {
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
public fun AuthenticationConfig.form(
    name: String? = null,
    configure: FormAuthenticationProvider.Config.() -> Unit
) {
    val provider = FormAuthenticationProvider.Config(name).apply(configure).build()
    register(provider)
}

/**
 * A context for [FormAuthChallengeFunction]
 */
public class FormAuthChallengeContext(
    public val call: ApplicationCall
)

/**
 * Specifies what to send back if form authentication fails.
 */
public typealias FormAuthChallengeFunction =
    suspend FormAuthChallengeContext.(UserPasswordCredential?) -> Unit

private val formAuthenticationChallengeKey: Any = "FormAuth"
