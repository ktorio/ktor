/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.typesafe

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.utils.io.*
import kotlin.reflect.KClass

/**
 * Configures a compound OAuth and Session authentication scheme.
 *
 * The OAuth provider authenticates the callback request, [sessionFactory] maps the OAuth result to a session value,
 * and the session provider later protects routes created with [authenticateWith].
 *
 * Challenge strategy: protected routes use a route-level `onUnauthorized` first, then [onUnauthorized]. If neither is
 * configured, Session authentication responds with its default `401 Unauthorized` challenge. The OAuth callback route
 * uses the OAuth challenge configured in [oauth] to start the redirect flow.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.OAuthWithSessionConfig)
 *
 * @param S the session type stored by the [Sessions] plugin.
 * @param P the principal type available in protected route handlers.
 */
@ExperimentalKtorApi
@KtorDsl
public class OAuthWithSessionConfig<S : Any, P : Any> @PublishedApi internal constructor() {
    /**
     * Human-readable description of this compound authentication scheme.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.OAuthWithSessionConfig.description)
     */
    public var description: String? = null

    internal var oauthConfigBlock: (OAuthAuthenticationProvider.Config.() -> Unit)? = null

    internal var sessionConfigBlock: (SessionsConfig.() -> Unit)? = null

    internal var sessionFactory: (suspend (OAuthAccessTokenResponse) -> S?)? = null

    /**
     * Default handler for session authentication failures on protected routes.
     *
     * A route-level `onUnauthorized` passed to [authenticateWith] overrides this handler. If both are `null`, Session
     * authentication sends the default challenge described by this configuration.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.OAuthWithSessionConfig.onUnauthorized)
     */
    public var onUnauthorized: UnauthorizedHandler? = null

    /**
     * Configures the OAuth authentication provider used by [oauthCallback].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.OAuthWithSessionConfig.oauth)
     *
     * @param configure configures the OAuth flow.
     */
    public fun oauth(configure: OAuthAuthenticationProvider.Config.() -> Unit) {
        oauthConfigBlock = configure
    }

    /**
     * Configures [Sessions] storage for the session created after OAuth succeeds.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.OAuthWithSessionConfig.session)
     *
     * @param configure configures the [Sessions] plugin.
     */
    public fun session(configure: SessionsConfig.() -> Unit) {
        sessionConfigBlock = configure
    }

    /**
     * Maps an [OAuthAccessTokenResponse] to the session value stored by [oauthCallback].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.OAuthWithSessionConfig.sessionFactory)
     *
     * @param factory creates a session value from the OAuth token response.
     */
    public fun sessionFactory(factory: suspend (OAuthAccessTokenResponse) -> S) {
        sessionFactory = factory
    }
}

/**
 * Compound typed authentication scheme that combines OAuth redirect flow with session-based persistence.
 *
 * Create the callback route with [oauthCallback], then protect routes with [authenticateWith].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.OAuthWithSessionScheme)
 *
 * @param S the session type stored by the [Sessions] plugin.
 * @param P the principal type available in protected route handlers.
 * @property name name that identifies the OAuth flow. Protected session routes use a derived scheme name.
 */
