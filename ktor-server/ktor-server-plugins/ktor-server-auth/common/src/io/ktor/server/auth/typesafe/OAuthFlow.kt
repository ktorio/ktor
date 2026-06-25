/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalKtorApi::class)

package io.ktor.server.auth.typesafe

import io.ktor.client.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.server.util.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import kotlin.reflect.KClass

internal sealed class OAuthCallback {
    abstract val path: String

    class Basic(
        override val path: String,
        val successHandler: CallbackSuccessHandler
    ) : OAuthCallback()

    class Session<S : Any, P : Any>(
        override val path: String,
        val successHandler: SessionCallbackSuccessHandler<S, P>,
        val failureHandler: UnauthorizedHandler,
    ) : OAuthCallback()
}

/**
 * Configures the OAuth 2.0 authentication provider used by a typed [OAuthFlow].
 *
 * This DSL intentionally exposes only OAuth 2.0 settings. The typed flow bridges this configuration into Ktor's
 * lower-level OAuth provider internally.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.OAuthFlowConfigBase)
 */
@KtorDsl
@ExperimentalKtorApi
public abstract class OAuthFlowConfigBase internal constructor() {

    /**
     * A description of the provider that can be used for API documentation.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.OAuthFlowConfigBase.description)
     */
    public var description: String? = null

    /**
     * HTTP client used to call the OAuth token endpoint.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.OAuthFlowConfigBase.client)
     */
    public var client: HttpClient? = null

    /**
     * Static OAuth 2.0 server settings. Either this or [providerLookup] must be specified.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.OAuthFlowConfigBase.settings)
     */
    public var settings: OAuthServerSettings.OAuth2ServerSettings? = null

    /**
     * Resolves OAuth 2.0 server settings for the current routing context.
     *
     * Either this or [settings] must be specified.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.OAuthFlowConfigBase.providerLookup)
     */
    public var providerLookup: (suspend RoutingContext.() -> OAuthServerSettings.OAuth2ServerSettings?)? = null

    /**
     * Optional login route that starts the OAuth redirect when visited.
     *
     * Only the path from [URLBuilder] is used to create the route; other URL components are ignored.
     *
     * Set to `null` to disable the login route.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.OAuthFlowConfigBase.loginUri)
     */
    public var loginUri: (URLBuilder.() -> Unit)? = null

    /**
     * Handles OAuth 2.0 errors such as authorization errors and token exchange failures.
     *
     * If the handler does not complete the call, authentication continues through the default challenge handling.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.OAuthFlowConfigBase.onUnauthorized)
     */
    public var onUnauthorized: UnauthorizedHandler = {}

    internal fun resolveLoginUriPath(): String? {
        val builder = loginUri ?: return null
        return URLBuilder().apply(builder).encodedPath
    }

    public open fun validate(flowName: String) {
        require(client != null) {
            "OAuth flow '$flowName' requires an HTTP client"
        }
        require(settings != null || providerLookup != null) {
            "OAuth flow '$flowName' requires settings or providerLookup"
        }
    }

    internal fun buildProvider(name: String, callbackPath: String): OAuthAuthenticationProvider {
        val oauthClient = requireNotNull(this@OAuthFlowConfigBase.client)
        return OAuthAuthenticationProvider.Config(name, description).also { config ->
            config.client = oauthClient
            config.settings = settings
            config.providerLookup = providerLookup?.let { lookup -> { toRoutingContext().lookup() } }
            config.urlProvider = { url { encodedPath = callbackPath } }
            config.fallback = { cause -> toRoutingContext().onUnauthorized(cause) }
        }.build()
    }
}

internal typealias CallbackSuccessHandler = suspend RoutingContext.(OAuthAccessTokenResponse) -> Unit

internal typealias SessionCallbackSuccessHandler<S, P> = suspend context(SessionContext<S, P>)
RoutingContext.() -> Unit

/**
 * Configures a typed OAuth 2.0 authorization-code flow.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.OAuthFlowConfig)
 */
@KtorDsl
@ExperimentalKtorApi
public class OAuthFlowConfig internal constructor() : OAuthFlowConfigBase() {
    internal var callback: OAuthCallback.Basic? = null

    /**
     * Configures the OAuth callback route and success handler.
     *
     * The redirect URI sent to the OAuth provider is derived automatically from [path].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.OAuthFlowConfig.callback)
     *
     * @param path route path used for both the initial OAuth redirect and the callback.
     * @param onSuccess handler invoked after OAuth token exchange succeeds.
     */
    public fun callback(path: String, onSuccess: CallbackSuccessHandler) {
        callback = OAuthCallback.Basic(path, onSuccess)
    }

