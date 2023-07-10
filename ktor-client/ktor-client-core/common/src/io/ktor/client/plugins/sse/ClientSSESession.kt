/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.sse

import io.ktor.sse.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * A client Server-sent events session.
 */
public interface ClientSSESession : CoroutineScope {
    /**
     * An incoming server-sent events channel.
     */
    public val incoming: Flow<ServerSentEvent>
}
