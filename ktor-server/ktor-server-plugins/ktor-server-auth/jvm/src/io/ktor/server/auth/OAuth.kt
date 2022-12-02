/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.util.*
import io.ktor.util.logging.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.errors.*
import kotlinx.coroutines.*

private val Logger: Logger = KtorSimpleLogger("io.ktor.auth.oauth2")

/**
 * OAuth versions used in configuration.
 */
@Suppress("KDocMissingDocumentation")
public enum class OAuthVersion {
    V10a, V20
}

/**
 * OAuth server settings.
 * @property name configuration name
 * @property version OAuth version (1a or 2)
 */
public sealed class OAuthServerSettings(public val name: String, public val version: OAuthVersion) {
    /**
     * OAuth1a server settings
     * @property requestTokenUrl OAuth server token request URL
     * @property authorizeUrl OAuth server authorization page URL
     * @property accessTokenUrl OAuth server access token request URL
     * @property consumerKey consumer key parameter (provided by OAuth server vendor)
     * @property consumerSecret a secret key parameter (provided by OAuth server vendor)
     */
    public class OAuth1aServerSettings(
        name: String,
        public val requestTokenUrl: String,
        public val authorizeUrl: String,
        public val accessTokenUrl: String,

        public val consumerKey: String,
        public val consumerSecret: String,

        public val accessTokenInterceptor: HttpRequestBuilder.() -> Unit = {}
    ) : OAuthServerSettings(name, OAuthVersion.V10a)

    /**
     * OAuth2 server settings
     * @property authorizeUrl OAuth server authorization page URL
     * @property accessTokenUrl OAuth server access token request URL
     * @property requestMethod HTTP request method to be used to acquire access token (see vendors documentation)
     * @property clientId client id parameter (provided by OAuth server vendor)
     * @property clientSecret client secret parameter (provided by OAuth server vendor)
     * @property defaultScopes OAuth scopes used by default
     * @property accessTokenRequiresBasicAuth to send BASIC auth header when an access token is requested
     * @property passParamsInURL whether to pass request parameters in POST requests in URL instead of body.
     * @property nonceManager to be used to produce and verify nonce values
     * @property authorizeUrlInterceptor an interceptor function to customize authorization URL
     * @property extraAuthParameters extra parameters to send during authentication
     * @property extraTokenParameters extra parameters to send with getting access token call
     * @property accessTokenInterceptor an interceptor function to customize access token request
     */
    public class OAuth2ServerSettings(
        name: String,
        public val authorizeUrl: String,
        public val accessTokenUrl: String,
        public val requestMethod: HttpMethod = HttpMethod.Get,

        public val clientId: String,
        public val clientSecret: String,
        public val defaultScopes: List<String> = emptyList(),
        public val accessTokenRequiresBasicAuth: Boolean = false,

        public val nonceManager: NonceManager = GenerateOnlyNonceManager,

        public val authorizeUrlInterceptor: URLBuilder.() -> Unit = {},
        public val passParamsInURL: Boolean = false,
        public val extraAuthParameters: List<Pair<String, String>> = emptyList(),
        public val extraTokenParameters: List<Pair<String, String>> = emptyList(),
        public val accessTokenInterceptor: HttpRequestBuilder.() -> Unit = {},
        public val onStateCreated: suspend (call: ApplicationCall, state: String) -> Unit = { _, _ -> }
    ) : OAuthServerSettings(name, OAuthVersion.V20) {

        @Deprecated("This constructor will be removed", level = DeprecationLevel.HIDDEN)
        public constructor(
            name: String,
            authorizeUrl: String,
            accessTokenUrl: String,
            requestMethod: HttpMethod = HttpMethod.Get,
            clientId: String,
            clientSecret: String,
            defaultScopes: List<String> = emptyList(),
            accessTokenRequiresBasicAuth: Boolean = false,
            nonceManager: NonceManager = GenerateOnlyNonceManager,
            authorizeUrlInterceptor: URLBuilder.() -> Unit = {},
            passParamsInURL: Boolean = false,
            accessTokenInterceptor: HttpRequestBuilder.() -> Unit = {}
        ) : this(
            name,
            authorizeUrl,
            accessTokenUrl,
            requestMethod,
            clientId,
            clientSecret,
            defaultScopes,
            accessTokenRequiresBasicAuth,
            nonceManager,
            authorizeUrlInterceptor,
            passParamsInURL,
            emptyList(),
            emptyList(),
            accessTokenInterceptor
        )

        @Deprecated("This constructor will be removed", level = DeprecationLevel.HIDDEN)
        public constructor(
            name: String,
            authorizeUrl: String,
            accessTokenUrl: String,
            requestMethod: HttpMethod = HttpMethod.Get,
            clientId: String,
            clientSecret: String,
            defaultScopes: List<String> = emptyList(),
            accessTokenRequiresBasicAuth: Boolean = false,
            nonceManager: NonceManager = GenerateOnlyNonceManager,
            authorizeUrlInterceptor: URLBuilder.() -> Unit = {},
            passParamsInURL: Boolean = false,
            extraAuthParameters: List<Pair<String, String>> = emptyList(),
            extraTokenParameters: List<Pair<String, String>> = emptyList(),
            accessTokenInterceptor: HttpRequestBuilder.() -> Unit = {},
        ) : this(
            name,
            authorizeUrl,
            accessTokenUrl,
            requestMethod,
            clientId,
            clientSecret,
            defaultScopes,
            accessTokenRequiresBasicAuth,
            nonceManager,
            authorizeUrlInterceptor,
            passParamsInURL,
            extraAuthParameters,
            extraTokenParameters,
            accessTokenInterceptor,
            { _, _ -> }
        )
    }
}

