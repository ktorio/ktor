/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.sse

import io.ktor.server.application.*
import io.ktor.util.*
import io.ktor.util.logging.*

internal val LOGGER = KtorSimpleLogger("io.ktor.server.plugins.sse.SSE")

/**
 * Server-Sent Events (SSE) support plugin. It is required to be installed first before binding any sse endpoints.
 *
 * To learn more, see [specification](https://html.spec.whatwg.org/multipage/server-sent-events.html).
 *
 * Example:
 * ```kotlin
 * install(SSE)
 *
 * install(Routing) {
 *     sse {
 *          send(ServerSentEvent("Hello"))
 *     }
 * }
 * ```
 */
public class SSE private constructor(
    public val serialize: (Any) -> String,
) {
    public companion object Plugin : BaseApplicationPlugin<Application, SSEConfig, SSE> {
        override val key: AttributeKey<SSE> = AttributeKey("SSE")

        override fun install(pipeline: Application, configure: SSEConfig.() -> Unit): SSE {
            val config = SSEConfig().also(configure)
            with(config) {
                return SSE(serialize)
            }
        }
    }
}
