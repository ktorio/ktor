package io.ktor.locations

import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.client.*
import kotlinx.coroutines.experimental.*
import kotlin.reflect.*

/**
 * Registers a typed route [T] that will act as an oauth handler.
 *
 * You have to provide an HTTP [client] and a [dispatcher] that will be used
 * to get the access token.
 *
 * Also it needs a [providerLookup] function that should use the typed route [T]
 * to return a [OAuthServerSettings] for that route.
 *
 * And a [urlProvider] function that using [T] and the generated [OAuthServerSettings]
 * have to construct the redirection URL.
 *
 * Class [T] must be annotated with [Location].
 */
inline fun <reified T : Any> AuthenticationPipeline.oauthAtLocation(client: HttpClient, dispatcher: CoroutineDispatcher,
                                                                    noinline providerLookup: ApplicationCall.(T) -> OAuthServerSettings?,
                                                                    noinline urlProvider: ApplicationCall.(T, OAuthServerSettings) -> String) {
    oauthWithType(T::class, client, dispatcher, providerLookup, urlProvider)
}

/**
 * Non-inline version of [AuthenticationPipeline.oauthAtLocation].
 *
 * @see AuthenticationPipeline.oauthAtLocation
 */
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
