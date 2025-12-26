/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.engine.netty

import io.ktor.client.*
import io.ktor.client.engine.*

/**
 * A JVM client engine that uses the Java HTTP Client introduced in Java 11.
 *
 * To create the client with this engine, pass it to the `HttpClient` constructor:
 * ```kotlin
 * val client = HttpClient(Netty)
 * ```
 * To configure the engine, pass settings exposed by [NettyHttpConfig] to the `engine` method:
 * ```kotlin
 * val client = HttpClient(Netty) {
 *     engine {
 *         // this: JavaHttpConfig
 *     }
 * }
 * ```
 *
 * You can learn more about client engines from [Engines](https://ktor.io/docs/http-client-engines.html).
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.java.Java)
 */
public data object Netty : HttpClientEngineFactory<NettyHttpConfig> {
    override fun create(block: NettyHttpConfig.() -> Unit): HttpClientEngine =
        NettyHttpEngine(NettyHttpConfig().apply(block))
}

public class NettyHttpEngineContainer : HttpClientEngineContainer {
    override val factory: HttpClientEngineFactory<*> = Netty

    override fun toString(): String = "Netty"
}
