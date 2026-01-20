/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.apache

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
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.apache.Apache)
 */
@Deprecated(
    message = "Apache engine is deprecated. Consider using Apache5 engine instead.",
    replaceWith = ReplaceWith(
        "Apache5",
        "io.ktor.client.engine.apache5.Apache5"
    ),
    level = DeprecationLevel.WARNING
)
public data object Apache : HttpClientEngineFactory<ApacheEngineConfig> {
    override fun create(block: ApacheEngineConfig.() -> Unit): HttpClientEngine {
        val config = ApacheEngineConfig().apply(block)
        return ApacheEngine(config)
    }
}

@Deprecated(
    message = "Apache engine is deprecated. Consider using Apache5 engine instead.",
    level = DeprecationLevel.WARNING
)
public class ApacheEngineContainer : HttpClientEngineContainer {
    override val factory: HttpClientEngineFactory<*> = Apache

    override fun toString(): String = "Apache"
}
