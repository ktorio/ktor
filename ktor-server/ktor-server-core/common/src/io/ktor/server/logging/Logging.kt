/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.logging

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.*

/**
 * Generates a string representing this [ApplicationRequest] suitable for logging
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.logging.toLogString)
 */
public fun ApplicationRequest.toLogString(): String = "${httpMethod.value} - ${path()}"

/**
 * Base interface for plugins that can setup MDC. See [CallLogging] plugin.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.logging.MDCProvider)
 */
public interface MDCProvider {
    /**
     * Executes [block] with [MDC] setup
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.logging.MDCProvider.withMDCBlock)
     */
    public suspend fun withMDCBlock(call: ApplicationCall, block: suspend () -> Unit)
}

private object EmptyMDCProvider : MDCProvider {
    override suspend fun withMDCBlock(call: ApplicationCall, block: suspend () -> Unit) = block()
}

/**
 * Returns first instance of a plugin that implements [MDCProvider]
 * or default implementation with an empty context
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.logging.mdcProvider)
 */
public val Application.mdcProvider: MDCProvider
    @Suppress("UNCHECKED_CAST")
    get() = pluginRegistry.allKeys
        .firstNotNullOfOrNull { pluginRegistry.getOrNull(it as AttributeKey<Any>) as? MDCProvider }
        ?: EmptyMDCProvider
