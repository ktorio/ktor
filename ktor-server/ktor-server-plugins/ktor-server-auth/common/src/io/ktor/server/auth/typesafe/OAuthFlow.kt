/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalKtorApi::class)

package io.ktor.server.auth.typesafe

import io.ktor.client.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import kotlin.reflect.KClass

/**
 * Creates a stored session value from a successful OAuth token response.
 *
 * The creator receives the current [RoutingContext] and OAuth token response.
 *
 * Return `null` to fail the OAuth callback after token exchange succeeds.
 */
public typealias SessionCreator<S> = suspend RoutingContext.(OAuthAccessTokenResponse) -> S?

/**
 * Configures a typed OAuth 2.0 flow.
 */
public typealias OAuth2Configure = OAuth2Config.() -> Unit

/**
 * Configures the OAuth 2.0 authentication provider used by a typed [OAuth2Flow].
 *
 * This DSL intentionally exposes only OAuth 2.0 settings. The typed flow bridges this configuration into Ktor's
 * lower-level OAuth provider internally.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.OAuth2Config)
 */
@ExperimentalKtorApi
@KtorDsl
public class OAuth2Config @InternalAPI constructor() {

    /**
     * A description of the provider that can be used for API documentation.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.OAuth2Config.description)
     */
    public var description: String? = null

    /**
     * HTTP client used to call the OAuth token endpoint.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.OAuth2Config.client)
     */
    public var client: HttpClient? = null

    /**
     * Static OAuth 2.0 server settings. Either this or [providerLookup] must be specified.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.OAuth2Config.settings)
     */
    public var settings: OAuthServerSettings.OAuth2ServerSettings? = null

    /**
     * Resolves OAuth 2.0 server settings for the current routing context.
     *
     * Either this or [settings] must be specified.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.OAuth2Config.providerLookup)
     */
    public var providerLookup: (suspend RoutingContext.() -> OAuthServerSettings.OAuth2ServerSettings?)? = null

    /**
     * Builds the redirect URI used during OAuth authorization and token exchange.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.OAuth2Config.urlProvider)
     */
    public var urlProvider: (suspend RoutingContext.(OAuthServerSettings.OAuth2ServerSettings) -> String)? = null

    /**
     * Handles OAuth 2.0 errors such as authorization errors and token exchange failures.
     *
     * If the handler does not complete the call, authentication continues through the default challenge handling.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.OAuth2Config.onForbidden)
     */
    public var onForbidden: UnauthorizedHandler = {}

    internal fun validate(flowName: String) {
        require(client != null) {
            "OAuth2Flow $flowName requires an HTTP client"
        }
        require(urlProvider != null) {
            "OAuth2Flow $flowName requires a URL provider"
        }
    }

    internal fun buildProvider(name: String): OAuthAuthenticationProvider {
        val oauthClient = requireNotNull(this@OAuth2Config.client)
        val oauthUrlProvider = requireNotNull(this@OAuth2Config.urlProvider)
        require(settings != null || providerLookup != null) {
            "Either settings or providerLookup should be specified"
        }

        return OAuthAuthenticationProvider.Config(name, description).also { config ->
            config.client = oauthClient
            config.settings = settings
            config.providerLookup = providerLookup?.let { lookup -> { toRoutingContext().lookup() } }
            config.urlProvider = { settings ->
                val oauth2Settings = checkNotNull(settings as? OAuthServerSettings.OAuth2ServerSettings) {
                    "Typed OAuth2Flow expects OAuth 2.0 server settings, but got ${settings::class.simpleName}"
                }
                toRoutingContext().oauthUrlProvider(oauth2Settings)
            }
            config.fallback = { cause -> toRoutingContext().onForbidden(cause) }
        }.build()
    }
}

/**
 * Configures a typed session wrapper around an [OAuth2Flow].
 *
 * The base OAuth flow authenticates the callback request. [sessionCreator] maps the OAuth response to the stored
 * session value [S], and [TypedSessionAuthConfig.validate] maps that session to the route principal [P].
 *
 * Protected routes use the challenge strategy from [TypedSessionAuthConfig]. The OAuth callback route uses the
 * challenge configured by the wrapped [OAuth2Flow].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.OAuth2SessionConfig)
 *
 * @param S the session type stored by the [Sessions] plugin.
 * @param P the principal type available in protected route handlers.
 * @param C the authenticated route context type.
 */
@ExperimentalKtorApi
@KtorDsl
public class OAuth2SessionConfig<S : Any, P : Any, C : SessionAuthenticatedContext<S, P>> @InternalAPI constructor() :
    TypedSessionAuthConfig<S, P, C>() {

    /**
     * Session provider name used by the [Sessions] plugin.
     *
     * Defaults to the OAuth flow name in uppercase followed by `_SESSION`.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.OAuth2SessionConfig.sessionName)
     */
    public var sessionName: String? = null

    /**
     * Maps a successful OAuth response to the session value stored by the [Sessions] plugin.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.OAuth2SessionConfig.sessionCreator)
     */
    public var sessionCreator: SessionCreator<S>? = null

    internal fun validate(flowName: String) {
        requireNotNull(sessionCreator) {
            "Session creator cannot be null for OAuth2SessionFlow $flowName"
        }
        requireNotNull(principalResolver) {
            "Principal resolver cannot be null for OAuth2SessionFlow $flowName"
        }
    }
}