    public override fun validate(flowName: String) {
        super.validate(flowName)
        requireNotNull(callback) {
            "OAuth flow '$flowName' requires a callback route. Set callback(\"/callback\") { ... }."
        }
    }
}

/**
 * Configures a typed OAuth 2.0 flow with session-backed route authentication.
 *
 * Combines [OAuthFlowConfigBase] with session storage, session creation, and principal resolution via [sessions].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.OAuthSessionFlowConfig)
 *
 * @param S the session type stored by the [Sessions] plugin.
 * @param P the principal type available in protected route handlers.
 */
@KtorDsl
@ExperimentalKtorApi
public class OAuthSessionFlowConfig<S : Any, P : Any> @PublishedApi internal constructor() : OAuthFlowConfigBase() {
    internal var callback: OAuthCallback.Session<S, P>? = null

    public var sessionsConfig: OAuth2SessionsConfig<S, P>? = null

    /**
     * Configures the OAuth callback route and success handler for a session-backed flow.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.OAuthSessionFlowConfig.callback)
     *
     * @param path route path used for both the initial OAuth redirect and the callback.
     * @param onFailure handler invoked when session creation or principal resolution fails.
     * @param onSuccess handler invoked after the session and principal are stored.
     */
    public fun callback(
        path: String,
        onFailure: UnauthorizedHandler = { _ -> call.respond(HttpStatusCode.Unauthorized) },
        onSuccess: SessionCallbackSuccessHandler<S, P>,
    ) {
        callback = OAuthCallback.Session(path, onSuccess, onFailure)
    }

    /**
     * Configures session storage, session creation, and principal resolution for this OAuth session flow.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.OAuthSessionFlowConfig.sessions)
     */
    public fun sessions(configure: OAuth2SessionsConfig<S, P>.() -> Unit) {
        sessionsConfig = OAuth2SessionsConfig<S, P>().apply(configure)
    }

    public override fun validate(flowName: String) {
        super.validate(flowName)
        requireNotNull(callback) {
            "OAuth session flow '$flowName' requires a callback route. Set callback(\"/callback\") { ... }."
        }
    }
}

/**
 * Configures session storage and principal resolution for an [OAuth2SessionFlow].
 *
 * The OAuth callback authenticates the token exchange. [sessionCreator] maps the OAuth response to the stored
 * session value [S], and [validate] maps that session to the route principal [P].
 *
 * Protected routes use the challenge strategy from [TypedSessionAuthConfig].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.OAuth2SessionsConfig)
 *
 * @param S the session type stored by the [Sessions] plugin.
 * @param P the principal type available in protected route handlers.
 */
@KtorDsl
@ExperimentalKtorApi
public open class OAuth2SessionsConfig<S : Any, P : Any> internal constructor() : TypedSessionAuthConfig<S, P>() {

    /**
     * Session provider name used by the [Sessions] plugin.
     *
     * Defaults to the OAuth flow name in uppercase followed by `_SESSION`.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.OAuth2SessionsConfig.name)
     */
    public var name: String? = null

    /**
     * Maps a successful OAuth response to the session value stored by the [Sessions] plugin.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.OAuth2SessionsConfig.sessionCreator)
     */
    public var sessionCreator: (suspend RoutingContext.(OAuthAccessTokenResponse.OAuth2) -> S?)? = null

    public open fun validate(flowName: String) {
        requireNotNull(sessionCreator) {
            "OAuth session flow '$flowName' requires sessionCreator in sessions { ... }"
        }
        requireNotNull(principalResolver) {
            "OAuth session flow '$flowName' requires validate { ... } in sessions { ... }"
        }
    }
}

/**
 * Typed OAuth 2.0 authentication scheme that exposes [OAuthAccessTokenResponse.OAuth2].
 */
@OptIn(ExperimentalKtorApi::class)
public typealias OAuthScheme = DefaultAuthenticatedScheme<OAuthAccessTokenResponse.OAuth2>

/**
 * Typed OAuth 2.0 authorization-code flow.
 *
 * Install callback and optional login routes with [io.ktor.server.routing.Route.install]. Use [oauth2SessionFlow]
 * to add session-backed route authentication.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.OAuthFlow)
 */
