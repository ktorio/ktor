/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.utils.io.InternalAPI

/**
 * A form-based authentication provider.
 *
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.FormAuthenticationProvider)
 *
 * @see [form]
 */
public class FormAuthenticationProvider internal constructor(config: Config) : AuthenticationProvider(config) {
    @InternalAPI
    public val userParamName: String = config.userParamName

    @InternalAPI
    public val passwordParamName: String = config.passwordParamName

    private val challengeFunction: FormAuthChallengeFunction = config.challengeFunction

    private val authenticationFunction: AuthenticationFunction<UserPasswordCredential> =
        config.authenticationFunction

    @OptIn(InternalAPI::class)
    override suspend fun onAuthenticate(context: AuthenticationContext) {
        val call = context.call
        val postParameters = runCatching { call.receiveNullable<Parameters>() }.getOrNull()
        val username = postParameters?.get(userParamName)
        val password = postParameters?.get(passwordParamName)

        val credentials = if (username != null && password != null) UserPasswordCredential(username, password) else null
        val principal = credentials?.let { (authenticationFunction)(call, it) }

        if (principal != null) {
            context.principal(name, principal)
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
     * A configuration for the [form]-based authentication provider.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.FormAuthenticationProvider.Config)
     */
    public class Config internal constructor(
        name: String?,
        description: String?
    ) : AuthenticationProvider.Config(name, description) {
        internal var authenticationFunction: AuthenticationFunction<UserPasswordCredential> = { null }

        internal var challengeFunction: FormAuthChallengeFunction = {
            call.respond(UnauthorizedResponse())
        }

        /**
         * Specifies a POST parameter name used to fetch a username.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.FormAuthenticationProvider.Config.userParamName)
         */
        public var userParamName: String = "user"

        /**
         * Specifies a POST parameter name used to fetch a password.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.FormAuthenticationProvider.Config.passwordParamName)
         */
        public var passwordParamName: String = "password"

        /**
         * Specifies a response sent to the client if authentication fails.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.FormAuthenticationProvider.Config.challenge)
         */
        public fun challenge(function: FormAuthChallengeFunction) {
            challengeFunction = function
        }

        /**
         * Specifies a redirect URL in a case of failed authentication.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.FormAuthenticationProvider.Config.challenge)
         */
        public fun challenge(redirectUrl: String) {
            challenge {
                call.respondRedirect(redirectUrl)
            }
        }

        /**
         * Specifies a redirect URL in a case of failed authentication.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.FormAuthenticationProvider.Config.challenge)
         */
        public fun challenge(redirect: Url) {
            challenge(redirect.toString())
        }

        /**
         * Sets a validation function that checks a specified [UserPasswordCredential] instance and
         * returns principal [Any] in a case of successful authentication or null if authentication fails.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.FormAuthenticationProvider.Config.validate)
         */
        public fun validate(body: suspend ApplicationCall.(UserPasswordCredential) -> Any?) {
            authenticationFunction = body
        }

        internal fun build() = FormAuthenticationProvider(this)
    }
}

/**
 * Installs the form-based [Authentication] provider.
 * Form-based authentication uses a web form to collect credential information and authenticate a user.
 * To learn how to configure it, see [Form-based authentication](https://ktor.io/docs/form.html).
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.form)
 */
public fun AuthenticationConfig.form(
    name: String? = null,
    configure: FormAuthenticationProvider.Config.() -> Unit
) {
    form(name, description = null, configure)
}

/**
 * Installs the form-based [Authentication] provider with description.
 * Form-based authentication uses a web form to collect credential information and authenticate a user.
 * To learn how to configure it, see [Form-based authentication](https://ktor.io/docs/form.html).
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.form)
 */
public fun AuthenticationConfig.form(
    name: String? = null,
    description: String? = null,
    configure: FormAuthenticationProvider.Config.() -> Unit
) {
    val provider = FormAuthenticationProvider.Config(name, description).apply(configure).build()
    register(provider)
}

/**
 * A context for [FormAuthChallengeFunction].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.FormAuthChallengeContext)
 */
public class FormAuthChallengeContext(public val call: ApplicationCall)

/**
 * Specifies what to send back if form-based authentication fails.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.FormAuthChallengeFunction)
 */
public typealias FormAuthChallengeFunction = suspend FormAuthChallengeContext.(UserPasswordCredential?) -> Unit

private val formAuthenticationChallengeKey: Any = "FormAuth"