/**
 * Typed OAuth 2.0 authentication scheme that exposes [OAuthAccessTokenResponse.OAuth2].
 */
@OptIn(ExperimentalKtorApi::class)
public typealias OAuth2Scheme = DefaultAuthenticatedScheme<OAuthAccessTokenResponse.OAuth2>

/**
 * Typed OAuth 2.0 authorization-code flow.
 *
 * Create a callback route with [oauthCallback]. Use [withSessions] or [OAuth2SessionFlow.from] to add session-backed
 * route authentication.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.OAuth2Flow)
 */
@ExperimentalKtorApi
public open class OAuth2Flow internal constructor(
    internal val name: String,
    internal val oauthScheme: DefaultAuthenticatedScheme<OAuthAccessTokenResponse.OAuth2>,
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
        public fun from(name: String, config: OAuth2Configure): OAuth2Flow {
            val oauthProviderName = "$name-oauth"
            val oauth2Config = OAuth2Config().apply(config)
            oauth2Config.validate(name)
            val oauthScheme = DefaultAuthScheme(
                name = oauthProviderName,
                principalType = OAuthAccessTokenResponse.OAuth2::class,
                provider = oauth2Config.buildProvider(oauthProviderName),
                onUnauthorized = null,
                contextFactory = { it }
            )
            return OAuth2Flow(name, oauthScheme)
        }
    }
}

/**
 * Typed OAuth 2.0 flow with session-backed route authentication.
 *
 * The OAuth callback stores session values produced by [OAuth2SessionConfig.sessionCreator]. Application routes are
 * protected with [sessions].
 *
 * @param S the session type stored by the [Sessions] plugin.
 * @param P the principal type available in protected route handlers.
 * @param C the authenticated route context type.
 * @param oauth base OAuth flow used for callback authentication.
 * @param config session flow configuration.
 * @property sessions typed session authentication scheme used with [authenticateWith].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.OAuth2SessionFlow)
 */
@ExperimentalKtorApi
public class OAuth2SessionFlow<S : Any, P : Any, C : SessionAuthenticatedContext<S, P>>(
    internal val oauth: OAuth2Flow,
    public val sessions: SessionAuthScheme<S, P, C>,
    internal val config: OAuth2SessionConfig<S, P, C>,
) {
    internal val name: String = oauth.name
    internal val oauthScheme: DefaultAuthenticatedScheme<OAuthAccessTokenResponse.OAuth2> = oauth.oauthScheme

    internal val sessionCreator = checkNotNull(config.sessionCreator)

    internal val principalResolver = checkNotNull(config.principalResolver)

    public companion object {
        /**
         * Creates a session-backed OAuth 2.0 flow from an existing [OAuth2Flow].
         *
         * This API is intended for integrations that need explicit session and principal type information.
         *
         * @param oauth base OAuth flow used for callback authentication.
         * @param config session authentication configuration.
         * @param sessionTypeInfo stored session type information.
         * @param principalType route principal type.
         * @return OAuth 2.0 flow with typed session authentication.
         */
        @InternalAPI
        public fun <S : Any, P : Any, C : SessionAuthenticatedContext<S, P>> from(
            oauth: OAuth2Flow,
            config: OAuth2SessionConfig<S, P, C>,
            sessionTypeInfo: TypeInfo,
            principalType: KClass<P>,
        ): OAuth2SessionFlow<S, P, C> {
            config.validate(oauth.name)
            val providerName = config.sessionName ?: (oauth.name.uppercase() + "_SESSION")
            val sessionScheme = SessionAuthScheme.from(
                name = providerName,
                sessionTypeInfo = sessionTypeInfo,
                principalType = principalType,
                config = config
            )
            return OAuth2SessionFlow(oauth, sessionScheme, config)
        }
    }
}

/**
 * Creates a typed OAuth 2.0 authorization-code flow.
 *
 * The returned flow installs callback handling with [oauthCallback]. It does not expose a route authentication scheme
 * until wrapped with [withSessions] or [OAuth2SessionFlow.from].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.oauth2Flow)
 *
 * @param name name that identifies the OAuth flow.
 * @param configure configures the underlying Ktor OAuth provider.
 * @return an OAuth 2.0 flow for callback route installation.
 */
@ExperimentalKtorApi
@OptIn(InternalAPI::class)
public fun oauth2Flow(name: String, configure: OAuth2Configure): OAuth2Flow = OAuth2Flow.from(name, configure)