@ExperimentalKtorApi
public class OAuthWithSessionScheme<S : Any, P : Any> @PublishedApi internal constructor(
    public val name: String,
    internal val oauthScheme: DefaultAuthScheme<
        OAuthAccessTokenResponse,
        DefaultAuthenticatedContext<OAuthAccessTokenResponse>
        >,
    internal val sessionScheme: DefaultAuthScheme<P, DefaultAuthenticatedContext<P>>,
    internal val sessionConfig: SessionsConfig.() -> Unit,
    internal val sessionFactory: suspend (OAuthAccessTokenResponse) -> S?,
    internal var principalFactory: suspend (S) -> P?,
    internal val sessionType: KClass<S>,
) {

    internal fun createAuthenticatedContext(route: Route): DefaultAuthenticatedContext<P> =
        sessionScheme.createAuthenticatedContext(route)

    public companion object {
        @PublishedApi
        internal fun <S : Any, P : Any> from(
            name: String,
            config: OAuthWithSessionConfig<S, P>,
            sessionType: KClass<S>,
            principalType: KClass<P>,
            principalFactory: suspend (S) -> P?
        ): OAuthWithSessionScheme<S, P> {
            val oauthConfigure = requireNotNull(config.oauthConfigBlock) {
                "OAuth configuration is required. Call oauth { } inside oauthWithSession."
            }
            val sessionConfig = requireNotNull(config.sessionConfigBlock) {
                "Session configuration is required. Call session { cookie<S>(\"name\") } inside oauthWithSession."
            }
            val sessionFactory = requireNotNull(config.sessionFactory) {
                "sessionFactory is required. Call sessionFactory { token -> ... } inside oauthWithSession."
            }

            val oauthProviderConfig = OAuthAuthenticationProvider.Config(name, config.description)
            oauthProviderConfig.oauthConfigure()
            val oauthScheme = DefaultAuthScheme(
                name = name,
                principalType = OAuthAccessTokenResponse::class,
                provider = oauthProviderConfig.build(),
                onUnauthorized = null,
            ) { config -> DefaultAuthenticatedContext(config.principalKey) }

            val sessionProviderName = "$name-session"
            val sessionProviderConfig = SessionAuthenticationProvider.Config(
                sessionProviderName,
                config.description,
                sessionType
            )
            sessionProviderConfig.validate { principalFactory(it) }

            val sessionScheme = DefaultAuthScheme(
                name = sessionProviderName,
                principalType = principalType,
                provider = sessionProviderConfig.buildProvider(),
                onUnauthorized = config.onUnauthorized,
            ) { config -> DefaultAuthenticatedContext(config.principalKey) }

            return OAuthWithSessionScheme(
                name,
                oauthScheme,
                sessionScheme,
                sessionConfig,
                sessionFactory,
                principalFactory,
                sessionType
            )
        }
    }
}

