package org.jetbrains.ktor.http.auth

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.routing.*

fun <C: Credential, P: Principal> RoutingEntry.withAuth(extractor: CredentialProvider<C>, validator: AuthenticationProvider<C, P>, onFailed: (ApplicationRequestContext) -> ApplicationRequestStatus) {
    addInterceptor { ctx, next ->
        val credentials = extractor.extract(ctx.request)
        val principal = when (credentials) {
            null -> null
            else -> validator.authenticate(credentials)
        }

        if (principal == null) {
            onFailed(ctx)
        } else {
            ctx.request.attributes.put(PrincipalKey, principal)
            next(ctx)
        }
    }
}

public val PrincipalKey: AttributeKey<Principal> = AttributeKey()
