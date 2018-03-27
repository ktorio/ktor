package io.ktor.auth

import io.ktor.application.*
import io.ktor.client.*
import java.io.*

val OAuthKey: Any = "OAuth"

class OAuthAuthenticationProvider(name: String?) : AuthenticationProvider(name) {
    lateinit var client: HttpClient
    lateinit var providerLookup: ApplicationCall.() -> OAuthServerSettings?
    lateinit var urlProvider: ApplicationCall.(OAuthServerSettings) -> String
}

/**
 * Installs OAuth Authentication mechanism
 */
fun Authentication.Configuration.oauth(name: String? = null, configure: OAuthAuthenticationProvider.() -> Unit) {
    val provider = OAuthAuthenticationProvider(name).apply(configure)
    provider.oauth1a()
    provider.oauth2()
    register(provider)
}

internal fun OAuthAuthenticationProvider.oauth2() {
    pipeline.intercept(AuthenticationPipeline.RequestAuthentication) { context ->
        val provider = call.providerLookup()
        if (provider is OAuthServerSettings.OAuth2ServerSettings) {
            val token = call.oauth2HandleCallback()
            val callbackRedirectUrl = call.urlProvider(provider)
            if (token == null) {
                context.challenge(OAuthKey, AuthenticationFailedCause.NoCredentials) {
                    call.redirectAuthenticateOAuth2(provider, callbackRedirectUrl,
                            state = provider.stateProvider.getState(call),
                            scopes = provider.defaultScopes,
                            interceptor = provider.authorizeUrlInterceptor)
                    it.complete()
                }
            } else {
                withIOException(context) {
                    val accessToken = simpleOAuth2Step2(client, provider, callbackRedirectUrl, token)
                    context.principal(accessToken)
                }
            }
        }
    }
}

internal fun OAuthAuthenticationProvider.oauth1a() {
    pipeline.intercept(AuthenticationPipeline.RequestAuthentication) { context ->
        val provider = call.providerLookup()
        if (provider is OAuthServerSettings.OAuth1aServerSettings) {
            val token = call.oauth1aHandleCallback()
            if (token == null) {
                context.challenge(OAuthKey, AuthenticationFailedCause.NoCredentials) { ch ->
                    withIOException(context) {
                        val t = simpleOAuth1aStep1(client, provider, call.urlProvider(provider))
                        call.redirectAuthenticateOAuth1a(provider, t)
                        ch.complete()
                    }
                }
            } else {
                withIOException(context) {
                    val accessToken = simpleOAuth1aStep2(client, provider, token)
                    context.principal(accessToken)
                }
            }
        }
    }
}

private suspend fun withIOException(context: AuthenticationContext, block: suspend () -> Unit) {
    try {
        block()
    } catch (ioe: IOException) {
        context.error(OAuthKey, AuthenticationFailedCause.Error(ioe.message ?: "IOException"))
    }
}
