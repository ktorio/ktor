/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth

import io.ktor.http.Parameters
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import io.ktor.util.logging.*
import io.ktor.util.pipeline.*
import io.ktor.util.reflect.typeInfo
import io.ktor.utils.io.*
import io.ktor.utils.io.readBuffer
import kotlinx.io.readByteArray

internal val LOGGER = KtorSimpleLogger("io.ktor.server.auth.Authentication")

internal object AuthenticationHook : Hook<suspend (ApplicationCall) -> Unit> {

    override fun install(
        pipeline: ApplicationCallPipeline,
        handler: suspend (ApplicationCall) -> Unit
    ) {
        pipeline.intercept(ApplicationCallPipeline.Validators) { handler(call) }
    }
}

/**
 * A hook that is executed after authentication was checked.
 * Note that this hook is also executed for optional authentication or for routes without any authentication,
 * resulting in [ApplicationCall.principal] being `null`.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.AuthenticationChecked)
 */
public object AuthenticationChecked : Hook<suspend (ApplicationCall) -> Unit> {
    internal val AfterAuthenticationPhase: PipelinePhase = PipelinePhase("AfterAuthentication")

    override fun install(
        pipeline: ApplicationCallPipeline,
        handler: suspend (ApplicationCall) -> Unit
    ) {
        pipeline.insertPhaseAfter(ApplicationCallPipeline.Validators, AfterAuthenticationPhase)
        pipeline.intercept(AfterAuthenticationPhase) { handler(call) }
    }
}

/**
 * A plugin that authenticates calls. Usually used via the [authenticate] function inside routing.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.AuthenticationInterceptors)
 */
@OptIn(InternalAPI::class)
public val AuthenticationInterceptors: RouteScopedPlugin<RouteAuthenticationConfig> = createRouteScopedPlugin(
    "AuthenticationInterceptors",
    ::RouteAuthenticationConfig
) {
    val authConfig = application.plugin(Authentication).config
    val plan = authConfig.buildPlan(pluginConfig.providers)

    // To cache the request body which can be consumed by the OAuth2 callback handler
    installOAuthBodyCache()

    on(AuthenticationHook) { call ->
        if (call.isHandled) return@on
        val context = AuthenticationContext.from(call)
        AuthenticationRun(plan, context).execute(call, ownIndex = plan.lastIndex)
    }
}

private object AuthPluginNameGenerator {
    private var nextId: Int = 0

    fun next(): String = "AuthenticationInterceptors:${nextId++}"
}

/**
 * Creates the plugin installed by a single [authenticate] call.
 *
 * Nested `authenticate` blocks share one [AuthenticationPlan] built from all of them. Each block runs only its own
 * and its ancestors' steps, at its own position in the route pipeline, so plugins nested between two blocks
 * (such as `rateLimit`) can already see the outer block's principal.
 */
@OptIn(InternalAPI::class)
private fun AuthenticateProvidersRegistration.toPlugin(): RouteScopedPlugin<RouteAuthenticationConfig> =
    createRouteScopedPlugin(
        name = AuthPluginNameGenerator.next(),
        createConfiguration = ::RouteAuthenticationConfig
    ) {
        val authentication = application.plugin(Authentication)
        names.forEach { authentication.config.findProvider(it) }

        // To cache the request body which can be consumed by the OAuth2 callback handler
        installOAuthBodyCache()

        on(AuthenticationHook) { call ->
            if (call.isHandled) {
                return@on
            }
            val route = checkNotNull((call as? RoutingPipelineCall)?.route) {
                "authenticate() interceptors must run for a routed call, but got ${call::class} for ${call.request.uri}"
            }
            val context = AuthenticationContext.from(call)

            val run = call.attributes.computeIfAbsent(AuthenticationRunKey) {
                val plan = route.attributes.computeIfAbsent(AuthenticationPlanKey) {
                    val registrations = route.authenticateRegistrations()
                    authentication.config.buildPlan(registrations)
                }
                AuthenticationRun(plan, context)
            }

            val ownIndex = run.plan.registrations.indexOfFirst { it === this@toPlugin }
            check(ownIndex >= 0) {
                "Authentication registration ${this@toPlugin} is not on the route ${call.request.uri}"
            }
            run.execute(call, ownIndex)
        }
    }

