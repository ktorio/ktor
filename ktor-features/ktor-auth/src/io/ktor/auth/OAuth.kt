package io.ktor.auth

import io.ktor.application.*
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.pipeline.*
import io.ktor.response.*
import io.ktor.util.*
import kotlinx.coroutines.experimental.*
import java.io.*

enum class OAuthVersion {
    V10a, V20
}

sealed class OAuthServerSettings(val name: String, val version: OAuthVersion) {
    class OAuth1aServerSettings(
            name: String,
            val requestTokenUrl: String,
            val authorizeUrl: String,
            val accessTokenUrl: String,

            val consumerKey: String,
            val consumerSecret: String
    ) : OAuthServerSettings(name, OAuthVersion.V10a)

    class OAuth2ServerSettings(
            name: String,
            val authorizeUrl: String,
            val accessTokenUrl: String,
            val requestMethod: HttpMethod = HttpMethod.Get,

            val clientId: String,
            val clientSecret: String,
            val defaultScopes: List<String> = emptyList(),
            val accessTokenRequiresBasicAuth: Boolean = false
    ) : OAuthServerSettings(name, OAuthVersion.V20)
}

sealed class OAuthCallback {
    data class TokenPair(val token: String, val tokenSecret: String) : OAuthCallback()
    data class TokenSingle(val token: String, val state: String) : OAuthCallback()
}

sealed class OAuthAccessTokenResponse : Principal {
    data class OAuth1a(val token: String, val tokenSecret: String, val extraParameters: StringValues = StringValues.Empty) : OAuthAccessTokenResponse()
    data class OAuth2(val accessToken: String, val tokenType: String, val expiresIn: Long, val refreshToken: String?, val extraParameters: StringValues = StringValues.Empty) : OAuthAccessTokenResponse()
}

object OAuthGrantTypes {
    val AuthorizationCode = "authorization_code"
    val Password = "password"
}

suspend fun PipelineContext<Unit, ApplicationCall>.oauth(
        client: HttpClient, dispatcher: CoroutineDispatcher,
        providerLookup: ApplicationCall.() -> OAuthServerSettings?,
        urlProvider: ApplicationCall.(OAuthServerSettings) -> String
) {
    oauth1a(client, dispatcher, providerLookup, urlProvider)
    oauth2(client, dispatcher, providerLookup, urlProvider)
}

suspend fun PipelineContext<Unit, ApplicationCall>.oauthRespondRedirect(client: HttpClient, dispatcher: CoroutineDispatcher, provider: OAuthServerSettings, callbackUrl: String) {
    when (provider) {
        is OAuthServerSettings.OAuth1aServerSettings -> {
            run(dispatcher) {
                val requestToken = simpleOAuth1aStep1(client, provider, callbackUrl)
                call.redirectAuthenticateOAuth1a(provider, requestToken)
            }
        }
        is OAuthServerSettings.OAuth2ServerSettings -> {
            call.redirectAuthenticateOAuth2(provider, callbackUrl, nextNonce(), scopes = provider.defaultScopes)
        }
    }
}

suspend fun PipelineContext<Unit, ApplicationCall>.oauthHandleCallback(
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
                run(dispatcher) {
                    try {
                        val accessToken = simpleOAuth1aStep2(client, provider, tokens)
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
                run(dispatcher) {
                    try {
                        val accessToken = simpleOAuth2Step2(
                                client,
                                provider,
                                callbackUrl,
                                code,
                                emptyMap(),
                                configure
                        )

                        block(accessToken)
                    } catch (ioe: IOException) {
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
