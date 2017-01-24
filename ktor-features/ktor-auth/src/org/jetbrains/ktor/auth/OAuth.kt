package org.jetbrains.ktor.auth

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.client.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.util.*
import java.io.*
import java.util.concurrent.*

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
    class TokenPair(val token: String, val tokenSecret: String) : OAuthCallback()
    class TokenSingle(val token: String, val state: String) : OAuthCallback()
}

sealed class OAuthAccessTokenResponse : Principal {
    class OAuth1a(val token: String, val tokenSecret: String, val extraParameters: ValuesMap = ValuesMap.Empty) : OAuthAccessTokenResponse()
    class OAuth2(val accessToken: String, val tokenType: String, val expiresIn: Long, val refreshToken: String?, val extraParameters: ValuesMap = ValuesMap.Empty) : OAuthAccessTokenResponse()
}

object OAuthGrandTypes {
    val AuthorizationCode = "authorization_code"
    val Password = "password"
}

fun PipelineContext<ApplicationCall>.oauth(client: HttpClient, exec: ExecutorService,
                                           providerLookup: ApplicationCall.() -> OAuthServerSettings?,
                                           urlProvider: ApplicationCall.(OAuthServerSettings) -> String) {
    oauth1a(client, exec, providerLookup, urlProvider)
    oauth2(client, exec, providerLookup, urlProvider)
}

fun PipelineContext<ApplicationCall>.oauthRespondRedirect(client: HttpClient, exec: ExecutorService, provider: OAuthServerSettings, callbackUrl: String) {
    when (provider) {
        is OAuthServerSettings.OAuth1aServerSettings -> {
            runAsync(exec) {
                val requestToken = simpleOAuth1aStep1(client, provider, callbackUrl)
                call.redirectAuthenticateOAuth1a(provider, requestToken)
            }
        }
        is OAuthServerSettings.OAuth2ServerSettings -> {
            call.redirectAuthenticateOAuth2(provider, callbackUrl, nextNonce(), scopes = provider.defaultScopes)
        }
    }
}

fun PipelineContext<ApplicationCall>.oauthHandleCallback(client: HttpClient,
                                                         exec: ExecutorService,
                                                         provider: OAuthServerSettings,
                                                         callbackUrl: String,
                                                         loginPageUrl: String,
                                                         configure: RequestBuilder.() -> Unit = {},
                                                         block: (OAuthAccessTokenResponse) -> Unit) {
    when (provider) {
        is OAuthServerSettings.OAuth1aServerSettings -> {
            val tokens = call.oauth1aHandleCallback()
            if (tokens == null) {
                call.respondRedirect(loginPageUrl)
            } else {
                runAsync(exec) {
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
                runAsync(exec) {
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

internal fun ApplicationCall.oauthHandleFail(redirectUrl: String): Nothing {
    respondRedirect(redirectUrl)
}

internal fun String.appendUrlParameters(parameters: String) =
        when {
            parameters.isEmpty() -> ""
            this.endsWith("?") -> ""
            "?" in this -> "&"
            else -> "?"
        }.let { separator -> "$this$separator$parameters" }
