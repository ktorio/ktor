/*
* Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.sessions

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.util.*
import io.ktor.util.logging.*
import io.ktor.utils.io.InternalAPI
import kotlin.concurrent.Volatile

@InternalAPI
public val SessionProvidersKey: AttributeKey<List<SessionProvider<*>>> = AttributeKey("SessionProvidersKey")

internal val LOGGER = KtorSimpleLogger("io.ktor.server.sessions.Sessions")

internal class SessionProviderRegistry(initial: List<SessionProvider<*>>) {
    @Volatile
    var providers: List<SessionProvider<*>> = initial
        private set

    fun register(provider: SessionProvider<*>) {
        providers.firstOrNull { it.name == provider.name }?.let { alreadyRegistered ->
            throw IllegalArgumentException(
                "There is already registered session provider with " +
                    "name ${provider.name}: $alreadyRegistered"
            )
        }
        providers = providers + provider
    }
}

private val SessionProviderRegistryKey = AttributeKey<SessionProviderRegistry>("SessionProviderRegistry")

@OptIn(InternalAPI::class)
private fun Application.exposeSessionProviders(providers: List<SessionProvider<*>>) {
    val exposed = attributes.getOrNull(SessionProvidersKey).orEmpty()
    attributes.put(SessionProvidersKey, exposed + providers.filter { it !in exposed })
}

/**
 * Registers the session providers from [configure] in the [Sessions] plugin that serves this route.
 *
 * When [Sessions] is already installed on this route, on one of its parents, or in the application, the providers
 * are added to that installation. Otherwise, [Sessions] is installed on this route.
 *
 * Unlike [SessionsConfig.register], providers added to an existing installation can share a session type with
 * the providers registered there before. Such sessions must be accessed by name.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.sessions.installOrExtendSessions)
 *
 * @param configure registers the session providers to add.
 * @throws IllegalArgumentException when a provider with the same name is already registered.
 */
@InternalAPI
public fun Route.installOrExtendSessions(configure: SessionsConfig.() -> Unit) {
    val registry = findSessionProviderRegistry() ?: run {
        install(Sessions, configure)
        return
    }
    val added = SessionsConfig().apply(configure).providers
    added.forEach(registry::register)
    application.exposeSessionProviders(added)
}

private fun Route.findSessionProviderRegistry(): SessionProviderRegistry? {
    var current: Route? = this
    while (current != null) {
        current.attributes.getOrNull(SessionProviderRegistryKey)?.let { return it }
        current = current.parent
    }
    return application.attributes.getOrNull(SessionProviderRegistryKey)
}

/**
 * A plugin that provides a mechanism to persist data between different HTTP requests.
 * Typical use cases include storing a logged-in user's ID, the contents of a shopping basket,
 * or keeping user preferences on the client.
 * In Ktor, you can implement sessions by using cookies or custom headers,
 * choose whether to store session data on the server or pass it to the client,
 * sign and encrypt session data and more.
 *
 * You can learn more from [Sessions](https://ktor.io/docs/sessions.html).
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.sessions.Sessions)
 *
 * @property providers list of session providers
 */
public val Sessions: RouteScopedPlugin<SessionsConfig> = createRouteScopedPlugin("Sessions", ::SessionsConfig) {
    val registry = SessionProviderRegistry(pluginConfig.providers)
    val sessionSupplier: suspend (ApplicationCall, List<SessionProvider<*>>) -> StatefulSession =
        if (isDeferredSessionsEnabled()) {
            ::createDeferredSession
        } else {
            ::createSession
        }

    (route?.attributes ?: application.attributes).put(SessionProviderRegistryKey, registry)
    application.exposeSessionProviders(registry.providers)

    onCall { call ->
        val providers = registry.providers
        if (providers.isEmpty()) {
            LOGGER.trace { "No sessions found for ${call.request.uri}" }
        } else {
            val sessions = providers.joinToString { it.name }
            LOGGER.trace { "Sessions found for ${call.request.uri}: $sessions" }
        }
        call.attributes.put(SessionDataKey, sessionSupplier(call, providers))
    }

    // When response is being sent, call each provider to update/remove session data
    on(BeforeSend) { call ->

        /*
         If sessionData is not available it means response happened before Session plugin got a chance to deserialize the data.
         We should ignore this call in this case.

         An example would be CORS plugin responding with 403 Forbidden
         */
        val sessionData = call.attributes.getOrNull(SessionDataKey) ?: return@on

        sessionData.sendSessionData(call) { provider ->
            LOGGER.trace { "Sending session data for ${call.request.uri}: $provider" }
        }
    }
}

private suspend fun createSession(call: ApplicationCall, providers: List<SessionProvider<*>>): StatefulSession {
    // For each call, call each provider and retrieve session data if needed.
    // Capture data in the attribute's value
    val providerData = providers.associateBy({ it.name }) {
        it.receiveSessionData(call)
    }

    return SessionData(providerData)
}
