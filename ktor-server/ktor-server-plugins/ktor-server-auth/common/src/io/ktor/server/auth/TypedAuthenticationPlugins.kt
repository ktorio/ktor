/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalKtorApi::class)

package io.ktor.server.auth

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.util.*
import io.ktor.utils.io.ExperimentalKtorApi
import kotlin.collections.set

private class TypedAuthPluginNameGenerator {
    private var nextId: Int = 0

    fun next(parts: List<String>): String = buildString {
        append("TypedAuth:")
        parts.forEach { part ->
            append(":")
            append(part)
        }
        append(":")
        append(nextId++)
    }

    companion object {
        val GLOBAL = TypedAuthPluginNameGenerator()
    }
}

internal fun typedAuthPluginName(vararg parts: String): String {
    return TypedAuthPluginNameGenerator.GLOBAL.next(parts.toList())
}

/**
 * Creates a route-scoped plugin for one typed authentication layer.
 *
 * Installed by [authenticateWithInternal]. Each layer receives a unique plugin key so nested [authenticateWith]
 * blocks run cumulatively instead of replacing one another.
 *
 * On success the resolved principal is stored in [principalKey] and [onAccepted] runs before the route handler.
 * Failure handling order: route-level [onUnauthorized], then [AuthenticationScheme.onUnauthorized], then provider
 * challenges. When [isOptional] is `true`, requests with no credentials skip failure handling.
 *
 * @param isOptional when `true`, requests without credentials continue without storing a principal.
 * @param onUnauthorized route-level handler; overrides the scheme default when non-null.
 * @param onAccepted optional hook invoked after authentication succeeds, before the route handler runs.
 */
internal fun <P : Any> AuthenticationScheme<P, *>.createPlugin(
    isOptional: Boolean,
    onUnauthorized: UnauthorizedHandler?,
    onAccepted: (suspend RoutingContext.(P) -> Unit)? = null
): RouteScopedPlugin<Unit> = createRouteScopedPlugin(name = typedAuthPluginName(name)) {
    on(AuthenticationHook) { call ->
        if (call.isHandled) return@on

        val authContext = call.authentication
        provider.logAuthenticationAttempt(call)
        provider.onAuthenticate(authContext)

        val principal = authContext.resolvePrincipal()
        if (principal != null) {
            provider.logAuthenticationSucceeded(call)
            call.attributes.put(principalKey, principal)
            onAccepted?.invoke(call.toRoutingContext(), principal)
            return@on
        }

        if (isOptional && authContext.failedWithNoCredentials()) {
            LOGGER.trace("Authentication is optional and no credentials were provided for ${call.request.uri}")
            return@on
        }

        provider.logAuthenticationFailed(call)
        val unauthorizedHandler = onUnauthorized ?: this@createPlugin.onUnauthorized
        if (unauthorizedHandler != null) {
            with(unauthorizedHandler) {
                call.toRoutingContext().onUnauthorized(cause = authContext.lastFailureOrNoCredentials())
            }
            return@on
        }
        authContext.executeChallenges(call)
    }
}

/**
 * Creates a route-scoped plugin for [authenticateWithAnyOf].
 *
 * Tries each scheme in declaration order against a shared [AuthenticationContext]. The first successful principal
 * is stored in [principalKey]. When all schemes fail, failure handling order is: route-level [MultiUnauthorizedHandler],
 * then the first scheme-level [AuthenticationScheme.onUnauthorized], then [executeChallenges].
 *
 * @param schemes typed schemes accepted by the route, tried in order.
 * @param principalKey attribute key used to expose the winning principal inside the route block.
 * @param onUnauthorized route-level handler invoked with per-scheme failure causes when every scheme fails.
 */
internal fun <P : Any> createMultiPlugin(
    schemes: List<AuthenticationScheme<out P, *>>,
    principalKey: AttributeKey<P>,
    onUnauthorized: MultiUnauthorizedHandler?,
): RouteScopedPlugin<Unit> {
    val schemeNames = schemes.joinToString(",") { it.name }
    val name = typedAuthPluginName(schemeNames)
    val schemeWithHandler = schemes.firstOrNull { it.onUnauthorized != null }

    return createRouteScopedPlugin(name) {
        on(AuthenticationHook) { call ->
            if (call.isHandled) return@on

            val failures = mutableMapOf<String, AuthenticationFailedCause>()
            val authContext = AuthenticationContext(call)

            for (scheme in schemes) {
                val provider = scheme.provider
                provider.logAuthenticationAttempt(call)
                provider.onAuthenticate(authContext)

                val principal = with(scheme) { authContext.resolvePrincipal() }
                if (principal != null) {
                    provider.logAuthenticationSucceeded(call)
                    authContext.principal(scheme.name, principal)
                    call.attributes.put(principalKey, principal)
                    return@on
                }

                provider.logAuthenticationFailed(call)
                failures[scheme.name] = authContext.lastFailureOrNoCredentials()
            }

            if (onUnauthorized != null) {
                onUnauthorized(call.toRoutingContext(), failures)
                return@on
            }

            if (schemeWithHandler != null) {
                with(checkNotNull(schemeWithHandler.onUnauthorized)) {
                    val cause = failures.getValue(schemeWithHandler.name)
                    call.toRoutingContext().onUnauthorized(cause)
                }
                return@on
            }

            authContext.executeChallenges(call)
        }
    }
}

private fun AuthenticationProvider.logAuthenticationAttempt(call: ApplicationCall) {
    LOGGER.trace("Trying to authenticate ${call.request.uri} with $name")
}

private fun AuthenticationProvider.logAuthenticationSucceeded(call: ApplicationCall) {
    LOGGER.trace("Authentication succeeded for ${call.request.uri} with provider $name")
}

private fun AuthenticationProvider.logAuthenticationFailed(call: ApplicationCall) {
    LOGGER.trace("Authentication failed for ${call.request.uri} with provider $name")
}
