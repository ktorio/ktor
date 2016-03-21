package org.jetbrains.ktor.auth

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.auth.httpclient.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.util.*
import java.util.concurrent.*

fun AuthenticationProcedure.oauth(client: HttpClient, exec: ExecutorService,
                                  providerLookup: ApplicationCall.() -> OAuthServerSettings?,
                                  urlProvider: ApplicationCall.(OAuthServerSettings) -> String) {
    oauth2(client, exec, providerLookup, urlProvider)
}

internal fun AuthenticationProcedure.oauth2(client: HttpClient, exec: ExecutorService,
                                            providerLookup: ApplicationCall.() -> OAuthServerSettings?,
                                            urlProvider: ApplicationCall.(OAuthServerSettings) -> String) {
    authenticate { context ->
        val provider = context.call.providerLookup()
        when (provider) {
            is OAuthServerSettings.OAuth2ServerSettings -> {
                val token = context.call.oauth2HandleCallback()
                val callbackRedirectUrl = context.call.urlProvider(provider)
                if (token == null) {
                    context.challenge {
                        context.call.redirectAuthenticateOAuth2(provider, callbackRedirectUrl, nextNonce(), scopes = provider.defaultScopes)
                    }
                } else {
                    proceedAsync(exec) {
                        val accessToken = simpleOAuth2Step2(client, provider, callbackRedirectUrl, token)
                        if (accessToken != null)
                            context.principal(accessToken)
                    }
                }
            }
        }
    }
}