@OptIn(InternalAPI::class)
private fun AuthenticationConfig.buildPlan(registrations: List<AuthenticateProvidersRegistration>): AuthenticationPlan {
    fun phaseOf(strategy: PhaseStrategy, filter: (AuthenticationStrategy) -> Boolean): Phase {
        val owners = buildMap {
            registrations.forEachIndexed { index, registration ->
                if (!filter(registration.strategy)) {
                    return@forEachIndexed
                }
                for (name in registration.names) {
                    val provider = findProvider(name)
                    if (provider !in this) put(provider, index)
                }
            }
        }
        val steps = owners.map { (provider, owner) -> AuthenticationStep(provider, owner) }
        return Phase(steps, strategy)
    }

    val phases = listOf(
        phaseOf(strategy = PhaseStrategy.AllRequired) { it == AuthenticationStrategy.Required },
        phaseOf(strategy = PhaseStrategy.FirstSuccessful) { it != AuthenticationStrategy.Required },
    )
    return AuthenticationPlan(registrations, phases)
}

@OptIn(InternalAPI::class)
private fun RoutingNode.authenticateRegistrations(): List<AuthenticateProvidersRegistration> =
    generateSequence(this) { it.parent }
        .mapNotNull { it.attributes.getOrNull(AuthenticateProvidersKey) }
        .toList()
        .reversed()

private val AuthenticationPlanKey = AttributeKey<AuthenticationPlan>("AuthenticationPlan")
private val AuthenticationRunKey = AttributeKey<AuthenticationRun>("AuthenticationRun")

internal val cacheOAuthFormReceiveKey = AttributeKey<Unit>("OauthFormReceiveKey")

internal val formCacheKey = AttributeKey<ByteArray>("AuthFormCacheKey")

internal object ReceiveBytes : Hook<suspend (ApplicationCall, Any) -> Any> {
    override fun install(
        pipeline: ApplicationCallPipeline,
        handler: suspend (ApplicationCall, Any) -> Any
    ) {
        pipeline.receivePipeline.intercept(ApplicationReceivePipeline.Before) {
            val body = handler(call, it)
            proceedWith(body)
        }
    }
}

/**
 * Installs the OAuth body cache hook.
 *
 * OAuth providers may consume the request body during authentication.
 * This hook caches form bodies so they can be read again by the route handler.
 */
internal fun RouteScopedPluginBuilder<*>.installOAuthBodyCache() {
    on(ReceiveBytes) { call, body ->
        if (!call.attributes.contains(cacheOAuthFormReceiveKey) || call.receiveType != typeInfo<Parameters>()) {
            val cache = call.attributes.getOrNull(formCacheKey)
            return@on if (cache != null) ByteReadChannel(cache) else body
        }
        if (body !is ByteReadChannel) {
            return@on body
        }
        try {
            val array = body.readBuffer().readByteArray()
            call.attributes.put(formCacheKey, array)
            return@on ByteReadChannel(array)
        } finally {
            call.attributes.remove(cacheOAuthFormReceiveKey)
        }
    }
}

/**
 * Creates a standalone route-scoped plugin that installs the OAuth body cache.
 */
internal fun createOAuthBodyCachePlugin() = createRouteScopedPlugin("OAuthBodyCache") {
    installOAuthBodyCache()
}

internal suspend fun AuthenticationContext.executeChallenges(call: ApplicationCall) {
    val challenges = challenge.challenges

    if (this.executeChallenges(challenges, call)) return

    if (this.executeChallenges(challenge.errorChallenges, call)) return

    for (error in allErrors) {
        if (!challenge.completed) {
            LOGGER.trace { "Authentication failed for ${call.request.uri} with error ${error.message}" }
            if (!call.isHandled) {
                call.respond(UnauthorizedResponse())
            }
            challenge.complete()
            return
        }
    }
}

internal suspend fun AuthenticationContext.executeChallenges(
    challenges: List<ChallengeFunction>,
    call: ApplicationCall
): Boolean {
    for (challengeFunction in challenges) {
        challengeFunction(challenge, call)
        if (challenge.completed) {
            if (!call.isHandled) {
                LOGGER.trace("Responding unauthorized because call is not handled.")
                call.respond(UnauthorizedResponse())
            }
            return true
        }
    }
    return false
}

public fun AuthenticationConfig.allProviders(): Map<String?, AuthenticationProvider> {
    return providers
}

private fun AuthenticationConfig.findProvider(configurationName: String?): AuthenticationProvider {
    return providers[configurationName] ?: throw IllegalArgumentException(
        if (configurationName == null) {
            "Default authentication configuration was not found. "
        } else {
            "Authentication configuration with the name $configurationName was not found. "
        } + "Make sure that you install Authentication plugin before you use it in Routing"
    )
}