/**
 * Creates a compound OAuth and Session authentication scheme with a separate route principal type.
 *
 * [principalFactory] maps the stored session [S] to the principal [P] exposed inside [authenticateWith] routes.
 *
 * ```kotlin
 * val auth = oauthWithSession<UserSession, UserPrincipal>(
 *     name = "oauth",
 *     principalMapper = { session -> loadUserByToken(session.accessToken) }
 * ) {
 *     oauth { /* configure OAuth provider */ }
 *     session { cookie<UserSession>("user_session") }
 *     sessionFactory { token ->
 *         val oauth2 = token as OAuthAccessTokenResponse.OAuth2
 *         UserSession(oauth2.accessToken)
 *     }
 * }
 *
 * routing {
 *     oauthCallback(auth, path = "/callback")
 *     authenticateWith(auth) {
 *         get("/me") { call.respondText(principal.name) }
 *     }
 * }
 * ```
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.oauthWithSession)
 *
 * @param name name that identifies the OAuth-with-session scheme.
 * @param principalFactory maps a session value to the route principal.
 * @param configure configures OAuth, session storage, and session creation.
 * @return a compound scheme for [oauthCallback] and [authenticateWith].
 */
@ExperimentalKtorApi
public inline fun <reified S : Any, reified P : Any> oauthWithSession(
    name: String,
    noinline principalFactory: suspend (S) -> P?,
    configure: OAuthWithSessionConfig<S, P>.() -> Unit
): OAuthWithSessionScheme<S, P> {
    val config = OAuthWithSessionConfig<S, P>().apply(configure)
    return OAuthWithSessionScheme.from(name, config, sessionType = S::class, principalType = P::class, principalFactory)
}

/**
 * Creates a compound OAuth and Session authentication scheme where the session is also the route principal.
 *
 * Use this overload when the session type is the same value you want to expose as [principal] in protected routes.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.oauthWithSession)
 *
 * @param name name that identifies the OAuth-with-session scheme.
 * @param configure configures OAuth, session storage, and session creation.
 * @return a compound scheme for [oauthCallback] and [authenticateWith].
 */
@ExperimentalKtorApi
public inline fun <reified P : Any> oauthWithSession(
    name: String,
    configure: OAuthWithSessionConfig<P, P>.() -> Unit,
): OAuthWithSessionScheme<P, P> {
    val config = OAuthWithSessionConfig<P, P>().apply(configure)
    return OAuthWithSessionScheme.from(
        name = name,
        config = config,
        sessionType = P::class,
        principalType = P::class,
        principalFactory = { it }
    )
}

/**
 * Handles a completed OAuth callback.
 *
 * Inside the handler, use [principal] to access the route principal and [session] to access the session value stored
 * by [oauthCallback].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.OAuthCallbackHandler)
 *
 * @param S the session type stored by the callback.
 * @param P the principal type exposed to protected routes.
 */
@OptIn(ExperimentalKtorApi::class)
public typealias OAuthCallbackHandler<S, P> = suspend context(OAuthCallbackContext<S, P>)
RoutingContext.() -> Unit

/**
 * Provides typed data created by a successful OAuth callback.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.OAuthCallbackContext)
 *
 * @param S the session type stored by the callback.
 * @param P the principal type exposed to protected routes.
 * @property session session value stored by the callback.
 */
@ExperimentalKtorApi
@KtorDsl
public class OAuthCallbackContext<S : Any, P : Any> internal constructor(
    internal val session: S,
    internal val principal: P,
)

/**
 * Session value stored by the current OAuth callback.
 *
 * The property is available inside [oauthCallback] success handlers.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.session)
 */
@ExperimentalKtorApi
context(callback: OAuthCallbackContext<S, P>)
public val <S : Any, P : Any> session: S
    get() = callback.session

/**
 * Principal value stored by the current OAuth callback.
 *
 * The property is available inside [oauthCallback] success handlers.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.session)
 */
@ExperimentalKtorApi
context(callback: OAuthCallbackContext<S, P>)
public val <S : Any, P : Any> principal: P
    get() = callback.principal

/**
 * Creates the OAuth callback route and stores a session after a successful callback.
 *
 * Calling [path] before OAuth completes starts the OAuth challenge. Calling [path] with OAuth callback parameters uses
 * the OAuth part of [scheme], maps the returned [OAuthAccessTokenResponse] to a session value, stores it, and invokes
 * [onSuccess] with [OAuthCallbackContext].
 *
 * ```kotlin
 * oauthCallback(auth, path = "/callback") {
 *     call.respondText("${session.accessToken}:${principal.name}")
 * }
 * ```
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.oauthCallback)
 *
 * @param scheme compound OAuth and Session scheme.
 * @param path route path used for both the initial OAuth redirect and the callback.
 * @param onFailure handler invoked when session creation or principal extraction fails.
 * @param onSuccess handler invoked after the session is stored.
 * @return the route that contains the OAuth callback.
 */
@ExperimentalKtorApi
public fun <S : Any, P : Any> Route.oauthCallback(
    scheme: OAuthWithSessionScheme<S, P>,
    path: String,
    onFailure: UnauthorizedHandler = { call, _ -> call.respond(HttpStatusCode.Unauthorized) },
    onSuccess: OAuthCallbackHandler<S, P>
): Route {
    application.registerSchemeIfNeeded(scheme.oauthScheme)
    install(Sessions, scheme.sessionConfig)

    val route = createChild(AuthenticationRouteSelector(listOf(scheme.oauthScheme.name)))
    route.install(createOAuthBodyCachePlugin())
    scheme.oauthScheme.install(route, onUnauthorized = null)

    route.get(path) callback@{
        try {
            val token = call.attributes[scheme.oauthScheme.principalKey]

            val session = scheme.sessionFactory(token)
                ?: return@callback onFailure(call, AuthenticationFailedCause.Error("Failed to create oauth session"))
            val principal = scheme.principalFactory(session)
                ?: return@callback onFailure(call, AuthenticationFailedCause.Error("Failed to create oauth principal"))

            call.sessions.set(session, scheme.sessionType)

            val callbackContext = OAuthCallbackContext(session, principal)
            with(callbackContext) {
                this@callback.onSuccess()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onFailure(call, AuthenticationFailedCause.Error(e.message ?: "Failed to create oauth principal"))
            return@callback
        }
    }

    return route
}
