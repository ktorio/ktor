/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

import io.ktor.server.config.*

internal actual fun buildApplicationConfig(args: Array<String>): ApplicationConfig {
    val argumentsPairs = args.mapNotNull { it.splitPair('=') }.toMap()
    val commandLineProperties: Map<String, String> = argumentsPairs
        .filterKeys { it.startsWith("-P:") }
        .mapKeys { it.key.removePrefix("-P:") }

    return MapApplicationConfig(*commandLineProperties.map { it.key to it.value }.toTypedArray())
}

internal actual fun ApplicationEngineEnvironmentBuilder.configureSSLConnectors(
    host: String,
    sslPort: String,
    sslKeyStorePath: String?,
    sslKeyStorePassword: String?,
    sslPrivateKeyPassword: String?,
    sslKeyAlias: String
) {
    error("SSL is not supported in native")
}

internal actual fun ApplicationEngineEnvironmentBuilder.configurePlatformProperties(args: Array<String>) {}