/**
 *  A resolution strategy for nested authentication providers.
 *  [AuthenticationStrategy.Optional] - if the client provides no authentication,
 *  a call continues but with a null principal.
 *  [AuthenticationStrategy.FirstSuccessful] - client must provide authentication data for at least one provider
 *  registered for this route
 *  [AuthenticationStrategy.Required] - client must provide authentication data for all providers registered for
 *  this route with this strategy
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.AuthenticationStrategy)
 */
public enum class AuthenticationStrategy { Optional, FirstSuccessful, Required }

/**
 * Creates a route that allows you to define authorization scope for application resources.
 * This function accepts names of authentication providers defined in the [Authentication] plugin configuration.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.authenticate)
 *
 * @see [Authentication]
 *
 * @param configurations names of authentication providers defined in the [Authentication] plugin configuration.
 * @param optional when set, if the client provides no authentication,
 * a call continues but with a null authenticated value.
 * @throws MissingApplicationPluginException if no [Authentication] plugin installed first.
 * @throws IllegalArgumentException if there are no registered providers referred by [configurations] names.
 */
public fun Route.authenticate(
    vararg configurations: String? = arrayOf(null),
    optional: Boolean = false,
    build: Route.() -> Unit
): Route {
    return authenticate(
        configurations = configurations,
        strategy = if (optional) AuthenticationStrategy.Optional else AuthenticationStrategy.FirstSuccessful,
        build = build
    )
}

/**
 * Creates a route that allows you to define authorization scope for application resources.
 * This function accepts names of authentication providers defined in the [Authentication] plugin configuration.
 *
 * When combined with other route-scoped guard plugins such as [io.ktor.server.plugins.ratelimit.rateLimit],
 * the relative execution order follows route nesting: interceptors on parent routes run before interceptors
 * on child routes. To use a principal in a rate limit [io.ktor.server.plugins.ratelimit.RateLimitProviderConfig.requestKey],
 * nest [rateLimit] inside [authenticate].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.authenticate)
 *
 * @see [Authentication]
 *
 * @param configurations names of authentication providers defined in the [Authentication] plugin configuration.
 * @param strategy defines resolution strategy for nested authentication providers.
 *  [AuthenticationStrategy.Optional] - if the client provides no authentication,
 *  a call continues but with a null authenticated value.
 *  [AuthenticationStrategy.FirstSuccessful] - client must provide authentication data for at least one provider
 *  registered for this route
 *  [AuthenticationStrategy.Required] - client must provide authentication data for all providers registered for
 *  this route with this strategy
 * @throws MissingApplicationPluginException if no [Authentication] plugin installed first.
 * @throws IllegalArgumentException if there are no registered providers referred by [configurations] names.
 */
@OptIn(InternalAPI::class)
public fun Route.authenticate(
    vararg configurations: String? = arrayOf(null),
    strategy: AuthenticationStrategy,
    build: Route.() -> Unit
): Route {
    require(configurations.isNotEmpty()) {
        "At least one configuration name or null for default need to be provided"
    }

    val configurationNames = configurations.distinct().toList()
    val authenticatedRoute = createChild(selector = AuthenticationRouteSelector(configurationNames))
    val registration = AuthenticateProvidersRegistration(configurationNames, strategy)
    authenticatedRoute.attributes.put(AuthenticateProvidersKey, registration)
    authenticatedRoute.install(plugin = registration.toPlugin())
    authenticatedRoute.build()
    return authenticatedRoute
}

/**
 * A configuration for the [AuthenticationInterceptors] plugin.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.RouteAuthenticationConfig)
 */
@KtorDsl
@InternalAPI
public class RouteAuthenticationConfig {
    internal var providers: List<AuthenticateProvidersRegistration> =
        listOf(AuthenticateProvidersRegistration(listOf(null), AuthenticationStrategy.FirstSuccessful))
}

/**
 * An authentication route node that is used by [Authentication] plugin
 * and usually created by the [Route.authenticate] DSL function,
 * so generally there is no need to instantiate it directly unless you are writing an extension.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.AuthenticationRouteSelector)
 *
 * @param names of authentication providers to be applied to this route.
 */
@OptIn(InternalAPI::class)
public class AuthenticationRouteSelector(public val names: List<String?>) : TransparentRouteSelector() {

    override fun toString(): String = "(authenticate ${names.joinToString { it ?: "\"$DEFAULT_NAME\"" }})"

    public companion object {
        public const val DEFAULT_NAME: String = "default"
    }
}

@InternalAPI
public class AuthenticateProvidersRegistration(
    public val names: List<String?>,
    public val strategy: AuthenticationStrategy
)

@InternalAPI
public val AuthenticateProvidersKey: AttributeKey<AuthenticateProvidersRegistration> =
    AttributeKey<AuthenticateProvidersRegistration>("AuthenticateProviderNamesKey")
