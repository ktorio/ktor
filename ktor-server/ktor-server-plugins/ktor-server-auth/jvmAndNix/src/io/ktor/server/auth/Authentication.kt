/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.auth

import io.ktor.server.application.*
import io.ktor.util.*

/**
 * A config for [Authentication] plugin
 */
@KtorDsl
public class AuthenticationConfig(providers: Map<String?, AuthenticationProvider> = emptyMap()) {
    internal val providers = providers.toMutableMap()

    /**
     * Register a provider with the specified [name] and [configure] it
     * @throws IllegalArgumentException if there is already provider installed with the same name
     */
    public fun provider(name: String? = null, configure: DynamicProviderConfig.() -> Unit) {
        requireProviderNotRegistered(name)
        val configuration = DynamicProviderConfig(name).apply(configure)
        val provider = configuration.buildProvider()
        providers[provider.name] = provider
    }

    /**
     * Register the specified [provider]
     * @throws IllegalArgumentException if there is already provider installed with the same name
     */
    public fun register(provider: AuthenticationProvider) {
        requireProviderNotRegistered(provider.name)
        providers[provider.name] = provider
    }

    private fun requireProviderNotRegistered(providerName: String?) {
        if (providers.containsKey(providerName)) {
            throw IllegalArgumentException("Provider with the name $providerName is already registered")
        }
    }

    internal fun copy(): AuthenticationConfig = AuthenticationConfig(providers)
}

/**
 * A plugin that keeps all installed authentication providers.
 * Used together with [AuthenticationInterceptors] via [authenticate] function
 * to provide authentication functionalities for routes.
 *
 * Basic usage:
 * ```kotlin
 * install(Authentication) {
 *   basic { /* configure your auth here */ }
 * }
 * routing {
 *   route("users/{id}") {
 *      get { ... }
 *      authenticate {
 *         post("settings") { ... }
 *      }
 *   }
 * }
 * ```
 */
public class Authentication(internal var config: AuthenticationConfig) {

    /**
     * Configure already installed plugin
     */
    public fun configure(block: AuthenticationConfig.() -> Unit) {
        val newConfiguration = config.copy()
        block(newConfiguration)
        config = newConfiguration.copy()
    }

    public companion object : ApplicationPlugin<Application, AuthenticationConfig, Authentication> {
        override val key: AttributeKey<Authentication> = AttributeKey("AuthenticationHolder")

        override fun install(pipeline: Application, configure: AuthenticationConfig.() -> Unit): Authentication {
            val config = AuthenticationConfig().apply(configure)
            return Authentication(config)
        }
    }
}

/**
 * Retrieves an [AuthenticationContext] for `this` call
 */
public val ApplicationCall.authentication: AuthenticationContext
    get() = AuthenticationContext.from(this)

/**
 * Retrieves authenticated [Principal] for `this` call
 */
public inline fun <reified P : Principal> ApplicationCall.principal(): P? = authentication.principal()

/**
 * Installs [Authentication] plugin if not yet installed and invokes [block] on its config.
 * One is allowed to modify existing authentication configuration only in [authentication]'s block or
 * via [Authentication.configure] function.
 * Changing captured instance of configuration outside of [block] may have no effect or damage application's state.
 */
public fun Application.authentication(block: AuthenticationConfig.() -> Unit) {
    pluginOrNull(Authentication)?.configure(block) ?: install(Authentication, block)
}
