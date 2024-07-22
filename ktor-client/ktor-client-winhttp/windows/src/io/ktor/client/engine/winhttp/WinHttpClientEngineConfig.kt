/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.winhttp

import io.ktor.client.engine.*
import io.ktor.http.*
import kotlinx.cinterop.*

/**
 * A challenge handler type.
 */
public typealias ChallengeHandler = (hRequest: COpaquePointer) -> Unit

public class WinHttpClientEngineConfig : HttpClientEngineConfig() {

    /**
     * A value indicating whether HTTP 2.0 protocol is enabled in WinHTTP.
     * The default value is true.
     */
    public var protocolVersion: HttpProtocolVersion = HttpProtocolVersion.HTTP_2_0

    /**
     * A value that allows to set required security protocol versions.
     * By default will be used system setting.
     */
    public var securityProtocols: WinHttpSecurityProtocol = WinHttpSecurityProtocol.Default

    /**
     * A value that disables TLS verification for outgoing requests.
     */
    public var sslVerify: Boolean = true

    /**
     * Handles the challenge of HTTP responses.
     */
    public var challengeHandler: ChallengeHandler? = null
        private set

    /**
     * Sets the [block] as an HTTP request challenge handler.
     */
    public fun handleChallenge(block: ChallengeHandler) {
        challengeHandler = block
    }
}
