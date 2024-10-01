/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.sse

import io.ktor.utils.io.*

@KtorDsl
public class SSEConfig {
    internal var serialize: (Any) -> String = { it.toString() }

    /**
     * Configures serialization logic for transforming data object into field `data` of `ServerSentEvent`.
     */
    public fun <T : Any> serialize(serialize: (T) -> String) {
        this.serialize = serialize as (Any) -> String
    }
}
