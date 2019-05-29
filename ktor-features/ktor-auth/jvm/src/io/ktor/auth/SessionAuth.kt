/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.auth

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.sessions.*
import io.ktor.util.*
import kotlin.reflect.*

/**
 * Represents a session-based authentication provider
 * @property type of session
 * @property challenge to be used if there is no session
 * @property validator applied to an application all and session providing a [Principal]
 */
class SessionAuthenticationProvider<T : Any> private constructor(
    config: Configuration<T>
) : AuthenticationProvider(config) {
    val type: KClass<T> = config.type

    @PublishedApi
    internal val challenge: SessionAuthChallenge<T> = config.challenge

    @PublishedApi
    internal val validator: AuthenticationFunction<T> = config.validator

    /**
     * Session auth configuration
     */
    class Configuration<T : Any> @PublishedApi internal constructor(
        name: String?,
        internal val type: KClass<T>
    ) : AuthenticationProvider.Configuration(name) {
        internal var validator: AuthenticationFunction<T> = UninitializedValidator

        /**
         * A response to send back if authentication failed
         */
        var challenge: SessionAuthChallenge<T> = SessionAuthChallenge.Default

        /**
         * Sets a validation function that will check given [T] session instance and return [Principal],
         * or null if the session does not correspond to an authenticated principal
         */
        fun validate(block: suspend ApplicationCall.(T) -> Principal?) {
            check(validator === UninitializedValidator) { "Only one validator could be registered" }
            validator = block
        }

        private fun verifyConfiguration() {
            check(validator !== UninitializedValidator) { "It should be a validator supplied to a session auth provider" }
        }

        @PublishedApi
        internal fun buildProvider(): SessionAuthenticationProvider<T> {
            verifyConfiguration()
            return SessionAuthenticationProvider(this)
        }
    }

    companion object {
        private val UninitializedValidator: suspend ApplicationCall.(Any) -> Principal? = {
            error("It should be a validator supplied to a session auth provider")
        }
    }
}


/**
 * Provides ability to authenticate users via sessions. It only works if [T] session type denotes [Principal] as well
 * otherwise use full [session] with lambda function with [SessionAuthenticationProvider.Configuration.validate] configuration
 */
inline fun <reified T : Principal> Authentication.Configuration.session(
    name: String? = null,
    challenge: SessionAuthChallenge<T> = SessionAuthChallenge.Default
) {
    session<T>(name) {
        this.challenge = challenge
        validate { session -> session }
    }
}

/**
 * Provides ability to authenticate users via sessions. It is important to have
 * specified [SessionAuthenticationProvider.Configuration.validate] and
 * [SessionAuthenticationProvider.Configuration.challenge] in the lambda
 * to get it work property
 */
inline fun <reified T : Any> Authentication.Configuration.session(
    name: String? = null,
    configure: SessionAuthenticationProvider.Configuration<T>.() -> Unit
) {
    val provider = SessionAuthenticationProvider.Configuration(name, T::class).apply(configure).buildProvider()

    provider.pipeline.intercept(AuthenticationPipeline.CheckAuthentication) { context ->
        val session = call.sessions.get<T>()
        val principal = session?.let { provider.validator(call, it) }

        if (principal != null) {
            context.principal(principal)
        } else {
            val cause =
                if (session == null) AuthenticationFailedCause.NoCredentials else AuthenticationFailedCause.InvalidCredentials
            if (provider.challenge != SessionAuthChallenge.Ignore) {
                context.challenge(SessionAuthChallengeKey, cause) {
                    when (val challenge = provider.challenge) {
                        is SessionAuthChallenge.Unauthorized -> call.respond(HttpStatusCode.Unauthorized)
                        is SessionAuthChallenge.Redirect -> call.respondRedirect(challenge.url(call, session))
                    }
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
sealed class SessionAuthChallenge<in T : Any> {
    /**
     * Redirect to an URL provided by the given function.
     * @property url is a function receiving [ApplicationCall] and [UserPasswordCredential] and returning an URL to redirect to.
     */
    class Redirect<in T : Any>(val url: ApplicationCall.(T?) -> String) : SessionAuthChallenge<T>()

    /**
     * Respond with [HttpStatusCode.Unauthorized].
     */
    object Unauthorized : SessionAuthChallenge<Any>()

    /**
     * Does nothing so other authentication methods could provide their challenges.
     * This is the  default and recommended way
     */
    object Ignore : SessionAuthChallenge<Any>()

    companion object {
        /**
         * The default session auth challenge kind
         */
        @KtorExperimentalAPI
        val Default: SessionAuthChallenge<Any> = Ignore
    }
}

/**
 * A key used to register auth challenge
 */
const val SessionAuthChallengeKey: String = "SessionAuth"