/**
 * OAuth callback parameters.
 */
public sealed class OAuthCallback {
    /**
     * An OAuth1a token pair callback parameters.
     * @property token OAuth1a token
     * @property tokenSecret OAuth1a token secret
     */
    public data class TokenPair(val token: String, val tokenSecret: String) : OAuthCallback()

    /**
     * An OAuth2 token callback parameter.
     * @property token OAuth2 token provided by server
     * @property state passed from a client (ktor server) during authorization startup
     */
    public data class TokenSingle(val token: String, val state: String) : OAuthCallback()
}

/**
 * An OAuth access token acquired from the server.
 */
public sealed class OAuthAccessTokenResponse : Principal {
    /**
     * OAuth1a access token acquired from the server
     * @property token itself
     * @property tokenSecret token secret to be used with [token]
     * @property extraParameters contains additional parameters provided by the server
     */
    public data class OAuth1a(
        val token: String,
        val tokenSecret: String,
        val extraParameters: Parameters = Parameters.Empty
    ) : OAuthAccessTokenResponse()

    /**
     * OAuth2 access token acquired from the server
     * @property accessToken access token from server
     * @property tokenType OAuth2 token type (usually Bearer)
     * @property expiresIn token expiration timestamp
     * @property refreshToken to be used to refresh access token after expiration
     * @property state generated state used for the OAuth procedure
     * @property extraParameters contains additional parameters provided by the server
     */
    public data class OAuth2(
        val accessToken: String,
        val tokenType: String,
        val expiresIn: Long,
        val refreshToken: String?,
        val extraParameters: Parameters = Parameters.Empty,
    ) : OAuthAccessTokenResponse() {

        public var state: String? = null
            private set

        public constructor(
            accessToken: String,
            tokenType: String,
            expiresIn: Long,
            refreshToken: String?,
            extraParameters: Parameters = Parameters.Empty,
            state: String? = null
        ) : this(accessToken, tokenType, expiresIn, refreshToken, extraParameters) {
            this.state = state
        }
    }
}

/**
 * OAuth grant types constants.
 */
@Suppress("KDocMissingDocumentation")
public object OAuthGrantTypes {
    public const val AuthorizationCode: String = "authorization_code"
    public const val Password: String = "password"
}

/**
 * Installs both OAuth1a and OAuth2 authentication helpers that redirects to an OAuth server authorization page
 * and handles corresponding callbacks.
 */
@Suppress("unused")
@Deprecated("Install and configure OAuth instead.", level = DeprecationLevel.ERROR)
public suspend fun PipelineContext<Unit, ApplicationCall>.oauth(
    client: HttpClient,
    dispatcher: CoroutineDispatcher,
    providerLookup: ApplicationCall.() -> OAuthServerSettings?,
    urlProvider: ApplicationCall.(OAuthServerSettings) -> String
) {
    oauth1a(client, dispatcher, providerLookup, urlProvider)
    oauth2(client, dispatcher, providerLookup, urlProvider)
}

