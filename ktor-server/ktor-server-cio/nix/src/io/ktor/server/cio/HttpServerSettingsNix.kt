/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.cio

import io.ktor.server.engine.*

internal actual fun HttpServerSettings(
    connectionIdleTimeoutSeconds: Long,
    connectorConfig: EngineConnectorConfig
): HttpServerSettings = HttpServerSettings(
    host = connectorConfig.host,
    port = connectorConfig.port,
    connectionIdleTimeoutSeconds = connectionIdleTimeoutSeconds
)
