/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.jetty

import io.ktor.client.engine.*
import org.eclipse.jetty.http2.client.*
import org.eclipse.jetty.util.ssl.*

/**
 * Configuration for [Jetty] implementation of [HttpClientEngineFactory].
 */
public class JettyEngineConfig : HttpClientEngineConfig() {
    internal var config: (HTTP2Client) -> Unit = {}
    /**
     * A Jetty's [SslContextFactory]. By default it trusts all the certificates.
     */
    public var sslContextFactory: SslContextFactory = SslContextFactory.Client()

    /**
     * Size of the cache that keeps least recently used [JettyHttp2Engine] instances.
     */
    public var clientCacheSize: Int = 10

    /**
     * Configure raw Jetty client.
     */
    public fun configureClient(block: (HTTP2Client) -> Unit) {
        val current = config
        config = {
            current(it)
            block(it)
        }
    }
}