@ExperimentalKtorApi
public open class OAuthFlow internal constructor(
    internal val name: String,
    internal val oauthScheme: OAuthScheme,
    internal val callback: OAuthCallback.Basic,
    internal val loginUriPath: String?,
) {
    public companion object {

        /**
         * Creates an OAuth 2.0 flow from a configured Ktor OAuth provider.
         *
         * This API is intended for integrations that need to build typed-auth flows from existing OAuth provider
         * configuration.
         *
         * @param name flow name used to derive authentication provider names.
         * @param config configures the underlying Ktor OAuth provider.
         * @return configured OAuth 2.0 flow.
         */
        @InternalAPI
        public fun from(name: String, config: OAuthFlowConfig): OAuthFlow {
            config.validate(name)
            val oauthScheme = createOauthScheme(name, config)
            val loginUriPath = config.resolveLoginUriPath()
            val callback = checkNotNull(config.callback)
            return OAuthFlow(name, oauthScheme, callback, loginUriPath)
        }
    }
}

private fun createOauthScheme(name: String, config: OAuthFlowConfigBase): OAuthScheme {
    val providerName = "$name-oauth"
    val callback = when (config) {
        is OAuthFlowConfig -> config.callback
        is OAuthSessionFlowConfig<*, *> -> config.callback
        else -> error("Unsupported OAuth flow config type: ${config::class.simpleName}")
    }
    require(callback != null) {
        "OAuth flow '$name' requires a callback route. Set callback(\"/callback\") { ... }."
    }
    return DefaultAuthScheme(
        name = providerName,
        principalType = OAuthAccessTokenResponse.OAuth2::class,
        provider = config.buildProvider(providerName, callback.path),
        onUnauthorized = null,
        contextFactory = { it }
    )
}

/**
 * Typed OAuth 2.0 flow with session-backed route authentication.
 *
 * The OAuth callback stores session values produced by [OAuth2SessionsConfig.sessionCreator]. Application routes are
 * protected with [sessions].
 *
 * @param S the session type stored by the [Sessions] plugin.
 * @param P the principal type available in protected route handlers.
 * @property sessions typed session authentication scheme used with [authenticateWith].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.OAuth2SessionFlow)
 */
@ExperimentalKtorApi
public class OAuth2SessionFlow<S : Any, P : Any> internal constructor(
    internal val name: String,
    internal val oauthScheme: OAuthScheme,
    public val sessions: SessionAuthScheme<S, P>,
    config: OAuthSessionFlowConfig<S, P>,
) {
    internal val callback = checkNotNull(config.callback)
    internal val loginUriPath: String? = config.resolveLoginUriPath()
    internal val sessionCreator = checkNotNull(config.sessionsConfig?.sessionCreator)
    internal val principalResolver = checkNotNull(config.sessionsConfig?.principalResolver)

    public companion object {
        /**
         * Creates a session-backed OAuth 2.0 flow from [OAuthSessionFlowConfig].
         *
         * This API is intended for integrations that need explicit session and principal type information.
         *
         * @param name flow name used to derive authentication provider names.
         * @param config OAuth session flow configuration.
         * @param sessionTypeInfo stored session type information.
         * @param principalType route principal type.
         * @return OAuth 2.0 flow with typed session authentication.
         */
        @InternalAPI
        public fun <S : Any, P : Any> from(
            name: String,
            config: OAuthSessionFlowConfig<S, P>,
            sessionTypeInfo: TypeInfo,
            principalType: KClass<P>,
        ): OAuth2SessionFlow<S, P> {
            config.validate(name)
            val oauthScheme = createOauthScheme(name, config)
            val sessionsConfig = requireNotNull(config.sessionsConfig) {
                "OAuth session flow '$name' requires sessions { ... } configuration"
            }
            sessionsConfig.validate(name)
            val providerName = sessionsConfig.name ?: (name.uppercase() + "_SESSION")
            val sessionScheme = SessionAuthScheme.from(providerName, sessionTypeInfo, principalType, sessionsConfig)
            return OAuth2SessionFlow(name, oauthScheme, sessionScheme, config)
        }
    }
}

/**
 * Creates a typed OAuth 2.0 authorization-code flow.
 *
 * Configure the callback route with [OAuthFlowConfig.callback] and install the flow with
 * [io.ktor.server.routing.Route.install].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.oauth2Flow)
 *
 * @param name name that identifies the OAuth flow.
 * @param configure configures the underlying Ktor OAuth provider and callback route.
 * @return an OAuth 2.0 flow for route installation.
 */
@ExperimentalKtorApi
@OptIn(InternalAPI::class)
public fun oauth2Flow(name: String, configure: OAuthFlowConfig.() -> Unit): OAuthFlow =
    OAuthFlow.from(name, OAuthFlowConfig().apply(configure))

