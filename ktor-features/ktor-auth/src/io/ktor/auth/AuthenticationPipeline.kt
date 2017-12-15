package io.ktor.auth

import io.ktor.application.*
import io.ktor.pipeline.*

/**
 * Represents authentication [Pipeline] for checking and requesting authentication
 */
class AuthenticationPipeline() : Pipeline<AuthenticationContext, ApplicationCall>(CheckAuthentication, RequestAuthentication) {

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

    companion object {
        /**
         * Phase for checking if user is already authenticated before all mechanisms kicks in
         */
        val CheckAuthentication = PipelinePhase("CheckAuthentication")

        /**
         * Phase for authentications mechanisms to plug into
         */
        val RequestAuthentication = PipelinePhase("RequestAuthentication")
    }
}