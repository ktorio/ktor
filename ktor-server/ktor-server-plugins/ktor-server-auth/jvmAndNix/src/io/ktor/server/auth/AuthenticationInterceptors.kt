/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import io.ktor.util.logging.*
import io.ktor.util.pipeline.*

internal object AuthenticationHook : Hook<suspend (ApplicationCall) -> Unit> {
    internal val AuthenticatePhase: PipelinePhase = PipelinePhase("Authenticate")

    override fun install(
        application: ApplicationCallPipeline,
        handler: suspend (ApplicationCall) -> Unit
    ) {
        application.insertPhaseAfter(ApplicationCallPipeline.Plugins, AuthenticatePhase)
        application.intercept(AuthenticatePhase) { handler(call) }
    }
}

internal object ChallengeHook : Hook<suspend (ApplicationCall) -> Unit> {
    internal val ChallengePhase: PipelinePhase = PipelinePhase("Challenge")

    override fun install(
        application: ApplicationCallPipeline,
        handler: suspend (ApplicationCall) -> Unit
    ) {
        application.insertPhaseAfter(ApplicationCallPipeline.Plugins, AuthenticationHook.AuthenticatePhase)
        application.insertPhaseAfter(AuthenticationHook.AuthenticatePhase, ChallengePhase)
        application.intercept(ChallengePhase) { handler(call) }
    }
}

/**
 * A hook that is executed after authentication succeeds.
 * Note that this hook executes also for optional authentication or for routes without any authentication,
 * resulting in [ApplicationCall.principal] being `null`
 */
public object AfterAuthenticationHook : Hook<suspend (ApplicationCall) -> Unit> {
    internal val AfterAuthenticationPhase: PipelinePhase = PipelinePhase("AfterAuthentication")

    override fun install(
        application: ApplicationCallPipeline,
        handler: suspend (ApplicationCall) -> Unit
    ) {
        application.insertPhaseAfter(ApplicationCallPipeline.Plugins, AuthenticationHook.AuthenticatePhase)
        application.insertPhaseAfter(AuthenticationHook.AuthenticatePhase, ChallengeHook.ChallengePhase)
        application.insertPhaseAfter(ChallengeHook.ChallengePhase, AfterAuthenticationPhase)
        application.intercept(AfterAuthenticationPhase) { handler(call) }
    }
}

private val logger = KtorSimpleLogger("Authentication")

/**
 * A plugin that authenticate calls. Usually used via [authenticate] function inside routing.
 */
public val AuthenticationInterceptors: RouteScopedPlugin<RouteAuthenticationConfig, PluginInstance> =
    createRouteScopedPlugin("AuthenticationInterceptors", ::RouteAuthenticationConfig) {

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
                if (provider.skipWhen.any { skipCondition -> skipCondition(call) }) continue

                provider.onAuthenticate(authenticationContext)

                if (authenticationContext.principal != null) break
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
            if (pluginConfig.optional &&
                context.allFailures.none { it == AuthenticationFailedCause.InvalidCredentials }
            ) {
                return@on
            }

            val challenges = context.challenge.challenges

            for (challenge in challenges) {
                challenge(context.challenge, call)
                if (context.challenge.completed) {
                    if (!call.isHandled) {
                        call.respond(UnauthorizedResponse())
                    }
                    return@on
                }
            }

            for (challenge in context.challenge.errorChallenges) {
                challenge(context.challenge, call)
                if (context.challenge.completed) {
                    if (!call.isHandled) {
                        call.respond(UnauthorizedResponse())
                    }
                    return@on
                }
            }

            for (error in context.allErrors) {
                if (!context.challenge.completed) {
                    logger.trace("Responding unauthorized because of error ${error.message}")
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
 * Creates an authentication route that does handle authentication by the specified providers referred by
 * [configurations] names. `null` could be used to point to the default provider and could be also mixed with other
 * provider names.
 * Other routes, handlers and interceptors could be nested into this node
 *
 * The [Authentication] plugin need to be installed first otherwise
 * it will fail with [MissingApplicationPluginException] and all providers requested by [configurations] need
 * to be already registered.
 *
 * It is important to note that when [optional] is set, challenges will be skipped only if no credentials are provided.
 *
 * To summarize:
 *
 * optional=false:
 *   - No credentials: challenge is sent and route is NOT executed
 *   - Bad credentials: Unauthorized
 *   - Good credentials: route handler will be executed
 *
 * optional=true:
 *   - No credentials: route handler will be executed with a null [Principal]
 *   - Bad credentials: Unauthorized
 *   - Good credentials: route handler will be executed
 *
 * @param configurations names that point to already registered authentication providers
 * @param optional when set, if no auth is provided by the client, the call will continue but with a null [Principal]
 * @throws MissingApplicationPluginException if no [Authentication] plugin installed first
 * @throws IllegalArgumentException if there are no registered providers referred by [configurations] names
 */
public fun Route.authenticate(
    vararg configurations: String? = arrayOf(null),
    optional: Boolean = false,
    build: Route.() -> Unit
): Route {
    require(configurations.isNotEmpty()) { "At least one configuration name or null for default need to be provided" }
    val configurationNames = configurations.distinct().toList()
    val authenticatedRoute = createChild(AuthenticationRouteSelector(configurationNames))
    attributes.put(AuthenticateProviderNamesKey, configurationNames)
    val allConfigurations = generateSequence(this) { it.parent }
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
 * A config for [AuthenticationInterceptors] plugin
 */
public class RouteAuthenticationConfig {
    internal var configurations: List<String?> = listOf(null)
    internal var optional: Boolean = false
}

/**
 * An authentication route node that is used by [Authentication] plugin
 * and usually created by [Route.authenticate] DSL function so generally there is no need to instantiate it directly
 * unless you are writing an extension
 * @param names of authentication providers to be applied to this route
 */
public class AuthenticationRouteSelector(public val names: List<String?>) : RouteSelector() {
    override fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation {
        return RouteSelectorEvaluation.Transparent
    }

    override fun toString(): String = "(authenticate ${names.joinToString { it ?: "\"default\"" }})"
}

private val AuthenticateProviderNamesKey = AttributeKey<List<String?>>("AuthenticateProviderNamesKey")
