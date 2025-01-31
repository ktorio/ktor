/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth

import io.ktor.server.application.*

/**
 * A predicate function that accepts an application call and returns `true` or `false`.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.ApplicationCallPredicate)
 */
public typealias ApplicationCallPredicate = (ApplicationCall) -> Boolean

/**
 * An authentication function that accepts and verifies credentials and returns a principal when verification is successful.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.AuthenticationFunction)
 */
public typealias AuthenticationFunction<C> = suspend ApplicationCall.(credentials: C) -> Any?

/**
 * An authentication provider with the specified name.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.AuthenticationProvider)
 */
public abstract class AuthenticationProvider(config: Config) {
    /**
     * A provider name or `null` for a default provider.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.AuthenticationProvider.name)
     */
    public val name: String? = config.name

    /**
     * Authenticates a request based on [AuthenticationContext].
     * Implementations should either add a new [AuthenticationContext.principal] for successful authentication
     * or register [AuthenticationContext.challenge] for failed ones.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.AuthenticationProvider.onAuthenticate)
     */
    public abstract suspend fun onAuthenticate(context: AuthenticationContext)

    /**
     * Authentication filters specifying if authentication is required for a particular [ApplicationCall].
     *
     * If there is no filters, authentication is required. If any filter returns `true`, authentication is not required.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.AuthenticationProvider.skipWhen)
     */
    public val skipWhen: List<ApplicationCallPredicate> = config.filterPredicates ?: emptyList()

    /**
     * Serves as the base class for authentication providers.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.AuthenticationProvider.Config)
     *
     * @property name is the name of the provider, or `null` for a default provider.
     */
    public open class Config protected constructor(public val name: String?) {

        /**
         * Authentication filters specifying if authentication is required for a particular [ApplicationCall].
         *
         * If there is no filters, authentication is required. If any filter returns `true`, authentication is not required.
         */
        internal var filterPredicates: MutableList<ApplicationCallPredicate>? = null

        /**
         * Adds an authentication filter to the list.
         * For every application call the specified [predicate] is applied and if it returns `true` then the
         * authentication provider is skipped (no auth required for this call with this provider).
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.AuthenticationProvider.Config.skipWhen)
         */
        public fun skipWhen(predicate: (ApplicationCall) -> Boolean) {
            val list = filterPredicates ?: mutableListOf()
            list.add(predicate)
            filterPredicates = list
        }
    }
}
