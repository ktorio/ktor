/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.auth

import io.ktor.application.*

/**
 * Predicate function that accepts an application call and returns `true` or `false`
 */
typealias ApplicationCallPredicate = (ApplicationCall) -> Boolean

/**
 * Authentication function that accepts and verifies credentials and returns a principal when verification successful.
 */
typealias AuthenticationFunction<C> = suspend ApplicationCall.(credentials: C) -> Principal?

/**
 * Represents an authentication provider with the given name
 */
open class AuthenticationProvider(config: Configuration) {

    @Deprecated(
        "Provider should be built using configuration that need to be passed via constructor instead.",
        level = DeprecationLevel.ERROR
    )
    constructor(name: String? = null) : this(NamedConfiguration(name))

    private var filterPredicates: MutableList<ApplicationCallPredicate>? = config.filterPredicates

    /**
     * Provider name or `null` for a default provider
     */
    val name: String? = config.name

    /**
     * Authentication pipeline for this provider
     */
    val pipeline: AuthenticationPipeline = AuthenticationPipeline().also { pipeline ->
        pipeline.merge(config.pipeline)
    }

    /**
     * Authentication filters specifying if authentication is required for particular [ApplicationCall]
     *
     * If there is no filters, authentication is required. If any filter returns true, authentication is not required.
     */
    val skipWhen: List<ApplicationCallPredicate> get() = filterPredicates ?: emptyList()

    /**
     * Adds an authentication filter to the list
     */
    @Deprecated(
        "List of predicates should be built in configuration and then be passed via constructor instead.",
        level = DeprecationLevel.ERROR
    )
    fun skipWhen(predicate: (ApplicationCall) -> Boolean) {
        val list = filterPredicates ?: mutableListOf()
        list.add(predicate)
        filterPredicates = list
    }

    /**
     * Authentication provider configuration base class
     * @property name is the name of the provider, or `null` for a default provider.
     */
    open class Configuration protected constructor(val name: String?) {
        /**
         * Authentication pipeline for this provider
         */
        val pipeline: AuthenticationPipeline = AuthenticationPipeline()

        /**
         * Authentication filters specifying if authentication is required for particular [ApplicationCall]
         *
         * If there is no filters, authentication is required. If any filter returns true, authentication is not required.
         */
        internal var filterPredicates: MutableList<ApplicationCallPredicate>? = null

        /**
         * Adds an authentication filter to the list.
         * For every application call the specified [predicate] is applied and if it returns `true` then the
         * authentication provider is skipped (no auth required for this call with this provider).
         */
        fun skipWhen(predicate: (ApplicationCall) -> Boolean) {
            val list = filterPredicates ?: mutableListOf()
            list.add(predicate)
            filterPredicates = list
        }
    }

    private class NamedConfiguration(name: String?) : Configuration(name)
}
