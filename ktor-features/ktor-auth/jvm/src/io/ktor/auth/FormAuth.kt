/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.auth

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
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

        @Suppress("DEPRECATION_ERROR")
        private var _challenge: FormAuthChallenge? = FormAuthChallenge.Unauthorized

        /**
         * POST parameter to fetch for a user name
         */
        public var userParamName: String = "user"

        /**
         * POST parameter to fetch for a user password
         */
        public var passwordParamName: String = "password"

        /**
         * A response to send back if authentication failed
         */
        @Deprecated("Use challenge {} instead.", level = DeprecationLevel.ERROR)
        @Suppress("DEPRECATION_ERROR")
        public var challenge: FormAuthChallenge
            get() = _challenge ?: error("Challenge is already configured via challenge() function")
            set(value) {
                _challenge = value
                challengeFunction = { credentials ->
                    challengeCompatibility(value, credentials)
                }
            }

        /**
         * Configure challenge (response to send back) if authentication failed.
         */
        public fun challenge(function: FormAuthChallengeFunction) {
            _challenge = null
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
public typealias FormAuthChallengeFunction = suspend PipelineContext<*, ApplicationCall>.(UserPasswordCredential?) -> Unit

/**
 * Specifies what to send back if form authentication fails.
 */
@Suppress("DEPRECATION_ERROR")
@Deprecated("Use challenge {} instead.", level = DeprecationLevel.ERROR)
public sealed class FormAuthChallenge {
    /**
     * Redirect to an URL provided by the given function.
     * @property url is a function receiving [ApplicationCall] and [UserPasswordCredential] and returning an URL to redirect to.
     */
    @Deprecated("Use challenge {} instead.", level = DeprecationLevel.ERROR)
    public class Redirect(public val url: ApplicationCall.(UserPasswordCredential?) -> String) : FormAuthChallenge()

    /**
     * Respond with [HttpStatusCode.Unauthorized].
     */
    @Deprecated("Use challenge {} instead.", level = DeprecationLevel.ERROR)
    public object Unauthorized : FormAuthChallenge()
}

private val formAuthenticationChallengeKey: Any = "FormAuth"

@Suppress("DEPRECATION_ERROR")
private suspend fun PipelineContext<*, ApplicationCall>.challengeCompatibility(
    challenge: FormAuthChallenge,
    credentials: UserPasswordCredential?
) {
    when (challenge) {
        FormAuthChallenge.Unauthorized -> call.respond(UnauthorizedResponse())
        is FormAuthChallenge.Redirect -> call.respondRedirect(challenge.url(call, credentials))
    }
}
