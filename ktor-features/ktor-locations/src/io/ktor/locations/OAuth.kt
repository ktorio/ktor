package io.ktor.locations

import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.client.*
import kotlinx.coroutines.experimental.*
import kotlin.reflect.*

inline fun <reified T : Any> AuthenticationPipeline.oauthAtLocation(client: HttpClient, dispatcher: CoroutineDispatcher,
                                                                    noinline providerLookup: ApplicationCall.(T) -> OAuthServerSettings?,
                                                                    noinline urlProvider: ApplicationCall.(T, OAuthServerSettings) -> String) {
    oauthWithType(T::class, client, dispatcher, providerLookup, urlProvider)
}

fun <T : Any> AuthenticationPipeline.oauthWithType(type: KClass<T>,
                                                   client: HttpClient,
                                                   dispatcher: CoroutineDispatcher,
                                                   providerLookup: ApplicationCall.(T) -> OAuthServerSettings?,
                                                   urlProvider: ApplicationCall.(T, OAuthServerSettings) -> String) {

    fun ApplicationCall.resolve(): T {
        return application.locations.resolve<T>(type, this)
    }

    fun ApplicationCall.providerLookupLocal(): OAuthServerSettings? = providerLookup(resolve())
    fun ApplicationCall.urlProviderLocal(s: OAuthServerSettings): String = urlProvider(resolve(), s)

    oauth(client, dispatcher,
            ApplicationCall::providerLookupLocal,
            ApplicationCall::urlProviderLocal)
}
