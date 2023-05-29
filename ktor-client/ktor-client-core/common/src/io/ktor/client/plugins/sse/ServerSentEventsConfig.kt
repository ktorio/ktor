/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.sse

import kotlin.time.*
import kotlin.time.Duration.Companion.milliseconds

/**
 * A config for the [ServerSentEvents] plugin.
 */
public class ServerSentEventsConfig {
    /**
     * The reconnection time. If the connection to the server is lost,
     * the client will wait for the specified time before attempting to reconnect.
     * Note that this parameter is not supported for some engines.
     */
    public var reconnectionTime: Duration = 1000.milliseconds
}
