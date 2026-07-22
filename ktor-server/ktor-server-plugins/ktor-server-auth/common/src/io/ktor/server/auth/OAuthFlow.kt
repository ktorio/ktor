/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalKtorApi::class)

package io.ktor.server.auth

import io.ktor.client.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.server.util.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import kotlin.reflect.KClass

internal sealed class OAuth2FlowCallback {
    abstract val path: String

    class Basic(
        override val path: String,
        val successHandler: CallbackSuccessHandler
    ) : OAuth2FlowCallback()

    class Session<S : Any, P : Any>(
        override val path: String,
        val successHandler: SessionCallbackSuccessHandler<S, P>,
        val failureHandler: UnauthorizedHandler,
    ) : OAuth2FlowCallback()
}

/**
 * Configures the OAuth 2.0 authentication provider used by a typed [OAuth2Flow].
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
     * A required route path that starts the OAuth redirect when visited.
     *
     * Visiting this route triggers a redirect to the OAuth provider. The provider returns to the configured callback path.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.OAuthFlowConfigBase.loginUri)
     */
    public var loginPath: String? = null

    /**
     * Handles OAuth 2.0 errors such as authorization errors and token exchange failures.
     *
     * If the handler does not complete the call, authentication continues through the default challenge handling.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.OAuthFlowConfigBase.onUnauthorized)
     */
    public var onUnauthorized: UnauthorizedHandler = {}

    internal open fun validate(flowName: String) {
        requireNotNull(loginPath) {
            "OAuth flow '$flowName' requires a login path"
        }
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

internal typealias SessionCallbackSuccessHandler<S, P> = suspend context(SessionContext<S, P>, RequiredContext)
RoutingContext.() -> Unit

/**
 * Configures a typed OAuth 2.0 authorization-code flow.
 *
 * Used by [oauth2] to configure the OAuth provider, callback route, and login route.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.OAuthFlowConfig)
 */
@KtorDsl
@ExperimentalKtorApi
public class OAuth2FlowConfig internal constructor() : OAuthFlowConfigBase() {
    internal var callback: OAuth2FlowCallback.Basic? = null

    /**
     * Configures the OAuth callback route and success handler.
     *
     * The redirect URI sent to the OAuth provider is derived automatically from [path].
     * The initial OAuth redirect is triggered by visiting [loginPath], not this route.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.OAuthFlowConfig.callback)
     *
     * @param path route path that receives the provider callback with authorization code and state.
     * @param onSuccess handler invoked after OAuth token exchange succeeds. Receives the token response as its
     * parameter.
     */
    public fun callback(path: String, onSuccess: CallbackSuccessHandler) {
        callback = OAuth2FlowCallback.Basic(path, onSuccess)
    }

    override fun validate(flowName: String) {
        super.validate(flowName)
        requireNotNull(callback) {
            "OAuth flow '$flowName' requires a callback route. Set callback(\"/callback\") { ... }."
        }
    }
}

/**
 * Configures a typed OAuth 2.0 flow with session-backed route authentication.
 *
 * Used by [oauth2Session] to combine [OAuthFlowConfigBase] with session storage, session creation, and principal
 * resolution via [sessions].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.OAuthSessionFlowConfig)
 *
 * @param S the session type stored by the [Sessions] plugin.
 * @param P the principal type available in protected route handlers.
 */
@KtorDsl
@ExperimentalKtorApi
public class OAuthSessionFlowConfig<S : Any, P : Any> @PublishedApi internal constructor() : OAuthFlowConfigBase() {
    internal var callback: OAuth2FlowCallback.Session<S, P>? = null

    /**
     * Session configuration set by [sessions].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.OAuthSessionFlowConfig.sessionsConfig)
     */
    public var sessionsConfig: OAuth2SessionsConfig<S, P>? = null

    /**
     * Configures the OAuth callback route and success handler for a session-backed flow.
     *
     * The redirect URI sent to the OAuth provider is derived automatically from [path].
     * The initial OAuth redirect is triggered by visiting [loginPath], not this route.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.OAuthSessionFlowConfig.callback)
     *
     * @param path route path that receives the provider callback with authorization code and state.
     * @param onFailure handler invoked when session creation or principal resolution fails.
     * @param onSuccess handler invoked after the session and principal are stored. Runs in [SessionContext] with
     * [io.ktor.server.application.ApplicationCall.principal] and
     * [io.ktor.server.application.ApplicationCall.session] available.
     */
    public fun callback(
        path: String,
        onFailure: UnauthorizedHandler = { _ -> call.respond(HttpStatusCode.Unauthorized) },
        onSuccess: SessionCallbackSuccessHandler<S, P>,
    ) {
        callback = OAuth2FlowCallback.Session(path, onSuccess, onFailure)
    }

    /**
     * Configures session storage, session creation, and principal resolution for this OAuth session flow.
     *
     * Requires [OAuth2SessionsConfig.sessionCreator] and [OAuth2SessionsConfig.validate] inside the block.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.OAuthSessionFlowConfig.sessions)
     */
    public fun sessions(configure: OAuth2SessionsConfig<S, P>.() -> Unit) {
        sessionsConfig = OAuth2SessionsConfig<S, P>().apply(configure)
    }

    override fun validate(flowName: String) {
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
 * session value [S], and [validate] maps that session to the route principal [P]. Both are required.
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
     * Required when configuring [OAuthSessionFlowConfig.sessions].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.OAuth2SessionsConfig.sessionCreator)
     */
    public var sessionCreator: (suspend RoutingContext.(OAuthAccessTokenResponse.OAuth2) -> S?)? = null

    @OptIn(InternalAPI::class)
    internal open fun validate(flowName: String) {
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
 *
 * Used internally to bridge typed OAuth flows to Ktor's lower-level OAuth provider. Advanced integrations can use
 * [OAuth2Flow.from] to build flows from existing provider configuration.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.OAuthScheme)
 */
public typealias OAuthScheme = SimpleAuthenticationScheme<OAuthAccessTokenResponse.OAuth2>

/**
 * Typed OAuth 2.0 authorization-code flow.
 *
 * Created by [oauth2] and installed with [Route.install] or [Application.install].
 * Visiting [loginPath] triggers a redirect to the OAuth provider; the provider returns to the callback route.
 *
 * Unauthenticated requests to the callback path or [loginPath] redirect to the OAuth provider.
 *
 * For session-backed route protection, use [oauth2Session] instead. The two factory functions are alternatives:
 * [oauth2] handles login entirely in the callback handler, while [oauth2Session] exposes [OAuth2SessionFlow.session]
 * for use with [authenticateWith].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.OAuthFlow)
 */
@ExperimentalKtorApi
@SubclassOptInRequired
public open class OAuth2Flow internal constructor(
    public val name: String,
    internal val oauthScheme: OAuthScheme,
    internal val callback: OAuth2FlowCallback.Basic,
    internal val loginPath: String,
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
        public fun from(name: String, config: OAuth2FlowConfig): OAuth2Flow {
            config.validate(name)
            val oauthScheme = createOauthScheme(name, config)
            val callback = checkNotNull(config.callback)
            val loginPath = checkNotNull(config.loginPath)
            return OAuth2Flow(name, oauthScheme, callback, loginPath)
        }
    }
}

private fun createOauthScheme(name: String, config: OAuthFlowConfigBase): OAuthScheme {
    val providerName = "$name-oauth"
    val callback = when (config) {
        is OAuth2FlowConfig -> config.callback
        is OAuthSessionFlowConfig<*, *> -> config.callback
        else -> error("Unsupported OAuth flow config type: ${config::class.simpleName}")
    }
    require(callback != null) {
        "OAuth flow '$name' requires a callback route. Set callback(\"/callback\") { ... }."
    }
    @OptIn(InternalAPI::class)
    return AuthenticationScheme.from(
        provider = config.buildProvider(providerName, callback.path),
        onUnauthorized = null,
    )
}

/**
 * Typed OAuth 2.0 flow with session-backed route authentication.
 *
 * Created by [oauth2Session] and installed with [Route.install] or [Application.install].
 * The OAuth callback stores session values produced by [OAuth2SessionsConfig.sessionCreator] and resolves the route
 * principal with [OAuth2SessionsConfig.validate]. Application routes are protected with [authenticateWith] using
 * [session].
 *
 * Unauthenticated requests to the callback path or [OAuthFlowConfigBase.loginPath] redirect to the OAuth provider.
 * Routes protected with [session] require an existing session and respond with `401 Unauthorized` when it is missing.
 *
 * @param S the session type stored by the [Sessions] plugin.
 * @param P the principal type available in protected route handlers.
 * @property session typed session authentication scheme used with [authenticateWith].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.OAuth2SessionFlow)
 */
@ExperimentalKtorApi
public class OAuth2SessionFlow<S : Any, P : Any> internal constructor(
    public val name: String,
    internal val oauthScheme: OAuthScheme,
    public val session: SessionAuthenticationScheme<S, P>,
    config: OAuthSessionFlowConfig<S, P>,
) {
    internal val callback = checkNotNull(config.callback)
    internal val sessionCreator = checkNotNull(config.sessionsConfig?.sessionCreator)

    @OptIn(InternalAPI::class)
    internal val principalResolver = checkNotNull(config.sessionsConfig?.principalResolver)

    internal val loginPath = config.loginPath

    public companion object {
        /**
         * Creates a session-backed OAuth 2.0 flow from [OAuthSessionFlowConfig].
         *
         * This API is intended for integrations that need explicit session and principal type information.
         *
         * @param name flow name used to derive authentication provider names.
         * @param config OAuth session flow configuration.
         * @param principalType route principal type.
         * @return OAuth 2.0 flow with typed session authentication.
         */
        @InternalAPI
        public fun <S : Any, P : Any> from(
            name: String,
            config: OAuthSessionFlowConfig<S, P>,
            principalType: KClass<P>,
            sessionTypeInfo: TypeInfo
        ): OAuth2SessionFlow<S, P> {
            config.validate(name)
            val oauthScheme = createOauthScheme(name, config)
            val sessionsConfig = requireNotNull(config.sessionsConfig) {
                "OAuth session flow '$name' requires sessions { ... } configuration"
            }
            sessionsConfig.validate(name)
            val providerName = sessionsConfig.name ?: (name.uppercase() + "_SESSION")
            val sessionScheme =
                SessionAuthenticationScheme.from(providerName, principalType, sessionTypeInfo, sessionsConfig)
            return OAuth2SessionFlow(name, oauthScheme, sessionScheme, config)
        }
    }
}

/**
 * Creates a typed OAuth 2.0 authorization-code flow.
 *
 * Visiting [OAuthFlowConfigBase.loginPath] triggers a redirect to the OAuth provider. The provider returns to the
 * callback route configured with [OAuth2FlowConfig.callback], where the success handler receives the token response.
 * This flow does not provide built-in route protection; handle post-login logic in the callback handler.
 *
 * Routes installed by [Route.install] under the OAuth authentication layer—including the callback path and
 * [OAuthFlowConfigBase.loginPath]—redirect unauthenticated requests to the OAuth provider. This is the default OAuth
 * challenge when no authorization code is present.
 *
 * Configure the callback route with [OAuth2FlowConfig.callback] and install the flow with [Route.install] or
 * [Application.install].
 *
 * ```kotlin
 * val googleOAuth = oauth2("google") {
 *     client = HttpClient()
 *     settings = OAuthServerSettings.OAuth2ServerSettings(
 *         name = "google",
 *         authorizeUrl = "https://accounts.google.com/o/oauth2/auth",
 *         accessTokenUrl = "https://oauth2.googleapis.com/token",
 *         clientId = "...",
 *         clientSecret = "...",
 *         requestMethod = HttpMethod.Post,
 *     )
 *     loginPath = "/login"
 *     callback("/callback") { token ->
 *         call.respondRedirect("/home")
 *     }
 * }
 *
 * routing {
 *     install(googleOAuth)
 * }
 * ```
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.oauth2Flow)
 *
 * @param name name that identifies the OAuth flow.
 * @param configure configures the underlying Ktor OAuth provider and callback route.
 * @return an OAuth 2.0 flow for route installation.
 */
@ExperimentalKtorApi
@OptIn(InternalAPI::class)
public fun oauth2(name: String, configure: OAuth2FlowConfig.() -> Unit): OAuth2Flow =
    OAuth2Flow.from(name, OAuth2FlowConfig().apply(configure))

/**
 * Creates a typed OAuth 2.0 authorization-code flow with session-backed route authentication.
 *
 * The OAuth callback stores a session value and principal. Protected routes use [OAuth2SessionFlow.session] with
 * [authenticateWith], not the OAuth scheme directly.
 *
 * Routes installed by [Route.install] under the OAuth authentication layer—including the callback path and
 * [OAuthFlowConfigBase.loginPath]—redirect unauthenticated requests to the OAuth provider.
 *
 * Routes protected with [authenticateWith] using [OAuth2SessionFlow.session] respond with `401 Unauthorized` when no
 * valid session exists. They do not automatically start the OAuth flow; direct users to [OAuthFlowConfigBase.loginPath]
 * to sign in, or customize [TypedSessionAuthConfig.onUnauthorized] to redirect there.
 *
 * Type parameters are declared as `<P, S>` where [P] is the route principal type and [S] is the stored session type.
 *
 * ```kotlin
 * data class User(val id: String)
 * data class UserSession(val accessToken: String)
 *
 * val googleAuth = oauth2Session<User, UserSession>("google") {
 *     client = HttpClient()
 *     settings = OAuthServerSettings.OAuth2ServerSettings(
 *         name = "google",
 *         authorizeUrl = "https://accounts.google.com/o/oauth2/auth",
 *         accessTokenUrl = "https://oauth2.googleapis.com/token",
 *         clientId = "...",
 *         clientSecret = "...",
 *         requestMethod = HttpMethod.Post,
 *     )
 *     loginPath = "/login"
 *     callback("/callback") {
 *         call.respondRedirect("/home")
 *     }
 *     sessions {
 *         transport = SessionTransportType.Cookie()
 *         sessionCreator = { token -> UserSession(token.accessToken) }
 *         validate { session -> User(id = lookupUserId(session.accessToken)) }
 *     }
 * }
 *
 * routing {
 *     install(googleAuth)
 *     authenticateWith(googleAuth.session) {
 *         get("/profile") {
 *             call.respondText(call.principal.id)
 *         }
 *     }
 * }
 * ```
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.oauth2SessionFlow)
 *
 * @param P the principal type available in protected route handlers.
 * @param S the session type stored by the [Sessions] plugin.
 * @param name name that identifies the OAuth flow.
 * @param configure configures the OAuth provider, callback route, and session authentication.
 * @return OAuth 2.0 flow with typed session authentication.
 */
@ExperimentalKtorApi
@OptIn(InternalAPI::class)
public inline fun <reified P : Any, reified S : Any> oauth2Session(
    name: String,
    configure: OAuthSessionFlowConfig<S, P>.() -> Unit,
): OAuth2SessionFlow<S, P> {
    val config = OAuthSessionFlowConfig<S, P>().apply(configure)
    return OAuth2SessionFlow.from(name, config, principalType = P::class, sessionTypeInfo = typeInfo<S>())
}

/**
 * Installs an [OAuth2Flow] into the routing tree.
 *
 * Creates the configured callback route and login route at [OAuth2Flow.loginPath]. Unauthenticated requests to either
 * route redirect to the OAuth provider.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.install)
 *
 * @param flow OAuth 2.0 flow to install.
 */
@ExperimentalKtorApi
public fun Route.install(flow: OAuth2Flow) {
    val scheme = flow.oauthScheme
    val route = installOAuthRoute(scheme)
    val plugin = scheme.createPlugin(isOptional = false, onUnauthorized = null)
    route.install(plugin)

    context(flow.oauthScheme.createContext(), RequiredContext) {
        val callbackHandler: RoutingHandler = {
            val response = call.principal
            flow.callback.successHandler(this, response)
        }
        route.get(flow.callback.path, callbackHandler)
        route.post(flow.callback.path, callbackHandler)
    }

    route.installOAuthLoginRoute(scheme, flow.loginPath)
}

/**
 * Installs an [OAuth2SessionFlow] into the routing tree.
 *
 * Creates the configured callback route and login route at [OAuthFlowConfigBase.loginPath].
 * Also installs the [Sessions] plugin for [OAuth2SessionFlow.session].
 *
 * Unauthenticated requests to the callback path or [OAuthFlowConfigBase.loginPath] redirect to the OAuth provider.
 * Routes protected later with [authenticateWith] using [OAuth2SessionFlow.session] are not covered by this redirect;
 * they respond with `401 Unauthorized` when no valid session exists.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.install)
 *
 * @param oauth OAuth 2.0 flow with typed session authentication.
 */
@ExperimentalKtorApi
public fun <S : Any, P : Any> Route.install(oauth: OAuth2SessionFlow<S, P>) {
    val route = installOAuthRoute(oauth.oauthScheme)
    install(oauth.session)
    val plugin = oauth.oauthScheme.createPlugin(
        isOptional = false,
        onUnauthorized = null // keep default OAuth redirect
    )
    route.install(plugin)

    val callback = oauth.callback
    val callbackHandler: RoutingHandler = callback@{
        try {
            val token = call.attributes[oauth.oauthScheme.principalKey]
            val session = oauth.sessionCreator(this, token) ?: run {
                val error = AuthenticationFailedCause.Error("Failed to create OAuth session")
                return@callback callback.failureHandler(this, error)
            }

            val principal = oauth.principalResolver(this, session) ?: run {
                val error = AuthenticationFailedCause.Error("Failed to create OAuth principal")
                return@callback callback.failureHandler(this, error)
            }
            val sessionsScheme = oauth.session
            sessionsScheme.setSession(session)
            call.attributes.put(sessionsScheme.sessionKey, session)
            call.attributes.put(sessionsScheme.principalKey, principal)

            context(sessionsScheme.createContext(), RequiredContext) {
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
    route.get(callback.path, callbackHandler)
    route.post(callback.path, callbackHandler)

    oauth.loginPath?.let { loginPath ->
        route.installOAuthLoginRoute(scheme = oauth.oauthScheme, path = loginPath)
    }
}

/**
 * Installs an [OAuth2Flow] at the application routing root.
 *
 * Equivalent to `application.routing { install(flow) }`.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.install)
 */
@ExperimentalKtorApi
public fun Application.install(flow: OAuth2Flow) {
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
public fun Application.install(oauth: OAuth2SessionFlow<*, *>) {
    routing { install(oauth) }
}

private fun Route.installOAuthRoute(scheme: OAuthScheme): Route {
    val route = createChild(AuthenticationRouteSelector(listOf(scheme.name)))
    route.install(createOAuthBodyCachePlugin())
    scheme.preinstallAt(route)
    return route
}

private fun Route.installOAuthLoginRoute(scheme: OAuthScheme, path: String) {
    authenticateWith(scheme) {
        get(path) { }
    }
}
