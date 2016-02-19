package org.jetbrains.ktor.locations

import org.jetbrains.ktor.auth.*
import org.jetbrains.ktor.auth.httpclient.*
import org.jetbrains.ktor.interception.*
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.routing.*
import java.util.concurrent.*
import kotlin.reflect.*

inline fun <reified T: Any> InterceptApplicationCall<RoutingApplicationCall>.oauthAtLocation(client: HttpClient, exec: ExecutorService,
                                                                                noinline providerLookup: RoutingApplicationCall.(T) -> OAuthServerSettings?,
                                                                                noinline urlProvider: RoutingApplicationCall.(T, OAuthServerSettings) -> String) {
    oauthWithType(T::class, client, exec, providerLookup, urlProvider)
}

fun <T: Any> InterceptApplicationCall<RoutingApplicationCall>.oauthWithType(type: KClass<T>,
                                                            client: HttpClient,
                                                            exec: ExecutorService,
                                                            providerLookup: RoutingApplicationCall.(T) -> OAuthServerSettings?,
                                                            urlProvider: RoutingApplicationCall.(T, OAuthServerSettings) -> String) {

    fun RoutingApplicationCall.resolve(): T {
        return route.locations().resolve<T>(type, this)
    }
    fun RoutingApplicationCall.providerLookupLocal(): OAuthServerSettings? = providerLookup(resolve())
    fun RoutingApplicationCall.urlProviderLocal(s: OAuthServerSettings): String = urlProvider(resolve(), s)

    oauth(client, exec,
            RoutingApplicationCall::providerLookupLocal,
            RoutingApplicationCall::urlProviderLocal)
}