/**
 * Creates a typed OAuth 2.0 authorization-code flow with session-backed route authentication.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.oauth2SessionFlow)
 *
 * @param name name that identifies the OAuth flow.
 * @param configure configures the OAuth provider, callback route, and session authentication.
 * @return OAuth 2.0 flow with typed session authentication.
 */
@ExperimentalKtorApi
@OptIn(InternalAPI::class)
public inline fun <reified P : Any, reified S : Any> oauth2SessionFlow(
    name: String,
    configure: OAuthSessionFlowConfig<S, P>.() -> Unit,
): OAuth2SessionFlow<S, P> {
    val config = OAuthSessionFlowConfig<S, P>().apply(configure)
    config.validate(name)
    return OAuth2SessionFlow.from(
        name = name,
        config = config,
        principalType = P::class,
        sessionTypeInfo = typeInfo<S>(),
    )
}

/**
 * Installs an [OAuthFlow] into the routing tree.
 *
 * Creates the configured callback route and optional login route.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.install)
 *
 * @param flow OAuth 2.0 flow to install.
 */
@ExperimentalKtorApi
public fun Route.install(flow: OAuthFlow) {
    val scheme = flow.oauthScheme
    val route = installOAuthAuthRoute(scheme)
    with(scheme.install(route, onUnauthorized = null)) {
        route.get(flow.callback.path) {
            val response = call.principal
            flow.callback.successHandler(this, response)
        }
    }
    flow.loginUriPath?.let { loginPath ->
        route.installOAuthLoginRoute(scheme, loginPath)
    }
}

/**
 * Installs an [OAuth2SessionFlow] into the routing tree.
 *
 * Creates the configured callback route and optional login route.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.install)
 *
 * @param flow OAuth 2.0 flow with typed session authentication.
 */
@ExperimentalKtorApi
public fun <S : Any, P : Any> Route.install(flow: OAuth2SessionFlow<S, P>) {
    val route = installOAuthAuthRoute(flow.oauthScheme)
    flow.sessions.installSessionsPlugin(route)
    flow.oauthScheme.install(route, onUnauthorized = null)

    val callback = flow.callback
    route.get(callback.path) callback@{
        try {
            val token = call.attributes[flow.oauthScheme.principalKey]
            val session = flow.sessionCreator(this, token) ?: run {
                val error = AuthenticationFailedCause.Error("Failed to create OAuth session")
                return@callback callback.failureHandler(this, error)
            }
            call.sessions.set(flow.sessions, session)

            val principal = flow.principalResolver(this, session) ?: run {
                val error = AuthenticationFailedCause.Error("Failed to create OAuth principal")
                return@callback callback.failureHandler(this, error)
            }
            call.attributes.put(flow.sessions.sessionKey, session)
            flow.sessions.capture(call, principal)

            val sessionContext = SessionContext(
                base = PrincipalContext(flow.sessions.principalKey),
                sessionKey = flow.sessions.sessionKey,
                sessionProviderName = flow.sessions.name
            )
            with(sessionContext) {
                callback.successHandler(this@callback)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            callback.failureHandler(
                this@callback,
                AuthenticationFailedCause.Error(e.message ?: "Failed to create OAuth principal")
            )
            return@callback
        }
    }

    flow.loginUriPath?.let { loginPath ->
        route.installOAuthLoginRoute(scheme = flow.oauthScheme, path = loginPath)
    }
}

/**
 * Installs an [OAuthFlow] at the application routing root.
 *
 * Equivalent to `application.routing { install(flow) }`.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.install)
 */
@ExperimentalKtorApi
public fun Application.install(flow: OAuthFlow) {
    routing { install(flow) }
}

/**
 * Installs an [OAuth2SessionFlow] at the application routing root.
 *
 * Equivalent to `application.routing { install(flow) }`.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.install)
 */
@ExperimentalKtorApi
public fun Application.install(flow: OAuth2SessionFlow<*, *>) {
    routing { install(flow) }
}

private fun Route.installOAuthAuthRoute(scheme: OAuthScheme): Route {
    val route = createChild(AuthenticationRouteSelector(listOf(scheme.name)))
    route.install(createOAuthBodyCachePlugin())
    scheme.preinstall(route)
    return route
}

private fun Route.installOAuthLoginRoute(scheme: OAuthScheme, path: String) {
    authenticateWith(scheme) {
        get(path) { }
    }
}
