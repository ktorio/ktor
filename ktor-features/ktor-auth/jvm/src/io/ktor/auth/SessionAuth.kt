/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.auth

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.sessions.*
import io.ktor.util.*
import io.ktor.util.pipeline.*
import kotlin.reflect.*

/**
 * Represents a session-based authentication provider
 * @property type of session
 * @property challenge to be used if there is no session
 * @property validator applied to an application all and session providing a [Principal]
 */
public class SessionAuthenticationProvider<T : Any> private constructor(
    config: Configuration<T>
) : AuthenticationProvider(config) {
    public val type: KClass<T> = config.type

    @PublishedApi
    internal val challenge: SessionAuthChallengeFunction<T> = config.challengeFunction

    @PublishedApi
    internal val validator: AuthenticationFunction<T> = config.validator

    /**
     * Session auth configuration
     */
    public class Configuration<T : Any> @PublishedApi internal constructor(
        name: String?,
        internal val type: KClass<T>
    ) : AuthenticationProvider.Configuration(name) {
        internal var validator: AuthenticationFunction<T> = UninitializedValidator

        internal var challengeFunction: SessionAuthChallengeFunction<T> = {
        }

        @Suppress("DEPRECATION_ERROR")
        private var _challenge: SessionAuthChallenge<T>? = SessionAuthChallenge.Ignore

        /**
         * A response to send back if authentication failed
         */
        @Suppress("DEPRECATION_ERROR")
        @Deprecated("Use challenge {} instead.", level = DeprecationLevel.ERROR)
        public var challenge: SessionAuthChallenge<T>
            get() = _challenge ?: error("Challenge is already configured via challenge {} function.")
            set(value) {
                _challenge = value
                challengeFunction = {
                    sessionAuthChallengeCompatibility(value, it)
                }
            }

        /**
         * A response to send back if authentication failed
         */
        public fun challenge(block: SessionAuthChallengeFunction<T>) {
            _challenge = null
            challengeFunction = block
        }

        /**
         * A response to send back if authentication failed
         */
        public fun challenge(redirectUrl: String) {
            challenge {
                call.respondRedirect(redirectUrl)
            }
        }

        /**
         * A response to send back if authentication failed
         */
        public fun challenge(redirect: Url) {
            challenge(redirect.toString())
        }

        /**
         * Sets a validation function that will check given [T] session instance and return [Principal],
         * or null if the session does not correspond to an authenticated principal
         */
        public fun validate(block: suspend ApplicationCall.(T) -> Principal?) {
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

    public companion object {
        private val UninitializedValidator: suspend ApplicationCall.(Any) -> Principal? = {
            error("It should be a validator supplied to a session auth provider")
        }
    }
}


/**
 * Provides ability to authenticate users via sessions. It only works if [T] session type denotes [Principal] as well
 * otherwise use full [session] with lambda function with [SessionAuthenticationProvider.Configuration.validate] configuration
 */
@Deprecated(
    "Use session(name) { } instead specifying validate as well.",
    replaceWith = ReplaceWith("session<T>(name) { validate { session -> session }\nthis.challenge { TODO(\"Implement your challenge\") }}"),
    level = DeprecationLevel.ERROR
)
@Suppress("DEPRECATION_ERROR")
public inline fun <reified T : Principal> Authentication.Configuration.session(
    name: String? = null,
    challenge: SessionAuthChallenge<T>
) {
    session<T>(name) {
        this.challenge = challenge
        validate { session -> session }
    }
}

/**
 * Provides ability to authenticate users via sessions. It only works if [T] session type denotes [Principal] as well
 * otherwise use full [session] with lambda function with [SessionAuthenticationProvider.Configuration.validate] configuration
 */
public inline fun <reified T : Principal> Authentication.Configuration.session(
    name: String? = null
) {
    session<T>(name) {
        validate { session -> session }
    }
}

/**
 * Provides ability to authenticate users via sessions. It is important to have
 * specified [SessionAuthenticationProvider.Configuration.validate] and
 * [SessionAuthenticationProvider.Configuration.challenge] in the lambda
 * to get it work property
 */
public inline fun <reified T : Any> Authentication.Configuration.session(
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
                if (session == null) AuthenticationFailedCause.NoCredentials
                else AuthenticationFailedCause.InvalidCredentials

            context.challenge(SessionAuthChallengeKey, cause) {
                provider.challenge(this, principal)
                if (!it.completed && call.response.status() != null) {
                    it.complete()
                }
            }
        }
    }

    register(provider)
}

/**
 * Specifies what to send back if session authentication fails.
 */
public typealias SessionAuthChallengeFunction<T> = suspend PipelineContext<*, ApplicationCall>.(T?) -> Unit

/**
 * Specifies what to send back if authentication fails.
 */
@Suppress("DEPRECATION_ERROR")
@Deprecated("Use challenge {} instead.", level = DeprecationLevel.ERROR)
public sealed class SessionAuthChallenge<in T : Any> {
    /**
     * Redirect to an URL provided by the given function.
     * @property url is a function receiving [ApplicationCall] and [UserPasswordCredential] and returning an URL to redirect to.
     */
    @Deprecated("Use challenge {} instead.", level = DeprecationLevel.ERROR)
    public class Redirect<in T : Any>(public val url: ApplicationCall.(T?) -> String) : SessionAuthChallenge<T>()

    /**
     * Respond with [HttpStatusCode.Unauthorized].
     */
    @Deprecated("Use challenge {} instead.", level = DeprecationLevel.ERROR)
    public object Unauthorized : SessionAuthChallenge<Any>()

    /**
     * Does nothing so other authentication methods could provide their challenges.
     * This is the  default and recommended way
     */
    @Deprecated("Use challenge {} instead.", level = DeprecationLevel.ERROR)
    public object Ignore : SessionAuthChallenge<Any>()

    public companion object {
        /**
         * The default session auth challenge kind
         */
        @Deprecated("Use challenge {} instead.", level = DeprecationLevel.ERROR)
        public val Default: SessionAuthChallenge<Any> = Ignore
    }
}

/**
 * A key used to register auth challenge
 */
public const val SessionAuthChallengeKey: String = "SessionAuth"

@Suppress("DEPRECATION_ERROR")
private suspend fun <T : Any> PipelineContext<*, ApplicationCall>.sessionAuthChallengeCompatibility(
    challenge: SessionAuthChallenge<T>,
    session: T?
) {
    when (challenge) {
        SessionAuthChallenge.Unauthorized -> call.respond(HttpStatusCode.Unauthorized)
        is SessionAuthChallenge.Redirect<T> -> call.respondRedirect(challenge.url(call, session))
        SessionAuthChallenge.Ignore -> {}
    }
}
