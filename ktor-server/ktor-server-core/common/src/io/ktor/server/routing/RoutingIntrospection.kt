/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.routing

import io.ktor.server.application.*

/**
 * Gets the root of the routing block.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.routing.routingRoot)
 */
public val Application.routingRoot: RoutingNode
    get() = pluginOrNull(RoutingRoot) ?: throw IllegalStateException("Routing plugin is not installed")
