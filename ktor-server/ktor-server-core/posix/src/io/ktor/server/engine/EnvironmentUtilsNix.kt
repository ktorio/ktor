/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

import kotlinx.cinterop.*
import platform.posix.*

internal actual fun ApplicationEngine.Configuration.configureSSLConnectors(
    host: String,
    sslPort: String,
    sslKeyStorePath: String?,
    sslKeyStorePassword: String?,
    sslPrivateKeyPassword: String?,
    sslKeyAlias: String
) {
    error("SSL is not supported in native")
}

internal actual fun ApplicationEnvironmentBuilder.configurePlatformProperties(args: Array<String>) {}

@OptIn(ExperimentalForeignApi::class)
internal actual fun getEnvironmentProperty(key: String): String? = getenv(key)?.toKString()
