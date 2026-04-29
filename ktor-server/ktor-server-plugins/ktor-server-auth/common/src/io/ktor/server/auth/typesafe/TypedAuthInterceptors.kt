/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalKtorApi::class)

package io.ktor.server.auth.typesafe

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.util.*
import io.ktor.utils.io.*

private val TypedAuthPluginNameGeneratorKey =
    AttributeKey<TypedAuthPluginNameGenerator>("TypesafeAuth:PluginNameGenerator")

private class TypedAuthPluginNameGenerator {
    private var nextId: Int = 0

    fun next(kind: String, parts: List<String>): String = buildString {
        append("TypedAuth:")
        append(kind)
        parts.forEach { part ->
            append(":")
            append(part)
        }
        append(":")
        append(nextId++)
    }
}

internal fun Route.typedAuthPluginName(kind: String, vararg parts: String): String {
    val root = lineage().last()
    val generator = root.attributes.computeIfAbsent(TypedAuthPluginNameGeneratorKey) {
        TypedAuthPluginNameGenerator()
    }
    return generator.next(kind, parts.toList())
}

/**
 * Creates a route-scoped plugin for one typed authentication layer.
 *
 * Each layer receives a unique plugin key, which lets nested [authenticateWith] blocks run cumulatively instead of
 * replacing one another. The same interceptor supports required, optional, and anonymous fallback modes.
 */
internal fun DefaultAuthScheme<*, *>.createTypedAuthPlugin(
    route: Route,
    kind: String,
    onUnauthorized: UnauthorizedHandler?,
    optional: Boolean = false,
    onAccepted: (suspend (ApplicationCall) -> Unit)? = null
): RouteScopedPlugin<Unit> =
    createRouteScopedPlugin(name = route.typedAuthPluginName(kind, name)) {
        on(AuthenticationHook) { call ->
            if (call.isHandled) {
                return@on
            }

            val context = call.authentication
            val existingPrincipal = principalFrom(context)
            if (existingPrincipal != null) {
                capture(call, existingPrincipal)
                return@on
            }

            logAuthenticationAttempt(call, provider)
            provider.onAuthenticate(context)
            if (principalFrom(context) != null) {
                logAuthenticationSucceeded(call, provider)
                onAccepted?.invoke(call)
                principalFrom(context)?.let { principal -> capture(call, principal) }
                return@on
            }

            logAuthenticationFailed(call, provider)
            val cause = context.allFailures.lastOrNull() ?: AuthenticationFailedCause.NoCredentials

            when {
                optional && cause == AuthenticationFailedCause.NoCredentials -> {
                    logOptionalAuthentication(call)
                    onAccepted?.invoke(call)
                    principalFrom(context)?.let { principal -> capture(call, principal) }
                }

                onUnauthorized != null -> onUnauthorized(call, cause)
                else -> context.executeChallenges(call)
            }
        }
    }

internal fun <P : Any> createTypedMultiAuthInterceptor(
    schemes: List<DefaultAuthScheme<out P, *>>,
    principalKey: AttributeKey<P>,
    onUnauthorized: MultiUnauthorizedHandler?,
    route: Route
): RouteScopedPlugin<Unit> {
    val schemeNames = schemes.joinToString(",") { it.name }
    val name = route.typedAuthPluginName("AnyOf", schemeNames)
    return createRouteScopedPlugin(name) {
        on(AuthenticationHook) { call ->
            if (call.isHandled) return@on

            val ctx = call.authentication

            if (onUnauthorized == null) {
                for ((index, scheme) in schemes.withIndex()) {
                    val provider = scheme.provider
                    logAuthenticationAttempt(call, provider)
                    provider.onAuthenticate(ctx)

                    val principal = scheme.principalFrom(ctx)
                    if (principal != null) {
                        logAuthenticationSucceeded(call, provider)
                        if (index != schemes.lastIndex) {
                            logSkippingOtherProviders(call)
                        }
                        call.attributes.put(principalKey, principal)
                        return@on
                    }
                    logAuthenticationFailed(call, provider)
                }

                ctx.executeChallenges(call)
                return@on
            }

            val failures = mutableMapOf<String, AuthenticationFailedCause>()
            for ((index, scheme) in schemes.withIndex()) {
                val schemeContext = AuthenticationContext(call)
                val provider = scheme.provider
                logAuthenticationAttempt(call, provider)
                provider.onAuthenticate(schemeContext)

                val principal = scheme.principalFrom(schemeContext)
                if (principal != null) {
                    logAuthenticationSucceeded(call, provider)
                    if (index != schemes.lastIndex) {
                        logSkippingOtherProviders(call)
                    }
                    ctx.principal(scheme.name, principal)
                    call.attributes.put(principalKey, principal)
                    return@on
                }

                logAuthenticationFailed(call, provider)
                failures[scheme.name] = schemeContext.allFailures.firstOrNull()
                    ?: AuthenticationFailedCause.NoCredentials
            }
            onUnauthorized(call, failures)
        }
    }
}

private fun logAuthenticationAttempt(call: ApplicationCall, provider: AuthenticationProvider) {
    LOGGER.trace("Trying to authenticate ${call.request.uri} with ${provider.name}")
}

private fun logAuthenticationSucceeded(call: ApplicationCall, provider: AuthenticationProvider) {
    LOGGER.trace("Authentication succeeded for ${call.request.uri} with provider $provider")
}

private fun logAuthenticationFailed(call: ApplicationCall, provider: AuthenticationProvider) {
    LOGGER.trace("Authentication failed for ${call.request.uri} with provider $provider")
}

private fun logOptionalAuthentication(call: ApplicationCall) {
    LOGGER.trace("Authentication is optional and no credentials were provided for ${call.request.uri}")
}

private fun logSkippingOtherProviders(call: ApplicationCall) {
    LOGGER.trace("Authenticate for ${call.request.uri} succeed. Skipping other providers")
}
