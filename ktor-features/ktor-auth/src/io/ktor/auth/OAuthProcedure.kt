package io.ktor.auth

import io.ktor.application.*
import io.ktor.client.*
import io.ktor.pipeline.*
import io.ktor.util.*
import kotlinx.coroutines.experimental.*
import java.io.*

val OAuthKey: Any = "OAuth"

fun AuthenticationPipeline.oauth(client: HttpClient, dispatcher: CoroutineDispatcher,
                                              providerLookup: ApplicationCall.() -> OAuthServerSettings?,
                                              urlProvider: ApplicationCall.(OAuthServerSettings) -> String) {
    oauth1a(client, dispatcher, providerLookup, urlProvider)
    oauth2(client, dispatcher, providerLookup, urlProvider)
}

internal fun AuthenticationPipeline.oauth2(client: HttpClient, dispatcher: CoroutineDispatcher,
                                                        providerLookup: ApplicationCall.() -> OAuthServerSettings?,
                                                        urlProvider: ApplicationCall.(OAuthServerSettings) -> String) {
    intercept(AuthenticationPipeline.RequestAuthentication) { context ->
        val provider = call.providerLookup()
        if (provider is OAuthServerSettings.OAuth2ServerSettings) {
            val token = call.oauth2HandleCallback()
            val callbackRedirectUrl = call.urlProvider(provider)
            if (token == null) {
                context.challenge(OAuthKey, AuthenticationFailedCause.NoCredentials) {
                    call.redirectAuthenticateOAuth2(provider, callbackRedirectUrl, nextNonce(), scopes = provider.defaultScopes)
                    it.complete()
                }
            } else {
                runAsyncWithError(dispatcher, context) {
                    val accessToken = simpleOAuth2Step2(client, provider, callbackRedirectUrl, token)
                    context.principal(accessToken)
                }
            }
        }
    }
}

internal fun AuthenticationPipeline.oauth1a(client: HttpClient, dispatcher: CoroutineDispatcher,
                                                         providerLookup: ApplicationCall.() -> OAuthServerSettings?,
                                                         urlProvider: ApplicationCall.(OAuthServerSettings) -> String) {
    intercept(AuthenticationPipeline.RequestAuthentication) { context ->
        val provider = call.providerLookup()
        if (provider is OAuthServerSettings.OAuth1aServerSettings) {
            val token = call.oauth1aHandleCallback()
            if (token == null) {
                context.challenge(OAuthKey, AuthenticationFailedCause.NoCredentials) { ch ->
                    runAsyncWithError(dispatcher, context) {
                        val t = simpleOAuth1aStep1(client, provider, call.urlProvider(provider))
                        call.redirectAuthenticateOAuth1a(provider, t)
                        ch.complete()
                    }
                }
            } else {
                runAsyncWithError(dispatcher, context) {
                    val accessToken = simpleOAuth1aStep2(client, provider, token)
                    context.principal(accessToken)
                }
            }
        }
    }
}

private suspend fun PipelineContext<*, ApplicationCall>.runAsyncWithError(dispatcher: CoroutineDispatcher, context: AuthenticationContext, block: suspend () -> Unit) {
    return run(dispatcher) {
        try {
            block()
        } catch (ioe: IOException) {
            context.error(OAuthKey, AuthenticationFailedCause.Error(ioe.message ?: "IOException"))
        }
    }
}
