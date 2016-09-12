package org.jetbrains.ktor.locations

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.auth.*
import org.jetbrains.ktor.client.*
import org.jetbrains.ktor.features.*
import java.util.concurrent.*
import kotlin.reflect.*

inline fun <reified T: Any> Authentication.Pipeline.oauthAtLocation(client: HttpClient, exec: ExecutorService,
                                                                    noinline providerLookup: ApplicationCall.(T) -> OAuthServerSettings?,
                                                                    noinline urlProvider: ApplicationCall.(T, OAuthServerSettings) -> String) {
    oauthWithType(T::class, client, exec, providerLookup, urlProvider)
}

fun <T: Any> Authentication.Pipeline.oauthWithType(type: KClass<T>,
                                                   client: HttpClient,
                                                   exec: ExecutorService,
                                                   providerLookup: ApplicationCall.(T) -> OAuthServerSettings?,
                                                   urlProvider: ApplicationCall.(T, OAuthServerSettings) -> String) {

    fun ApplicationCall.resolve(): T {
        return application.feature(Locations).resolve<T>(type, this)
    }
    fun ApplicationCall.providerLookupLocal(): OAuthServerSettings? = providerLookup(resolve())
    fun ApplicationCall.urlProviderLocal(s: OAuthServerSettings): String = urlProvider(resolve(), s)

    oauth(client, exec,
            ApplicationCall::providerLookupLocal,
            ApplicationCall::urlProviderLocal)
}