/**
 * Responds with OAuth redirect.
 */
@Deprecated("Install and configure OAuth instead.", level = DeprecationLevel.ERROR)
public suspend fun PipelineContext<Unit, ApplicationCall>.oauthRespondRedirect(
    client: HttpClient,
    dispatcher: CoroutineDispatcher,
    provider: OAuthServerSettings,
    callbackUrl: String
) {
    when (provider) {
        is OAuthServerSettings.OAuth1aServerSettings -> {
            withContext(dispatcher) {
                val requestToken = simpleOAuth1aStep1(client, provider, callbackUrl)
                call.redirectAuthenticateOAuth1a(provider, requestToken)
            }
        }

        is OAuthServerSettings.OAuth2ServerSettings -> {
            call.redirectAuthenticateOAuth2(
                provider,
                callbackUrl,
                provider.nonceManager.newNonce(),
                scopes = provider.defaultScopes,
                interceptor = provider.authorizeUrlInterceptor
            )
        }
    }
}

/**
 * Handles an OAuth callback. Usually it leads to requesting an access token.
 */
@Deprecated("Install and configure OAuth instead.", level = DeprecationLevel.ERROR)
public suspend fun PipelineContext<Unit, ApplicationCall>.oauthHandleCallback(
    client: HttpClient,
    dispatcher: CoroutineDispatcher,
    provider: OAuthServerSettings,
    callbackUrl: String,
    loginPageUrl: String,
    block: suspend (OAuthAccessTokenResponse) -> Unit
) {
    @Suppress("DEPRECATION_ERROR")
    oauthHandleCallback(client, dispatcher, provider, callbackUrl, loginPageUrl, {}, block)
}

/**
 * Handles an OAuth callback.
 */
@Deprecated(
    "Specifying an extra configuration function will be deprecated. " +
        "Please provide it via OAuthServerSettings.",
    level = DeprecationLevel.ERROR
)
public suspend fun PipelineContext<Unit, ApplicationCall>.oauthHandleCallback(
    client: HttpClient,
    dispatcher: CoroutineDispatcher,
    provider: OAuthServerSettings,
    callbackUrl: String,
    loginPageUrl: String,
    configure: HttpRequestBuilder.() -> Unit = {},
    block: suspend (OAuthAccessTokenResponse) -> Unit
) {
    when (provider) {
        is OAuthServerSettings.OAuth1aServerSettings -> {
            val tokens = call.oauth1aHandleCallback()
            if (tokens == null) {
                call.respondRedirect(loginPageUrl)
            } else {
                withContext(dispatcher) {
                    try {
                        val accessToken = requestOAuth1aAccessToken(client, provider, tokens)
                        block(accessToken)
                    } catch (ioe: IOException) {
                        call.oauthHandleFail(loginPageUrl)
                    }
                }
            }
        }

        is OAuthServerSettings.OAuth2ServerSettings -> {
            val code = call.oauth2HandleCallback()
            if (code == null) {
                call.respondRedirect(loginPageUrl)
            } else {
                withContext(dispatcher) {
                    try {
                        val accessToken = oauth2RequestAccessToken(
                            client,
                            provider,
                            callbackUrl,
                            code,
                            configure
                        )

                        block(accessToken)
                    } catch (cause: OAuth2Exception.InvalidGrant) {
                        Logger.trace("Redirected to the login page due to invalid_grant error: ${cause.message}")
                        call.oauthHandleFail(loginPageUrl)
                    } catch (ioe: IOException) {
                        Logger.trace("Redirected to the login page due to IO error", ioe)
                        call.oauthHandleFail(loginPageUrl)
                    }
                }
            }
        }
    }
}

internal suspend fun ApplicationCall.oauthHandleFail(redirectUrl: String) = respondRedirect(redirectUrl)

internal fun String.appendUrlParameters(parameters: String) =
    when {
        parameters.isEmpty() -> ""
        this.endsWith("?") -> ""
        "?" in this -> "&"
        else -> "?"
    }.let { separator -> "$this$separator$parameters" }
