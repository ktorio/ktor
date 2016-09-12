package org.jetbrains.ktor.auth

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.client.*
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.util.*
import java.io.*
import java.util.concurrent.*

val OAuthKey: Any = "OAuth"

fun Authentication.Pipeline.oauth(client: HttpClient, exec: ExecutorService,
                                  providerLookup: ApplicationCall.() -> OAuthServerSettings?,
                                  urlProvider: ApplicationCall.(OAuthServerSettings) -> String) {
    oauth1a(client, exec, providerLookup, urlProvider)
    oauth2(client, exec, providerLookup, urlProvider)
}

internal fun Authentication.Pipeline.oauth2(client: HttpClient, exec: ExecutorService,
                                            providerLookup: ApplicationCall.() -> OAuthServerSettings?,
                                            urlProvider: ApplicationCall.(OAuthServerSettings) -> String) {
    intercept(Authentication.Pipeline.RequestAuthentication) { context ->
        val provider = context.call.providerLookup()
        when (provider) {
            is OAuthServerSettings.OAuth2ServerSettings -> {
                val token = context.call.oauth2HandleCallback()
                val callbackRedirectUrl = context.call.urlProvider(provider)
                if (token == null) {
                    context.challenge(OAuthKey, NotAuthenticatedCause.NoCredentials) {
                        it.success()
                        context.call.redirectAuthenticateOAuth2(provider, callbackRedirectUrl, nextNonce(), scopes = provider.defaultScopes)
                    }
                } else {
                    runAsyncWithError(exec, context) {
                        val accessToken = simpleOAuth2Step2(client, provider, callbackRedirectUrl, token)
                        context.principal(accessToken)
                    }
                }
            }
        }
    }
}

internal fun Authentication.Pipeline.oauth1a(client: HttpClient, exec: ExecutorService,
                                             providerLookup: ApplicationCall.() -> OAuthServerSettings?,
                                             urlProvider: ApplicationCall.(OAuthServerSettings) -> String) {
    intercept(Authentication.Pipeline.RequestAuthentication) { context ->
        val provider = context.call.providerLookup()
        if (provider is OAuthServerSettings.OAuth1aServerSettings) {
            val token = context.call.oauth1aHandleCallback()
            if (token == null) {
                context.challenge(OAuthKey, NotAuthenticatedCause.NoCredentials) { ch ->
                    runAsyncWithError(exec, context) {
                        val t = simpleOAuth1aStep1(client, provider, context.call.urlProvider(provider))
                        ch.success()
                        context.call.redirectAuthenticateOAuth1a(provider, t)
                    }
                }
            } else {
                runAsyncWithError(exec, context) {
                    val accessToken = simpleOAuth1aStep2(client, provider, token)
                    context.principal(accessToken)
                }
            }
        }
    }
}

private fun PipelineContext<*>.runAsyncWithError(exec: ExecutorService, context: AuthenticationProcedureContext, block: () -> Unit) {
    runAsync(exec) {
        try {
            block()
        } catch (ioe: IOException) {
            context.error(OAuthKey, NotAuthenticatedCause.Error(ioe.message ?: "IOException"))
        }
    }
}