/**
 * Adds typed session authentication to an [OAuth2Flow].
 *
 * Configure [OAuth2SessionConfig.sessionCreator] to create the stored session value and
 * [TypedSessionAuthConfig.validate] to expose the route principal.
 *
 * @param configure configures session storage, session creation, validation, CSRF, and context creation.
 * @return OAuth 2.0 flow with typed session authentication.
 */
@ExperimentalKtorApi
@OptIn(InternalAPI::class)
public inline fun <reified P : Any, reified S : Any> OAuth2Flow.withSessions(
    configure: OAuth2SessionConfig<S, P, DefaultSessionAuthenticatedContext<S, P>>.() -> Unit
): OAuth2SessionFlow<S, P, DefaultSessionAuthenticatedContext<S, P>> {
    val config = OAuth2SessionConfig<S, P, DefaultSessionAuthenticatedContext<S, P>>().apply(configure)
    check(config.contextFactory == null)
    config.contextFactory = { it }
    return OAuth2SessionFlow.from(
        oauth = this,
        config = config,
        principalType = P::class,
        sessionTypeInfo = typeInfo<S>(),
    )
}

/**
 * Creates an OAuth 2.0 callback route.
 *
 * Calling [path] before OAuth completes starts the OAuth challenge. Calling [path] with OAuth callback parameters uses
 * the OAuth provider in [flow], captures the [OAuthAccessTokenResponse.OAuth2], and invokes [onSuccess].
 *
 * ```kotlin
 * oauthCallback(flow, path = "/callback") {
 *     call.respondText(principal.accessToken)
 * }
 * ```
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.oauthCallback)
 *
 * @param flow OAuth 2.0 flow.
 * @param path route path used for both the initial OAuth redirect and the callback.
 * @param onSuccess handler invoked after OAuth token exchange succeeds.
 * @return the route that contains the OAuth callback.
 */
@ExperimentalKtorApi
public fun Route.oauthCallback(
    flow: OAuth2Flow,
    path: String,
    onSuccess: suspend context(DefaultAuthenticatedContext<OAuthAccessTokenResponse.OAuth2>) RoutingContext.() -> Unit
): Route {
    val route = createChild(AuthenticationRouteSelector(listOf(flow.oauthScheme.name)))
    route.install(createOAuthBodyCachePlugin())
    with(flow.oauthScheme.install(route, onUnauthorized = null)) {
        route.get(path) { onSuccess() }
    }
    return route
}

/**
 * Creates an OAuth 2.0 callback route that stores a typed session after a successful callback.
 *
 * Calling [path] before OAuth completes starts the OAuth challenge. Calling [path] with OAuth callback parameters uses
 * the OAuth provider in [flow], maps the OAuth response to a stored session, resolves the typed route principal, and
 * invokes [onSuccess].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.oauthCallback)
 *
 * @param flow OAuth 2.0 flow with typed session authentication.
 * @param path route path used for both the initial OAuth redirect and the callback.
 * @param onFailure handler invoked when session creation or principal resolution fails.
 * @param onSuccess handler invoked after the session and principal are stored.
 * @return the route that contains the OAuth callback.
 */
@ExperimentalKtorApi
public fun <S : Any, P : Any, C : SessionAuthenticatedContext<S, P>> Route.oauthCallback(
    flow: OAuth2SessionFlow<S, P, C>,
    path: String,
    onFailure: UnauthorizedHandler = { _ -> call.respond(HttpStatusCode.Unauthorized) },
    onSuccess: suspend context(C) RoutingContext.() -> Unit
): Route {
    val route = createChild(AuthenticationRouteSelector(listOf(flow.oauthScheme.name)))
    route.install(createOAuthBodyCachePlugin())
    flow.sessions.installSessionsPlugin(route)
    flow.oauthScheme.install(route, onUnauthorized = null)

    route.get(path) callback@{
        try {
            val token = call.attributes[flow.oauthScheme.principalKey]
            val session = flow.sessionCreator(this, token) ?: run {
                val error = AuthenticationFailedCause.Error("Failed to create OAuth session")
                return@callback onFailure(error)
            }
            call.sessions.set(flow.sessions, session)

            val principal = flow.principalResolver(this, session) ?: run {
                val error = AuthenticationFailedCause.Error("Failed to create OAuth principal")
                return@callback onFailure(error)
            }
            call.attributes.put(flow.sessions.sessionKey, session)
            flow.sessions.capture(call, principal)

            val sessionContext = DefaultSessionAuthenticatedContext(
                base = DefaultAuthenticatedContext(flow.sessions.principalKey),
                sessionKey = flow.sessions.sessionKey,
                sessionProviderName = flow.sessions.name
            )
            @OptIn(InternalAPI::class)
            with(receiver = checkNotNull(flow.config.contextFactory)(sessionContext)) {
                this@callback.onSuccess()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onFailure(AuthenticationFailedCause.Error(e.message ?: "Failed to create OAuth principal"))
            return@callback
        }
    }
    return route
}
