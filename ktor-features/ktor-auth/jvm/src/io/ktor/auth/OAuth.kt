/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.auth

import io.ktor.application.*
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.util.*
import io.ktor.util.pipeline.*
import kotlinx.coroutines.*
import org.slf4j.*
import java.io.*

private val Logger: Logger = LoggerFactory.getLogger("io.ktor.auth.oauth2")

/**
 * OAuth versions used in configuration
 */
@Suppress("KDocMissingDocumentation")
public enum class OAuthVersion {
    V10a, V20
}

/**
 * Provides states for OAuth2. State could be just a random number (nonce) or could contain additional form fields or
 * a signature. It is important that it should be a way to verify state. So all states need to be saved somehow or
 * a state need to be a signed set of parameters that could be verified later
 */
@Deprecated("Use NonceManager instead", level = DeprecationLevel.ERROR)
public interface OAuth2StateProvider {
    /**
     * Generates a new state for given [call]
     */
    public suspend fun getState(call: ApplicationCall): String

    /**
     * Verifies [state] and throws exceptions if it's not valid
     */
    public suspend fun verifyState(state: String)
}

/**
 * The default state provider that does generate random nonce and don't keep them
 */
@Suppress("DEPRECATION_ERROR")
@Deprecated("Use NonceManager instead", level = DeprecationLevel.HIDDEN)
public object DefaultOAuth2StateProvider : OAuth2StateProvider {
    override suspend fun getState(call: ApplicationCall): String {
        TODO("This is no longer supported")
    }

    override suspend fun verifyState(state: String) {
        TODO("This is no longer supported")
    }
}

/**
 * Represents OAuth server settings
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
    ) : OAuthServerSettings(name, OAuthVersion.V10a) {
        @Suppress("unused")
        @Deprecated("Binary compatibility", level = DeprecationLevel.HIDDEN)
        public constructor(
            name: String,
            requestTokenUrl: String,
            authorizeUrl: String,
            accessTokenUrl: String,

            consumerKey: String,
            consumerSecret: String
        ) : this(name, requestTokenUrl, authorizeUrl, accessTokenUrl, consumerKey, consumerSecret, {})
    }

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
        public val accessTokenInterceptor: HttpRequestBuilder.() -> Unit = {}
    ) : OAuthServerSettings(name, OAuthVersion.V20) {
        @Deprecated("Binary compatibility", level = DeprecationLevel.HIDDEN)
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
            {}
        )
    }
}

/**
 * OAauth callback parameters
 */
public sealed class OAuthCallback {
    /**
     * An OAuth1a token pair callback parameters
     * @property token OAuth1a token
     * @property tokenSecret OAuth1a token secret
     */
    public data class TokenPair(val token: String, val tokenSecret: String) : OAuthCallback()

    /**
     * OAuth2 token callback parameter
     * @property token OAuth2 token provided by server
     * @property state passed from a client (ktor server) during authorization startup
     */
    public data class TokenSingle(val token: String, val state: String) : OAuthCallback()
}

/**
 * OAuth access token acquired from the server
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
     * @property extraParameters contains additional parameters provided by the server
     */
    public data class OAuth2(
        val accessToken: String,
        val tokenType: String,
        val expiresIn: Long,
        val refreshToken: String?,
        val extraParameters: Parameters = Parameters.Empty
    ) : OAuthAccessTokenResponse()
}

/**
 * OAuth grant types constants
 */
@Suppress("KDocMissingDocumentation")
public object OAuthGrantTypes {
    public const val AuthorizationCode: String = "authorization_code"
    public const val Password: String = "password"
}

/**
 * Install both OAuth1a and OAuth2 authentication helpers that do redirect to OAuth server authorization page
 * and handle corresponding callbacks
 */
@Suppress("unused")
@Deprecated("Install and configure OAuth instead.")
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
 * Respond OAuth redirect
 */
@Deprecated("Install and configure OAuth instead.")
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
 * Handle OAuth callback. Usually it leads to requesting an access token.
 */
@Deprecated("Install and configure OAuth instead.")
public suspend fun PipelineContext<Unit, ApplicationCall>.oauthHandleCallback(
    client: HttpClient,
    dispatcher: CoroutineDispatcher,
    provider: OAuthServerSettings,
    callbackUrl: String,
    loginPageUrl: String,
    block: suspend (OAuthAccessTokenResponse) -> Unit
) {
    @Suppress("DEPRECATION")
    oauthHandleCallback(client, dispatcher, provider, callbackUrl, loginPageUrl, {}, block)
}

/**
 * Handle OAuth callback.
 */
@Deprecated(
    "Specifying an extra configuration function will be deprecated. " +
        "Please provide it via OAuthServerSettings."
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
                            emptyMap(),
                            configure
                        )

                        block(accessToken)
                    } catch (cause: OAuth2Exception.InvalidGrant) {
                        Logger.trace("Redirected to the login page due to invalid_grant error: {}", cause.message)
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
