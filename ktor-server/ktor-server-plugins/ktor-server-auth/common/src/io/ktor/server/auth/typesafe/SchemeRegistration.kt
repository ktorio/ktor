/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.typesafe

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.util.*
import io.ktor.utils.io.*

private val RegisteredSchemesKey = AttributeKey<MutableSet<String>>("TypesafeAuthRegisteredSchemes")

@ExperimentalKtorApi
@PublishedApi
internal fun Application.registerSchemeIfNeeded(scheme: DefaultAuthScheme<*, *>) {
    val registered = attributes.computeIfAbsent(RegisteredSchemesKey) { mutableSetOf() }
    if (scheme.name in registered) return
    authentication { scheme.setup(this) }
    registered.add(scheme.name)
}
