/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.engine.curl

import io.ktor.client.engine.*

public class CurlClientEngineConfig : HttpClientEngineConfig() {
    /**
     * Forces proxy tunneling by setting CURLOPT_HTTPPROXYTUNNEL
     */
    internal var forceProxyTunneling: Boolean = false

    /**
     * Enable TLS host and certificate verification by setting options
     * CURLOPT_SSL_VERIFYPEER and CURLOPT_SSL_VERIFYHOST.
     * Similar to `-k/--insecure` curl option.
     *
     * Setting this to `false` disables TLS verification so all connections will be insecure.
     *
     * While this is generally suitable for testing purpose,
     * we do not recommend using this in production.
     */
    public var sslVerify: Boolean = true
}
