/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.apache5

import io.ktor.client.*
import io.ktor.client.engine.*

/**
 * A JVM client engine that uses the Apache HTTP client.
 *
 * To create the client with this engine, pass it to the `HttpClient` constructor:
 * ```kotlin
 * val client = HttpClient(Apache)
 * ```
 * To configure the engine, pass settings exposed by [ApacheEngineConfig] to the `engine` method:
 * ```kotlin
 * val client = HttpClient(Apache) {
 *     engine {
 *         // this: ApacheEngineConfig
 *     }
 * }
 * ```
 *
 * You can learn more about client engines from [Engines](https://ktor.io/docs/http-client-engines.html).
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.apache5.Apache5)
 */
public data object Apache5 : HttpClientEngineFactory<Apache5EngineConfig> {
    override fun create(block: Apache5EngineConfig.() -> Unit): HttpClientEngine {
        val config = Apache5EngineConfig().apply(block)
        return Apache5Engine(config)
    }
}

public class Apache5EngineContainer : HttpClientEngineContainer {
    override val factory: HttpClientEngineFactory<*> = Apache5

    override fun toString(): String = "Apache5"
}
