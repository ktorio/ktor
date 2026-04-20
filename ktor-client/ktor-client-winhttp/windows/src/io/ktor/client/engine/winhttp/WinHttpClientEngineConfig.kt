/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.winhttp

import io.ktor.client.engine.*
import io.ktor.http.*

public class WinHttpClientEngineConfig : HttpClientEngineConfig() {

    /**
     * A value indicating whether HTTP 2.0 protocol is enabled in WinHTTP.
     * The default value is true.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.winhttp.WinHttpClientEngineConfig.protocolVersion)
     */
    public var protocolVersion: HttpProtocolVersion = HttpProtocolVersion.HTTP_2_0

    /**
     * A value that allows setting the required security protocol versions.
     * By default, the system setting will be used.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.winhttp.WinHttpClientEngineConfig.securityProtocols)
     */
    public var securityProtocols: WinHttpSecurityProtocol = WinHttpSecurityProtocol.Default

    /**
     * A value that disables TLS verification for outgoing requests.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.winhttp.WinHttpClientEngineConfig.sslVerify)
     */
    public var sslVerify: Boolean = true
}
