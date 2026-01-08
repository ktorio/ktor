/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.engine.netty

import io.ktor.client.engine.*
import io.netty.bootstrap.Bootstrap
import javax.net.ssl.SSLContext

/**
 * A configuration for the [Netty] client engine.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.netty.NettyHttpConfig)
 */
public class NettyHttpConfig : HttpClientEngineConfig() {

    /**
     * An HTTP version to use.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.netty.NettyHttpConfig.protocolVersion)
     */
    public var protocolVersion: java.net.http.HttpClient.Version = java.net.http.HttpClient.Version.HTTP_1_1

    /**
     * Maximum number of connections per route.
     */
    public var maxConnectionsPerRoute: Int = 100

    /**
     * Maximum total connections.
     */
    public var maxConnectionsTotal: Int = 1000

    /**
     * Specifies the maximum amount of allocation allowed for a decompressor in the Netty engine.
     *
     * This value is used as a configuration parameter to manage memory usage when handling compressed data.
     */
    public var maxDecompressorAllocation: Int = 1_048_576 * 4

    /**
     * Custom SSL context to use for HTTPS connections.
     */
    public var sslContext: SSLContext? = null

    internal var bootstrapConfig: Bootstrap.() -> Unit = {}

    /**
     * Configure Netty [Bootstrap].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.netty.NettyHttpConfig.bootstrap)
     */
    public fun bootstrap(block: Bootstrap.() -> Unit) {
        val oldConfig = bootstrapConfig
        bootstrapConfig = {
            oldConfig()
            block()
        }
    }

    /**
     * Configure SSL context for HTTPS connections.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.netty.NettyHttpConfig.sslContext)
     */
    public fun sslContext(context: SSLContext) {
        sslContext = context
    }
}
