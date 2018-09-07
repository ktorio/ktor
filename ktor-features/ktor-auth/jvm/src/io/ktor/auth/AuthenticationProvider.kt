package io.ktor.auth

import io.ktor.application.*

/**
 * Represents an authentication provider with the given name
 * @property name is the name of the provider, or `null` for a default provider
 */
open class AuthenticationProvider(val name: String? = null) {
    /**
     * Authentication pipeline for this provider
     */
    val pipeline = AuthenticationPipeline()

    private var filterPredicates: MutableList<(ApplicationCall) -> Boolean>? = null

    /**
     * Authentication filters specifying if authentication is required for particular [ApplicationCall]
     *
     * If there is no filters, authentication is required. If any filter returns true, authentication is not required.
     */
    val skipWhen: List<(ApplicationCall) -> Boolean> get() = filterPredicates ?: emptyList()

    /**
     * Adds an authentication filter to the list
     */
    fun skipWhen(predicate: (ApplicationCall) -> Boolean) {
        val list = filterPredicates ?: mutableListOf()
        list.add(predicate)
        filterPredicates = list
    }
}