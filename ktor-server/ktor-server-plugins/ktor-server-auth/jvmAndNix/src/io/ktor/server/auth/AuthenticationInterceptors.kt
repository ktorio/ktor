/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import io.ktor.util.logging.*
import io.ktor.util.pipeline.*

internal val LOGGER = KtorSimpleLogger("io.ktor.server.auth.Authentication")

internal object AuthenticationHook : Hook<suspend (ApplicationCall) -> Unit> {
    internal val AuthenticatePhase: PipelinePhase = PipelinePhase("Authenticate")

    override fun install(
        pipeline: ApplicationCallPipeline,
        handler: suspend (ApplicationCall) -> Unit
    ) {
        pipeline.insertPhaseAfter(ApplicationCallPipeline.Plugins, AuthenticatePhase)
        pipeline.intercept(AuthenticatePhase) { handler(call) }
    }
}

internal object ChallengeHook : Hook<suspend (ApplicationCall) -> Unit> {
    internal val ChallengePhase: PipelinePhase = PipelinePhase("Challenge")

    override fun install(
        pipeline: ApplicationCallPipeline,
        handler: suspend (ApplicationCall) -> Unit
    ) {
        pipeline.insertPhaseAfter(ApplicationCallPipeline.Plugins, AuthenticationHook.AuthenticatePhase)
        pipeline.insertPhaseAfter(AuthenticationHook.AuthenticatePhase, ChallengePhase)
        pipeline.intercept(ChallengePhase) { handler(call) }
    }
}

/**
 * A hook that is executed after authentication was checked.
 * Note that this hook is also executed for optional authentication or for routes without any authentication,
 * resulting in [ApplicationCall.principal] being `null`.
 */
public object AuthenticationChecked : Hook<suspend (ApplicationCall) -> Unit> {
    internal val AfterAuthenticationPhase: PipelinePhase = PipelinePhase("AfterAuthentication")

    override fun install(
        pipeline: ApplicationCallPipeline,
        handler: suspend (ApplicationCall) -> Unit
    ) {
        pipeline.insertPhaseAfter(ApplicationCallPipeline.Plugins, AuthenticationHook.AuthenticatePhase)
        pipeline.insertPhaseAfter(AuthenticationHook.AuthenticatePhase, ChallengeHook.ChallengePhase)
        pipeline.insertPhaseAfter(ChallengeHook.ChallengePhase, AfterAuthenticationPhase)
        pipeline.intercept(AfterAuthenticationPhase) { handler(call) }
    }
}

/**
 * A plugin that authenticates calls. Usually used via the [authenticate] function inside routing.
 */
public val AuthenticationInterceptors: RouteScopedPlugin<RouteAuthenticationConfig> = createRouteScopedPlugin(
    "AuthenticationInterceptors",
    ::RouteAuthenticationConfig
) {
    on(AuthenticationHook) { call ->
        val authConfig = call.application.plugin(Authentication).config

        val authenticationContext = AuthenticationContext.from(call)
        if (authenticationContext.principal != null) return@on

        val configurations = pluginConfig.configurations.map { configurationName ->
            authConfig.providers[configurationName] ?: throw IllegalArgumentException(
                if (configurationName == null) {
                    "Default authentication configuration was not found"
                } else {
                    "Authentication configuration with the name $configurationName was not found"
                }
            )
        }
        for (provider in configurations) {
            if (provider.skipWhen.any { skipCondition -> skipCondition(call) }) {
                LOGGER.trace("Skipping authentication provider ${provider.name} for ${call.request.uri}")
                continue
            }

            LOGGER.trace("Trying to authenticate ${call.request.uri} with ${provider.name}")
            provider.onAuthenticate(authenticationContext)

            if (authenticationContext.principal != null) {
                LOGGER.trace("Authentication succeeded for ${call.request.uri} with provider $provider")
                break
            } else {
                LOGGER.trace("Authentication failed for ${call.request.uri} with provider $provider")
            }
        }
    }

    on(ChallengeHook) { call ->
        val context = AuthenticationContext.from(call)

        if (context.principal != null) return@on
        if (context.challenge.completed) {
            if (!call.isHandled) {
                call.respond(UnauthorizedResponse())
            }
            return@on
        }
        if (pluginConfig.optional && context.allFailures.none { it == AuthenticationFailedCause.InvalidCredentials }) {
            LOGGER.trace("Authentication is optional and no credentials were provided for ${call.request.uri}")
            return@on
        }

        val challenges = context.challenge.challenges

        for (challenge in challenges) {
            challenge(context.challenge, call)
            if (context.challenge.completed) {
                if (!call.isHandled) {
                    LOGGER.trace("Responding unauthorized because call is not handled.")
                    call.respond(UnauthorizedResponse())
                }
                return@on
            }
        }

        for (challenge in context.challenge.errorChallenges) {
            challenge(context.challenge, call)
            if (context.challenge.completed) {
                if (!call.isHandled) {
                    LOGGER.trace("Responding unauthorized because call is not handled.")
                    call.respond(UnauthorizedResponse())
                }
                return@on
            }
        }

        for (error in context.allErrors) {
            if (!context.challenge.completed) {
                LOGGER.trace("Responding unauthorized because of error ${error.message}")
                if (!call.isHandled) {
                    call.respond(UnauthorizedResponse())
                }
                context.challenge.complete()
                return@on
            }
        }
    }
}

/**
 * Creates a route that allows you to define authorization scope for application resources.
 * This function accepts names of authentication providers defined in the [Authentication] plugin configuration.
 * @see [Authentication]
 *
 * @param configurations names of authentication providers defined in the [Authentication] plugin configuration.
 * @param optional when set, if no authentication is provided by the client, a call continues but with a null [Principal].
 * @throws MissingApplicationPluginException if no [Authentication] plugin installed first.
 * @throws IllegalArgumentException if there are no registered providers referred by [configurations] names.
 */
public fun Route.authenticate(
    vararg configurations: String? = arrayOf(null),
    optional: Boolean = false,
    build: Route.() -> Unit
): Route {
    require(configurations.isNotEmpty()) { "At least one configuration name or null for default need to be provided" }

    val configurationNames = configurations.distinct().toList()
    val authenticatedRoute = createChild(AuthenticationRouteSelector(configurationNames))
    authenticatedRoute.attributes.put(AuthenticateProviderNamesKey, configurationNames)
    val allConfigurations = generateSequence(authenticatedRoute) { it.parent }
        .toList()
        .reversed()
        .flatMap { it.attributes.getOrNull(AuthenticateProviderNamesKey).orEmpty() }
        .distinct()

    authenticatedRoute.install(AuthenticationInterceptors) {
        this.configurations = allConfigurations
        this.optional = optional
    }
    authenticatedRoute.build()
    return authenticatedRoute
}

/**
 * A configuration for the [AuthenticationInterceptors] plugin.
 */
@KtorDsl
public class RouteAuthenticationConfig {
    internal var configurations: List<String?> = listOf(null)
    internal var optional: Boolean = false
}

/**
 * An authentication route node that is used by [Authentication] plugin
 * and usually created by the [Route.authenticate] DSL function, so generally there is no need to instantiate it directly
 * unless you are writing an extension.
 * @param names of authentication providers to be applied to this route.
 */
public class AuthenticationRouteSelector(public val names: List<String?>) : RouteSelector() {
    override fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation {
        return RouteSelectorEvaluation.Transparent
    }

    override fun toString(): String = "(authenticate ${names.joinToString { it ?: "\"default\"" }})"
}

private val AuthenticateProviderNamesKey = AttributeKey<List<String?>>("AuthenticateProviderNamesKey")
